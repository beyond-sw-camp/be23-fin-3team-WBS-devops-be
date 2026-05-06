package com.beyond.wbs.streams;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
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
}
