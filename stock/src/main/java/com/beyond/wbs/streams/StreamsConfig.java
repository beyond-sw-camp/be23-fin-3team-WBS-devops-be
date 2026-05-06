package com.beyond.wbs.streams;

import com.beyond.wbs.kafka.event.EtcInoutStockEvent;
import com.beyond.wbs.kafka.event.InboundStockEvent;
import com.beyond.wbs.kafka.event.OutboundStockEvent;
import com.beyond.wbs.kafka.event.TransferStockEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.WindowStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Streams 설정 — 인프라 검증용 1차 Topology.
 *
 * 목적:
 *  - kafka-streams 의존성/설정이 stock 모듈에 정상 들어왔는지 검증
 *  - state store + interactive query 가 정상 작동하는지 확인
 *
 * 1차 Topology:
 *  - 입력: outbound.completed (출고 확정 이벤트)
 *  - 처리: productId 별 누적 출고 수량 합계
 *  - 출력: KTable "outbound-qty-by-product" (state store 로 자동 보관)
 *
 * 이후 Step 2/3 에서 대시보드 카운트 등 추가 Topology 가 이 클래스에 더해진다.
 */
@Configuration
@EnableKafkaStreams
public class StreamsConfig {

    /** 검증용 — productId 별 누적 출고 수량 합계 KTable 의 store 이름 */
    public static final String OUTBOUND_QTY_BY_PRODUCT_STORE = "outbound-qty-by-product";

    /** 시간별 처리량 (24h hopping) state store 이름 — key="{clientId}|{module}" */
    public static final String HOURLY_THROUGHPUT_STORE = "hourly-throughput";

    /** 시간별 처리량 윈도우 크기 — 1시간 단위 */
    public static final Duration THROUGHPUT_WINDOW_SIZE = Duration.ofHours(1);

    /** 시간별 처리량 retention — 25시간 (24h 차트 + 1h 여유) */
    public static final Duration THROUGHPUT_WINDOW_RETENTION = Duration.ofHours(25);

    /** 진행 중 작업 수 state store 이름 — key="{clientId}|{module}", value=현재 진행 중 카운트 */
    public static final String ACTIVE_ORDERS_STORE = "active-orders-count";

    /** 반품 vs 일반 비율 state store 이름 — key="{clientId}|{module}|{kind}", windowed 일 단위 */
    public static final String RETURN_RATIO_STORE = "return-ratio-daily";

    /** 반품 비율 윈도우 — 1일 tumbling */
    public static final Duration RETURN_RATIO_WINDOW = Duration.ofDays(1);

    /** 반품 비율 retention — 2일 (오늘 + 어제 여유) */
    public static final Duration RETURN_RATIO_RETENTION = Duration.ofDays(2);

    /**
     * Topology 정의 — Spring Boot 의 KafkaStreamsAutoConfiguration 이
     * 이 빈을 수집해서 단일 KafkaStreams 인스턴스로 묶어 실행한다.
     */
    @Bean
    public KTable<String, Long> outboundQtyByProductTable(StreamsBuilder builder) {
        // value 는 JSON → OutboundStockEvent 객체로 deserialize
        JsonSerde<OutboundStockEvent> eventSerde = new JsonSerde<>(OutboundStockEvent.class);
        eventSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);

        // 1) outbound.completed 토픽에서 이벤트 스트림 받기
        KStream<String, OutboundStockEvent> stream = builder.stream(
                "outbound.completed",
                Consumed.with(Serdes.String(), eventSerde)
        );

        // 2) 한 이벤트의 items 를 (productId, qty) 쌍 여러 개로 펼침 → 3) productId 별 누적합
        return stream
                .flatMap((key, event) -> {
                    List<KeyValue<String, Long>> out = new ArrayList<>();
                    if (event != null && event.getItems() != null) {
                        for (OutboundStockEvent.Item item : event.getItems()) {
                            if (item.getProductId() != null) {
                                out.add(KeyValue.pair(
                                        item.getProductId().toString(),
                                        (long) item.getQty()));
                            }
                        }
                    }
                    return out;
                })
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .aggregate(
                        () -> 0L,
                        (productId, qty, total) -> total + qty,
                        Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as(OUTBOUND_QTY_BY_PRODUCT_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Long())
                );
    }

    /**
     * Topology 2 — 시간별 처리량 (지난 24시간 라인차트용).
     *
     * 입력 토픽 4개를 합쳐서 (clientId|module) 키로 1시간 단위 카운트.
     *  - inbound.placed       → module="inbound"
     *  - outbound.completed   → module="outbound"
     *  - transfer.completed   → module="transfer"
     *  - etcinout.completed   → module="etcinout"
     *
     * 결과: WindowStore "hourly-throughput"
     *  - key   : Windowed<String> (clientId|module, [windowStart, windowEnd))
     *  - value : Long (그 시간 구간 내 처리 건수)
     *
     * 조회 API 는 windowStore 에서 windowStart 기준으로 최근 24개 조회 → 라인차트.
     */
    @Bean
    public KTable<Windowed<String>, Long> hourlyThroughputTable(StreamsBuilder builder) {
        // 4개 토픽 각각 deserializer 준비
        JsonSerde<InboundStockEvent> inboundSerde = new JsonSerde<>(InboundStockEvent.class);
        inboundSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);
        JsonSerde<OutboundStockEvent> outboundSerde = new JsonSerde<>(OutboundStockEvent.class);
        outboundSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);
        JsonSerde<TransferStockEvent> transferSerde = new JsonSerde<>(TransferStockEvent.class);
        transferSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);
        JsonSerde<EtcInoutStockEvent> etcInoutSerde = new JsonSerde<>(EtcInoutStockEvent.class);
        etcInoutSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);

        // 각 토픽을 (clientId|module, 1L) 형식의 공통 스트림으로 변환
        KStream<String, Long> inboundStream = builder
                .stream("inbound.placed", Consumed.with(Serdes.String(), inboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toThroughputKey(v.getClientId(), "inbound"), 1L));

        KStream<String, Long> outboundStream = builder
                .stream("outbound.completed", Consumed.with(Serdes.String(), outboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toThroughputKey(v.getClientId(), "outbound"), 1L));

        KStream<String, Long> transferStream = builder
                .stream("transfer.completed", Consumed.with(Serdes.String(), transferSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toThroughputKey(v.getClientId(), "transfer"), 1L));

        KStream<String, Long> etcInoutStream = builder
                .stream("etcinout.completed", Consumed.with(Serdes.String(), etcInoutSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toThroughputKey(v.getClientId(), "etcinout"), 1L));

        // 4개 합쳐서 1시간 tumbling window 카운트
        KStream<String, Long> merged = inboundStream
                .merge(outboundStream)
                .merge(transferStream)
                .merge(etcInoutStream);

        return merged
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .windowedBy(TimeWindows.ofSizeAndGrace(THROUGHPUT_WINDOW_SIZE, Duration.ofMinutes(5)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(HOURLY_THROUGHPUT_STORE)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long())
                        .withRetention(THROUGHPUT_WINDOW_RETENTION));
    }

    /** 시간별 처리량 state store key 생성 — "{clientId}|{module}" */
    private static String toThroughputKey(UUID clientId, String module) {
        return clientId.toString() + "|" + module;
    }

    /**
     * Topology 3 — 진행 중 작업 수 (모듈별 라이브 카운터).
     *
     * approved 받으면 +1, 마감(완료/취소) 받으면 -1.
     * 결과 = approved 됐고 아직 마감 안 된 작업 수 = "지금 미완료" 카운트.
     *
     * 결과: KeyValueStore "active-orders-count"
     *  - key   : "{clientId}|{module}"
     *  - value : Long (현재 진행 중 작업 수)
     *
     * 주의: Streams 시작 전부터 진행 중이던 지시서는 카운트에 포함 안 됨.
     */
    @Bean
    public KTable<String, Long> activeOrdersCountTable(StreamsBuilder builder) {
        JsonSerde<InboundStockEvent> inboundSerde = new JsonSerde<>(InboundStockEvent.class);
        inboundSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);
        JsonSerde<OutboundStockEvent> outboundSerde = new JsonSerde<>(OutboundStockEvent.class);
        outboundSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);
        JsonSerde<TransferStockEvent> transferSerde = new JsonSerde<>(TransferStockEvent.class);
        transferSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);
        JsonSerde<EtcInoutStockEvent> etcInoutSerde = new JsonSerde<>(EtcInoutStockEvent.class);
        etcInoutSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);

        // 입고 — approved=+1, order-completed=-1, cancelled=-1
        KStream<String, Long> inboundApproved = builder
                .stream("inbound.approved", Consumed.with(Serdes.String(), inboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "inbound"), 1L));
        KStream<String, Long> inboundCompleted = builder
                .stream("inbound.order-completed", Consumed.with(Serdes.String(), inboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "inbound"), -1L));
        KStream<String, Long> inboundCancelled = builder
                .stream("inbound.cancelled", Consumed.with(Serdes.String(), inboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "inbound"), -1L));

        // 출고
        KStream<String, Long> outboundApproved = builder
                .stream("outbound.approved", Consumed.with(Serdes.String(), outboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "outbound"), 1L));
        KStream<String, Long> outboundCompleted = builder
                .stream("outbound.completed", Consumed.with(Serdes.String(), outboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "outbound"), -1L));
        KStream<String, Long> outboundCancelled = builder
                .stream("outbound.cancelled", Consumed.with(Serdes.String(), outboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "outbound"), -1L));

        // 이동
        KStream<String, Long> transferApproved = builder
                .stream("transfer.approved", Consumed.with(Serdes.String(), transferSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "transfer"), 1L));
        KStream<String, Long> transferCompleted = builder
                .stream("transfer.completed", Consumed.with(Serdes.String(), transferSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "transfer"), -1L));
        KStream<String, Long> transferCancelled = builder
                .stream("transfer.cancelled", Consumed.with(Serdes.String(), transferSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "transfer"), -1L));

        // 기타입출고
        KStream<String, Long> etcApproved = builder
                .stream("etcinout.approved", Consumed.with(Serdes.String(), etcInoutSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "etcinout"), 1L));
        KStream<String, Long> etcCompleted = builder
                .stream("etcinout.completed", Consumed.with(Serdes.String(), etcInoutSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "etcinout"), -1L));
        KStream<String, Long> etcCancelled = builder
                .stream("etcinout.cancelled", Consumed.with(Serdes.String(), etcInoutSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> KeyValue.pair(toActiveKey(v.getClientId(), "etcinout"), -1L));

        // 모든 +1/-1 스트림 합쳐서 (clientId|module) 별 누적합
        KStream<String, Long> merged = inboundApproved
                .merge(inboundCompleted).merge(inboundCancelled)
                .merge(outboundApproved).merge(outboundCompleted).merge(outboundCancelled)
                .merge(transferApproved).merge(transferCompleted).merge(transferCancelled)
                .merge(etcApproved).merge(etcCompleted).merge(etcCancelled);

        return merged
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .aggregate(
                        () -> 0L,
                        (key, delta, total) -> Math.max(0L, total + delta),
                        Materialized.<String, Long, KeyValueStore<Bytes, byte[]>>as(ACTIVE_ORDERS_STORE)
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.Long())
                );
    }

    /** 진행 중 카운트 state store key — "{clientId}|{module}" */
    private static String toActiveKey(UUID clientId, String module) {
        return clientId.toString() + "|" + module;
    }

    /**
     * Topology 4 — 오늘 반품 vs 일반 비율.
     *
     * 입력:
     *  - inbound.order-completed (입고 마감)
     *  - outbound.completed     (출고 마감)
     *
     * 페이로드의 originType 으로 일반/반품 구분:
     *  - 입고: "return" → 반품, 그 외 (purchase_order, manual) → 일반
     *  - 출고: "return" → 반품, 그 외 (sales_order, manual) → 일반
     *
     * 결과: WindowStore "return-ratio-daily"
     *  - key   : "{clientId}|{module}|{kind}" (kind = "normal" | "return")
     *  - value : Long (오늘 카운트)
     *  - 윈도우: 일 단위 tumbling
     *
     * 조회 API 가 (normal, return) 두 카운트를 합쳐 비율 계산해 응답.
     */
    @Bean
    public KTable<Windowed<String>, Long> returnRatioDailyTable(StreamsBuilder builder) {
        JsonSerde<InboundStockEvent> inboundSerde = new JsonSerde<>(InboundStockEvent.class);
        inboundSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);
        JsonSerde<OutboundStockEvent> outboundSerde = new JsonSerde<>(OutboundStockEvent.class);
        outboundSerde.configure(Map.of("spring.json.trusted.packages", "*"), false);

        // 입고: originType="return" → 반품, 그 외는 일반
        KStream<String, Long> inboundStream = builder
                .stream("inbound.order-completed", Consumed.with(Serdes.String(), inboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> {
                    String kind = "return".equalsIgnoreCase(v.getOriginType()) ? "return" : "normal";
                    return KeyValue.pair(toRatioKey(v.getClientId(), "inbound", kind), 1L);
                });

        // 출고: originType="return" → 반품, 그 외는 일반
        KStream<String, Long> outboundStream = builder
                .stream("outbound.completed", Consumed.with(Serdes.String(), outboundSerde))
                .filter((k, v) -> v != null && v.getClientId() != null)
                .map((k, v) -> {
                    String kind = "return".equalsIgnoreCase(v.getOriginType()) ? "return" : "normal";
                    return KeyValue.pair(toRatioKey(v.getClientId(), "outbound", kind), 1L);
                });

        KStream<String, Long> merged = inboundStream.merge(outboundStream);

        return merged
                .groupByKey(Grouped.with(Serdes.String(), Serdes.Long()))
                .windowedBy(TimeWindows.ofSizeAndGrace(RETURN_RATIO_WINDOW, Duration.ofMinutes(5)))
                .count(Materialized.<String, Long, WindowStore<Bytes, byte[]>>as(RETURN_RATIO_STORE)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long())
                        .withRetention(RETURN_RATIO_RETENTION));
    }

    /** 반품 비율 state store key — "{clientId}|{module}|{kind}" */
    private static String toRatioKey(UUID clientId, String module, String kind) {
        return clientId.toString() + "|" + module + "|" + kind;
    }
}
