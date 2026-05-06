package com.beyond.wbs.inbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "inbound_receipts")
public class InboundReceipts {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (회사) — 전표 목록/필터의 client 소유 검증을 InboundOrders join 없이 직접 수행하기 위함
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // 입고지시서 ID
    @Column(name = "inbound_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID inboundOrderId;

    // Master Service 참조 (창고)
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Account Service 참조 (입고 담당자)
    @Column(name = "received_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID receivedBy;

    // 전표 번호 (자동채번)
    @Column(name = "receipt_no", nullable = false, length = 30)
    private String receiptNo;

    // 입고 일시
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    // 비고
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 입고 전표 생성
    public static InboundReceipts create(UUID clientId, UUID orderId, UUID warehouseId, UUID receivedBy, String receiptNo) {
        return InboundReceipts.builder()
                .clientId(clientId)
                .inboundOrderId(orderId)
                .warehouseId(warehouseId)
                .receivedBy(receivedBy)
                .receiptNo(receiptNo)
                .receivedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
