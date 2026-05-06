package com.beyond.wbs.streams;

import com.beyond.wbs.kafka.event.OutboundStockEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
}
