package com.beyond.wbs.inventory.kafka;

import com.beyond.wbs.alert.service.SalesOrderShortageService;
import com.beyond.wbs.kafka.event.InboundStockEvent;
import com.beyond.wbs.inventory.service.InventoryService;
import com.beyond.wbs.kafka.event.OutboundStockEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 재고 이벤트 수신자 (Consumer)
 *
 * 출고/입고 서비스에서 발행한 Kafka 이벤트를 수신하여
 * InventoryService의 재고 변동 메서드를 호출한다.
 *
 * 수신 토픽 (7개):
 *  [출고]
 *  - outbound.approved  → reserve()          가용 ↓ / 예약 ↑
 *  - outbound.cancelled → unreserve()        예약 ↓ / 가용 ↑
 *  - outbound.completed → releaseOnDispatch() 예약 ↓ (실물 출고)
 *
 *  [입고]
 *  - inbound.approved   → addIncoming()       입고예정 ↑
 *  - inbound.inspected  → addPending()        입고예정 ↓ / 검수중 ↑
 *  - inbound.defect     → markDefect()        검수중 ↓ / 불량 ↑
 *  - inbound.placed     → confirmPlacement()  검수중 ↓ / 가용 ↑
 */
@Slf4j
@Component
public class InventoryEventConsumer {

    private final InventoryService inventoryService;
    private final SalesOrderShortageService salesOrderShortageService;

    @Autowired
    public InventoryEventConsumer(InventoryService inventoryService,
                                   SalesOrderShortageService salesOrderShortageService) {
        this.inventoryService = inventoryService;
        this.salesOrderShortageService = salesOrderShortageService;
    }

    // ============================================================
    // 출고 이벤트 수신
    // ============================================================

    /**
     * 출고지시서 승인 → 재고 예약 (가용 → 예약)
     */
    @KafkaListener(topics = "outbound.approved", groupId = "stock-group")
    public void handleOutboundApproved(OutboundStockEvent event) {
        log.info("[Kafka 수신] outbound.approved | refId={}", event.getRefId());
        try {
            for (OutboundStockEvent.Item item : event.getItems()) {
                inventoryService.reserve(
                        event.getClientId(),
                        item.getProductId(),
                        event.getWarehouseId(),
                        item.getLocationId(),
                        item.getQty(),
                        event.getRefId(),
                        event.getUserId()
                );
            }
            log.info("[Kafka 처리 완료] outbound.approved | refId={}", event.getRefId());
            salesOrderShortageService.refresh(event.getClientId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] outbound.approved | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }

    /**
     * 출고지시서 취소 → 재고 예약 해제 (예약 → 가용 원복)
     */
    @KafkaListener(topics = "outbound.cancelled", groupId = "stock-group")
    public void handleOutboundCancelled(OutboundStockEvent event) {
        log.info("[Kafka 수신] outbound.cancelled | refId={}", event.getRefId());
        try {
            for (OutboundStockEvent.Item item : event.getItems()) {
                inventoryService.unreserve(
                        event.getClientId(),
                        item.getProductId(),
                        event.getWarehouseId(),
                        item.getLocationId(),
                        item.getQty(),
                        event.getRefId(),
                        event.getUserId()
                );
            }
            log.info("[Kafka 처리 완료] outbound.cancelled | refId={}", event.getRefId());
            salesOrderShortageService.refresh(event.getClientId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] outbound.cancelled | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }

    /**
     * 출고 확정 → 예약 재고 최종 차감 (실물 출고)
     */
    @KafkaListener(topics = "outbound.completed", groupId = "stock-group")
    public void handleOutboundCompleted(OutboundStockEvent event) {
        log.info("[Kafka 수신] outbound.completed | refId={}", event.getRefId());
        try {
            for (OutboundStockEvent.Item item : event.getItems()) {
                inventoryService.releaseOnDispatch(
                        event.getClientId(),
                        item.getProductId(),
                        event.getWarehouseId(),
                        item.getLocationId(),
                        item.getQty(),
                        event.getRefId(),
                        event.getUserId()
                );
            }
            log.info("[Kafka 처리 완료] outbound.completed | refId={}", event.getRefId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] outbound.completed | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }

    // ============================================================
    // 입고 이벤트 수신
    // ============================================================

    /**
     * 입고지시서 승인 → 입고예정 재고 추가
     */
    @KafkaListener(topics = "inbound.approved", groupId = "stock-group")
    public void handleInboundApproved(InboundStockEvent event) {
        log.info("[Kafka 수신] inbound.approved | refId={} | [디버그] userId={}",
                event.getRefId(), event.getUserId());
        try {
            for (InboundStockEvent.Item item : event.getItems()) {
                inventoryService.addIncoming(
                        event.getClientId(),
                        item.getProductId(),
                        event.getWarehouseId(),
                        item.getLocationId(),
                        item.getQty(),
                        event.getRefId(),
                        event.getUserId()
                );
            }
            log.info("[Kafka 처리 완료] inbound.approved | refId={}", event.getRefId());
            salesOrderShortageService.refresh(event.getClientId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] inbound.approved | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }

    /**
     * 입고지시서 취소 → approved 단계 취소 시 입고예정(incomingQty) 원복.
     * draft 단계 취소는 items 가 비어 발행되므로 이 핸들러가 사실상 noop.
     */
    @KafkaListener(topics = "inbound.cancelled", groupId = "stock-group")
    public void handleInboundCancelled(InboundStockEvent event) {
        log.info("[Kafka 수신] inbound.cancelled | refId={} | items={}",
                event.getRefId(),
                event.getItems() != null ? event.getItems().size() : 0);
        try {
            if (event.getItems() == null || event.getItems().isEmpty()) {
                // draft 취소 — 재고 영향 없음
                log.info("[Kafka 처리 완료] inbound.cancelled (draft, noop) | refId={}", event.getRefId());
                return;
            }
            for (InboundStockEvent.Item item : event.getItems()) {
                inventoryService.removeIncoming(
                        event.getClientId(),
                        item.getProductId(),
                        event.getWarehouseId(),
                        item.getLocationId(),
                        item.getQty(),
                        event.getRefId(),
                        event.getUserId()
                );
            }
            log.info("[Kafka 처리 완료] inbound.cancelled | refId={}", event.getRefId());
            salesOrderShortageService.refresh(event.getClientId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] inbound.cancelled | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }

    /**
     * 검수 완료 → 입고예정 ↓ / 검수중 ↑, 그리고 불량분이 있으면 검수중 ↓ / 불량 ↑ 까지 원자적으로 처리.
     *
     * 하나의 이벤트에 normal 총량(items) + defect 하위집합(defectItems)이 같이 실려와서
     * 같은 트랜잭션 내에서 순서대로 처리한다. (토픽 분리 시 발생하던 race 방지)
     */
    @KafkaListener(topics = "inbound.inspected", groupId = "stock-group")
    public void handleInboundInspected(InboundStockEvent event) {
        log.info("[Kafka 수신] inbound.inspected | refId={} | [디버그] userId={}",
                event.getRefId(), event.getUserId());
        try {
            // 1) 입고예정 → 검수중 (정상+불량 총량)
            if (event.getItems() != null) {
                for (InboundStockEvent.Item item : event.getItems()) {
                    inventoryService.addPending(
                            event.getClientId(),
                            item.getProductId(),
                            event.getWarehouseId(),
                            item.getLocationId(),
                            item.getQty(),
                            event.getRefId(),
                            event.getUserId()
                    );
                }
            }
            // 2) 검수중 → 불량 (불량분만)
            if (event.getDefectItems() != null) {
                for (InboundStockEvent.Item item : event.getDefectItems()) {
                    inventoryService.markDefect(
                            event.getClientId(),
                            item.getProductId(),
                            event.getWarehouseId(),
                            item.getLocationId(),
                            item.getQty(),
                            event.getRefId(),
                            event.getUserId()
                    );
                }
            }
            log.info("[Kafka 처리 완료] inbound.inspected | refId={}", event.getRefId());
            salesOrderShortageService.refresh(event.getClientId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] inbound.inspected | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }

    /**
     * [DEPRECATED] 검수 불량 전용 토픽 — 과거 분리 발행 방식의 잔재.
     * 신 코드에서는 inbound.inspected 이벤트 안에 defectItems 로 함께 전달됨.
     * 이 리스너는 호환성을 위해 남겨둠 (기존 미소비 메시지 처리).
     */
    @KafkaListener(topics = "inbound.defect", groupId = "stock-group")
    public void handleInboundDefect(InboundStockEvent event) {
        log.info("[Kafka 수신] inbound.defect (deprecated path) | refId={}", event.getRefId());
        try {
            for (InboundStockEvent.Item item : event.getItems()) {
                inventoryService.markDefect(
                        event.getClientId(),
                        item.getProductId(),
                        event.getWarehouseId(),
                        item.getLocationId(),
                        item.getQty(),
                        event.getRefId(),
                        event.getUserId()
                );
            }
            log.info("[Kafka 처리 완료] inbound.defect | refId={}", event.getRefId());
            salesOrderShortageService.refresh(event.getClientId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] inbound.defect | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }

    /**
     * 적치 완료 → 검수중 ↓ / 가용 ↑
     */
    @KafkaListener(topics = "inbound.placed", groupId = "stock-group")
    public void handleInboundPlaced(InboundStockEvent event) {
        log.info("[Kafka 수신] inbound.placed | refId={}", event.getRefId());
        try {
            for (InboundStockEvent.Item item : event.getItems()) {
                inventoryService.confirmPlacement(
                        event.getClientId(),
                        item.getProductId(),
                        event.getWarehouseId(),
                        item.getLocationId(),
                        item.getQty(),
                        event.getRefId(),
                        event.getUserId()
                );
            }
            log.info("[Kafka 처리 완료] inbound.placed | refId={}", event.getRefId());
            salesOrderShortageService.refresh(event.getClientId());
        } catch (Exception e) {
            log.error("[Kafka 처리 실패] inbound.placed | refId={} | error={}",
                    event.getRefId(), e.getMessage(), e);
        }
    }
}
