package com.beyond.wbs.etcinout.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 기타출고 진행을 위해 정식 출고지시서를 취소했을 때 남기는 추적 링크.
 *
 * <p>한 기타출고가 여러 출고지시서를 취소시킬 수 있어 1:N.
 * 취소된 출고지시서 입장에서도 "왜 취소됐나" 역조회 가능.
 */
@Entity
@Table(name = "etc_inout_cancellation_links",
        indexes = {
                @Index(name = "idx_eicl_etc_order", columnList = "etc_order_id"),
                @Index(name = "idx_eicl_outbound", columnList = "cancelled_outbound_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EtcInoutCancellationLink {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID id;

    // 어느 기타출고 때문에 취소했나
    @Column(name = "etc_order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID etcOrderId;

    // 어느 정식 출고지시서를 취소했나
    @Column(name = "cancelled_outbound_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID cancelledOutboundId;

    // 취소 처리한 운영자
    @Column(name = "cancelled_by", columnDefinition = "BINARY(16)", nullable = false)
    private UUID cancelledBy;

    @Column(name = "cancelled_at", nullable = false)
    private LocalDateTime cancelledAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.cancelledAt == null) {
            this.cancelledAt = LocalDateTime.now();
        }
    }
}
