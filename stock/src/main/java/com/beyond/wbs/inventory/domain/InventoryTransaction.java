package com.beyond.wbs.inventory.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 이력 (Inventory Transaction)
 *
 * 재고가 변동될 때마다 row를 쌓는 감사 로그 테이블.
 * "언제 / 어떤 상품이 / 어떤 위치에서 / 어떤 이유로 / 얼마나 변했는지" 추적용.
 *
 * ─────────────────────────────────────────────
 *  [기록 규칙]
 *  한 row = "특정 status의 수량 변화 한 건"
 *  → statusFrom == statusTo 로 기록한다.
 *  → qtyBefore/qtyAfter 는 "그 status의 잔액 전/후"를 의미.
 *
 *  상태 간 이동(예: available → reserved)은
 *  반드시 '2개 row'로 나눠 기록한다. (회계 분개의 차변/대변과 같은 원리)
 *
 *  [예시 1] 입고 완료로 available +100
 *    Row 1: statusFrom=available, statusTo=available
 *           qty=+100, qtyBefore=50, qtyAfter=150
 *           txType=inbound, refType=inbound_order
 *
 *  [예시 2] 출고지시서 승인 (100 예약)
 *    Row 1: statusFrom=available, statusTo=available
 *           qty=-100, qtyBefore=150, qtyAfter=50
 *           txType=reserve, refType=outbound_order
 *    Row 2: statusFrom=reserved,  statusTo=reserved
 *           qty=+100, qtyBefore=0, qtyAfter=100
 *           txType=reserve, refType=outbound_order
 *    → 두 row는 같은 refId를 공유해서 "같은 이벤트"임을 표시.
 *
 *  [예시 3] 출고지시서 취소 (예약 해제)
 *    Row 1: reserved -100, Row 2: available +100  (refId 공유)
 *
 *  [예시 4] 출고 확정 (실제 창고에서 빠짐)
 *    Row 1: statusFrom=reserved, statusTo=reserved
 *           qty=-100, qtyBefore=100, qtyAfter=0
 *           txType=outbound, refType=outbound_order
 *    → 예약재고가 최종 소진. 이 경우 available은 변화 없음(1 row).
 * ─────────────────────────────────────────────
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "inventory_transactions")
public class InventoryTransaction {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (고객사 / 멀티테넌트)
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // Master Service 참조 - 상품
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // Inventory 테이블 참조 - 어느 재고 row가 변경됐는지
    @Column(name = "inventory_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID inventoryId;

    // Master Service 참조 - 창고
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Master Service 참조 - 위치 (랙)
    // nullable: 입고예정/검수중처럼 적치 위치가 미정인 단계의 이력을 허용
    @Column(name = "location_id", columnDefinition = "BINARY(16)")
    private UUID locationId;

    // 변동 유형 (inbound / outbound / reserve / unreserve / adjust ...)
    // - 이벤트의 "원인"을 나타냄 (예: 예약을 위해 available이 깎였다면 reserve)
    @Enumerated(EnumType.STRING)
    @Column(name = "tx_type", nullable = false, length = 20)
    private TxType txType;

    // 이 row의 수량 변화량 (양수: 해당 status 증가, 음수: 해당 status 감소)
    @Column(name = "qty", nullable = false)
    private Integer qty;

    // 변동 전 잔액 (statusFrom == statusTo 의 잔액)
    @Column(name = "qty_before", nullable = false)
    private Integer qtyBefore;

    // 변동 후 잔액
    @Column(name = "qty_after", nullable = false)
    private Integer qtyAfter;

    // 이 row가 기록하는 status 축 (statusFrom == statusTo)
    @Enumerated(EnumType.STRING)
    @Column(name = "status_from", length = 20)
    private InventoryStatus statusFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_to", length = 20)
    private InventoryStatus statusTo;

    // 변동 발생 원천 ID (예: 출고지시서 ID, 입고지시서 ID 등)
    // 같은 이벤트로 생성된 여러 row는 동일한 refId를 공유
    @Column(name = "ref_id", columnDefinition = "BINARY(16)")
    private UUID refId;

    // 변동 발생 원천 유형
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", length = 30)
    private RefType refType;

    // 비고
    @Column(columnDefinition = "TEXT")
    private String note;

    // 생성자 (Account Service 참조). 시스템 자동 생성 시 null
    @Column(name = "created_by", columnDefinition = "BINARY(16)")
    private UUID createdBy;

    // 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
