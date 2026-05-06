package com.beyond.wbs.inbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(
        name = "inbound_orders",
        indexes = {
                // 미리보기 입고예정 합산: client + warehouse + expected_date + status
                @Index(name = "idx_ib_client_wh_date_status", columnList = "client_id, warehouse_id, expected_date, status"),
                // 대시보드 KPI 최적화
                @Index(name = "idx_ib_client_status_date", columnList = "client_id, status, expected_date")
        }
)
public class InboundOrders {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (회사)
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // Master Service 참조 (창고)
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Master Service 참조 (입고처)
    // nullable: 반품 입고 시 협력사 없음 (출고처/고객으로부터 반품)
    @Column(name = "supplier_id", columnDefinition = "BINARY(16)")
    private UUID supplierId;

    // Account Service 참조 (생성자)
    @Column(name = "created_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    // Account Service 참조 (승인자)
    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    // 지시서 번호 (자동채번)
    @Column(name = "order_no", nullable = false, length = 30)
    private String orderNo;

    // 입고 지시서 상태
    // draft(초안) → approved(승인) → in_progress(처리중)
    // → completed(완료) / partial(부분완료) / cancelled(취소)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InboundOrderStatus status;

    // 출처 유형 (purchase_order / manual)
    @Column(name = "origin_type", length = 20)
    private String originType;

    // 출처 ID (erp_purchase_orders.id)
    @Column(name = "origin_id", columnDefinition = "BINARY(16)")
    private UUID originId;

    // 입고 예정일
    @Column(name = "expected_date", nullable = false)
    private LocalDate expectedDate;

    // 승인 일시
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 비고
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 수정 일시
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static InboundOrders fromAsn(ErpPurchaseOrders asn, UUID warehouseId,
                                         UUID clientId, UUID userId, String orderNo) {
        return InboundOrders.builder()
                .clientId(clientId)
                .warehouseId(warehouseId)
                .supplierId(asn.getSupplierId())
                .createdBy(userId)
                .orderNo(orderNo)
                .status(InboundOrderStatus.draft)
                .originType("purchase_order")
                .originId(asn.getId())
                .expectedDate(asn.getScheduledDate())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    public static InboundOrders createManual(UUID clientId, UUID warehouseId, UUID supplierId,
                                              UUID userId, String orderNo, LocalDate expectedDate, String source) {
        return InboundOrders.builder()
                .clientId(clientId)
                .warehouseId(warehouseId)
                .supplierId(supplierId)
                .createdBy(userId)
                .orderNo(orderNo)
                .status(InboundOrderStatus.draft)
                .originType(source != null ? source : "manual")
                .expectedDate(expectedDate)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 반품 입고지시서 생성
     * originType = "return", originId = 출고지시서 ID
     */
    public static InboundOrders createReturn(UUID clientId, UUID warehouseId,
                                              UUID userId, String orderNo,
                                              UUID outboundOrderId) {
        return InboundOrders.builder()
                .clientId(clientId)
                .warehouseId(warehouseId)
                .createdBy(userId)
                .orderNo(orderNo)
                .status(InboundOrderStatus.draft)
                .originType("return")
                .originId(outboundOrderId)
                .expectedDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // 승인 처리 (draft → approved)
    public void approve(UUID approvedBy) {
        this.status = InboundOrderStatus.approved;
        this.approvedBy = approvedBy;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 입고지시서 취소 (draft / approved 단계만 가능 — 검수 시작 후엔 불가)
    public void cancel() {
        this.status = InboundOrderStatus.cancelled;
        this.updatedAt = LocalDateTime.now();
    }

    // 입고 확정 (approved → received) - 수량 검수 완료, 가재고 발생
    public void receive() {
        this.status = InboundOrderStatus.received;
        this.updatedAt = LocalDateTime.now();
    }

    // 적치 작업 생성 (received → placing) - 적치 지시서 생성됨
    public void startPlacing() {
        this.status = InboundOrderStatus.placing;
        this.updatedAt = LocalDateTime.now();
    }

    // 적치 완료 (placing → completed / partial)
    // 검수 or 적치 중 한 곳이라도 불량 발생했으면 partial, 전량 정상이면 completed
    public void completePlacing(boolean hasDefect) {
        this.status = hasDefect
                ? InboundOrderStatus.partial
                : InboundOrderStatus.completed;
        this.updatedAt = LocalDateTime.now();
    }

    // [하위호환] 기존 inspect API용
    public void startInspection() {
        this.status = InboundOrderStatus.placing;
        this.updatedAt = LocalDateTime.now();
    }

    public void completeInspection(boolean allCompleted) {
        this.status = InboundOrderStatus.completed;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
