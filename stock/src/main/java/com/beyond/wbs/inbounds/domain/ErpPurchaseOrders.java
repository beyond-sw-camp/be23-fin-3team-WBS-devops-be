package com.beyond.wbs.inbounds.domain;

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
@Table(name = "erp_purchase_orders")
public class ErpPurchaseOrders {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (회사)
    @Column(name = "client_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID clientId;

    // Master Service 참조 (입고처)
    @Column(name = "supplier_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID supplierId;

    // 발주서 번호
    @Column(name = "po_no", nullable = false, length = 30)
    private String poNo;

    // 발주서 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ErpPurchaseOrderStatus status = ErpPurchaseOrderStatus.draft;

    // 발주일
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    // 납기예정일
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    // 비고
    @Column(columnDefinition = "TEXT")
    private String note;

    // 생성일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
// 더미데이터 (ERP 발주서):
//  이미 id 포함해서 DB에 넣어두는 것
//  → @PrePersist 필요 없음
