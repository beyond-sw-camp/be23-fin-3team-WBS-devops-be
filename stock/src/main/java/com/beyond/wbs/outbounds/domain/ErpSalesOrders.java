package com.beyond.wbs.outbounds.domain;

import com.beyond.wbs.outbounds.domain.ErpSalesOrderStatus;
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
@Table(name = "erp_sales_orders")
public class ErpSalesOrders {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (회사)
    @Column(name = "client_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID clientId;

    // Master Service 참조 (출고처)
    @Column(name = "store_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID storeId;

    // 수주서 번호 (자동채번)
    @Column(name = "so_no", nullable = false)
    private String salesOrderNumber;

    // 수주서 상태
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ErpSalesOrderStatus status = ErpSalesOrderStatus.draft;

    // 수주일
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    // 출고예정일
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    // 배송지
    @Column(name = "shipping_address")
    private String shippingAddress;

    // 비고
    private String note;

    // 생성일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

}
// 더미데이터 (ERP 수주서):
//  이미 id 포함해서 DB에 넣어두는 것
//  → @PrePersist 필요 없음

//우리 시스템에서 생성하는 데이터 (출고전표 등):
//  서비스에서 객체 만들 때 id가 없음
//  → @PrePersist 필요
