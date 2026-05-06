package com.beyond.wbs.outbounds.domain;

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
@Table(name = "outbound_dispatches")
public class OutboundDispatch {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (고객사)
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // Master Service 참조 - 창고 ID
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Stock Service 내부 참조 - 출고지시서 ID
    @Column(name = "outbound_orders_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID outboundOrdersId;

    // Account Service 참조 - 출고 담당자 ID
    @Column(name = "dispatched_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID dispatchedBy;

    // 출고전표번호
    @Column(name = "dispatch_no", nullable = false)
    private String dispatchNo;

    // 실제 출고 일시
    @Column(name = "dispatched_at", nullable = false)
    private LocalDateTime dispatchedAt;

    // 전표 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    // DB에 저장(INSERT)하기 직전에 자동으로 실행됨
    public void prePersist() {
        if (this.id == null) {
            // id가 없으면 자동으로 UUID 생성해서 넣어줌
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

}
