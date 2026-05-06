package com.beyond.wbs.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.kstream.Windowed;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyWindowStore;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kafka Streams state store 조회용 REST 컨트롤러 (검증 목적).
 *
 * Streams 가 메모리/디스크에 들고 있는 집계 결과(KTable) 를
 * Interactive Query 로 직접 읽어 즉시 응답한다. DB 안 거침.
 *
 * 검증 절차:
 *  1) 출고지시서 생성 → 승인 → 출고 확정 (outbound.completed 이벤트 발행)
 *  2) GET /streams/outbound-qty?productId=<UUID> 호출
 *  3) 누적 출고 수량 응답되면 인프라 정상
 */
@RestController
@RequestMapping("/streams")
public class StreamsQueryController {

    private final StreamsBuilderFactoryBean streamsFactory;

    public StreamsQueryController(StreamsBuilderFactoryBean streamsFactory) {
        this.streamsFactory = streamsFactory;
    }

    /**
     * 특정 상품의 누적 출고 수량 조회 — state store 직접 read.
     */
    @GetMapping("/outbound-qty")
    public ResponseEntity<Map<String, Object>> getOutboundQtyByProduct(
            @RequestParam String productId) {

        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);

        KafkaStreams streams = streamsFactory.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            body.put("status", "NOT_READY");
            body.put("streamsState", streams != null ? streams.state().toString() : "NULL");
            return ResponseEntity.ok(body);
        }

        ReadOnlyKeyValueStore<String, Long> store = streams.store(
                StoreQueryParameters.fromNameAndType(
                        StreamsConfig.OUTBOUND_QTY_BY_PRODUCT_STORE,
                        QueryableStoreTypes.keyValueStore())
        );

        Long qty = store.get(productId);
        body.put("status", "OK");
        body.put("totalOutboundQty", qty != null ? qty : 0L);
        return ResponseEntity.ok(body);
    }

    /**
     * 시간별 처리량 — 지난 24시간을 1시간 단위 카운트 배열로 응답.
     *
     * 응답 형식:
     *   [
     *     { "hour": "2026-05-06T03:00", "count": 5 },
     *     { "hour": "2026-05-06T04:00", "count": 12 },
     *     ...
     *   ]
     * (count 0 인 시간대도 포함 — 차트에서 빈 시간 자리 그리기 위함)
     */
    @GetMapping("/hourly-throughput")
    public ResponseEntity<Map<String, Object>> getHourlyThroughput(
            @RequestParam String clientId,
            @RequestParam String module) {

        Map<String, Object> body = new HashMap<>();
        body.put("clientId", clientId);
        body.put("module", module);

        KafkaStreams streams = streamsFactory.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            body.put("status", "NOT_READY");
            body.put("streamsState", streams != null ? streams.state().toString() : "NULL");
            body.put("buckets", List.of());
            return ResponseEntity.ok(body);
        }

        ReadOnlyWindowStore<String, Long> store = streams.store(
                StoreQueryParameters.fromNameAndType(
                        StreamsConfig.HOURLY_THROUGHPUT_STORE,
                        QueryableStoreTypes.windowStore())
        );

        // 시간 범위: [지금 - 24h] ~ 지금
        Instant now = Instant.now();
        Instant from = now.minus(24, ChronoUnit.HOURS);
        String key = clientId + "|" + module;

        // store 에서 해당 key 의 24시간 윈도우들 가져오기
        Map<Instant, Long> bucketByStart = new HashMap<>();
        try (var it = store.fetch(key, from, now)) {
            while (it.hasNext()) {
                KeyValue<Long, Long> kv = it.next();
                bucketByStart.put(Instant.ofEpochMilli(kv.key), kv.value);
            }
        }

        // 정시 단위로 24개 슬롯 채움 (count 0 도 포함)
        List<Map<String, Object>> buckets = new ArrayList<>();
        Instant cursor = from.truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i < 24; i++) {
            Map<String, Object> b = new HashMap<>();
            b.put("hour", LocalDateTime.ofInstant(cursor, ZoneId.systemDefault()).toString());
            Long count = bucketByStart.get(cursor);
            b.put("count", count != null ? count : 0L);
            buckets.add(b);
            cursor = cursor.plus(1, ChronoUnit.HOURS);
        }

        body.put("status", "OK");
        body.put("buckets", buckets);
        return ResponseEntity.ok(body);
    }

    /**
     * 진행 중 작업 수 — 모듈별 라이브 카운터.
     *
     * 응답 형식:
     *   {
     *     "clientId": "...",
     *     "modules": {
     *       "inbound": 3,
     *       "outbound": 5,
     *       "transfer": 1,
     *       "etcinout": 0
     *     },
     *     "total": 9
     *   }
     */
    @GetMapping("/active-orders")
    public ResponseEntity<Map<String, Object>> getActiveOrdersCount(
            @RequestParam String clientId) {

        Map<String, Object> body = new HashMap<>();
        body.put("clientId", clientId);

        KafkaStreams streams = streamsFactory.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            body.put("status", "NOT_READY");
            body.put("streamsState", streams != null ? streams.state().toString() : "NULL");
            body.put("modules", Map.of());
            body.put("total", 0L);
            return ResponseEntity.ok(body);
        }

        ReadOnlyKeyValueStore<String, Long> store = streams.store(
                StoreQueryParameters.fromNameAndType(
                        StreamsConfig.ACTIVE_ORDERS_STORE,
                        QueryableStoreTypes.keyValueStore())
        );

        Map<String, Long> modules = new HashMap<>();
        long total = 0L;
        for (String module : List.of("inbound", "outbound", "transfer", "etcinout")) {
            Long count = store.get(clientId + "|" + module);
            long v = count != null ? count : 0L;
            modules.put(module, v);
            total += v;
        }

        body.put("status", "OK");
        body.put("modules", modules);
        body.put("total", total);
        return ResponseEntity.ok(body);
    }

    /**
     * 오늘 반품 vs 일반 비율 — 입고/출고 모듈별.
     *
     * 응답 형식:
     *   {
     *     "clientId": "...",
     *     "inbound":  { "normal": 12, "return": 3, "total": 15, "returnRatio": 0.20 },
     *     "outbound": { "normal": 19, "return": 1, "total": 20, "returnRatio": 0.05 }
     *   }
     */
    @GetMapping("/return-ratio")
    public ResponseEntity<Map<String, Object>> getReturnRatio(
            @RequestParam String clientId) {

        Map<String, Object> body = new HashMap<>();
        body.put("clientId", clientId);

        KafkaStreams streams = streamsFactory.getKafkaStreams();
        if (streams == null || streams.state() != KafkaStreams.State.RUNNING) {
            body.put("status", "NOT_READY");
            body.put("streamsState", streams != null ? streams.state().toString() : "NULL");
            return ResponseEntity.ok(body);
        }

        ReadOnlyWindowStore<String, Long> store = streams.store(
                StoreQueryParameters.fromNameAndType(
                        StreamsConfig.RETURN_RATIO_STORE,
                        QueryableStoreTypes.windowStore())
        );

        // 오늘 자정 ~ 지금 범위로 fetch
        Instant now = Instant.now();
        Instant todayStart = now.truncatedTo(ChronoUnit.DAYS);

        for (String module : List.of("inbound", "outbound")) {
            long normal = sumWindowCounts(store, clientId + "|" + module + "|normal", todayStart, now);
            long ret = sumWindowCounts(store, clientId + "|" + module + "|return", todayStart, now);
            long total = normal + ret;
            double ratio = total > 0 ? (double) ret / total : 0.0;

            Map<String, Object> entry = new HashMap<>();
            entry.put("normal", normal);
            entry.put("return", ret);
            entry.put("total", total);
            entry.put("returnRatio", ratio);
            body.put(module, entry);
        }

        body.put("status", "OK");
        return ResponseEntity.ok(body);
    }

    /** WindowStore 의 [from, to) 범위 윈도우들 카운트 합산 */
    private static long sumWindowCounts(ReadOnlyWindowStore<String, Long> store,
                                         String key, Instant from, Instant to) {
        long sum = 0L;
        try (var it = store.fetch(key, from, to)) {
            while (it.hasNext()) {
                KeyValue<Long, Long> kv = it.next();
                if (kv.value != null) sum += kv.value;
            }
        }
        return sum;
    }
}
