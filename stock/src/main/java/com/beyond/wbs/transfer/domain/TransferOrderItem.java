package com.beyond.wbs.transfer.domain;

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
@Table(name = "transfer_order_items")
public class TransferOrderItem {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 이동지시서 ID
    @Column(name = "transfer_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID transferOrderId;

    // Master Service 참조 (상품)
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // Master Service 참조 (출발 로케이션)
    @Column(name = "from_location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID fromLocationId;

    // Master Service 참조 (도착 로케이션)
    @Column(name = "to_location_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID toLocationId;

    // 지시 수량
    @Column(name = "ordered_qty", nullable = false)
    private Integer orderedQty;

    // 픽업된 수량 (출발지에서 꺼낸 누적)
    @Builder.Default
    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    // 마지막 픽업 시각
    @Column(name = "picked_at")
    private LocalDateTime pickedAt;

    // 처리된 수량 (도착지에 정상 적치까지 완료된 누적)
    @Builder.Default
    @Column(name = "processed_qty", nullable = false)
    private Integer processedQty = 0;

    // 불량 수량 (이동 중 파손 — 도착지에 불량으로 적치된 누적)
    @Builder.Default
    @Column(name = "defect_qty", nullable = false)
    private Integer defectQty = 0;

    // LOT 번호 (옵션)
    @Column(name = "lot_no", length = 50)
    private String lotNo;

    // 상태
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferOrderItemStatus status = TransferOrderItemStatus.pending;

    // [웹 관리자용] 품목 처리 (꺼내고 놓는 단일 동작 — 정상/불량 수량 반영 + 상태 변경)
    public void process(int goodQty, int defectQty) {
        this.processedQty += goodQty;
        this.defectQty += defectQty;

        int handled = this.processedQty + this.defectQty;
        if (handled >= this.orderedQty) {
            this.status = (this.defectQty > 0)
                    ? TransferOrderItemStatus.shortage
                    : TransferOrderItemStatus.completed;
        } else {
            this.status = TransferOrderItemStatus.in_progress;
        }
    }

    // [모바일] 픽업 — 출발지에서 꺼냄. 누적 후 상태 갱신.
    public void pick(int qty) {
        this.pickedQty += qty;
        this.pickedAt = LocalDateTime.now();
        this.status = (this.pickedQty >= this.orderedQty)
                ? TransferOrderItemStatus.picked
                : TransferOrderItemStatus.in_progress;
    }

    // [모바일] 적치 — 도착지에 놓음. 정상/불량 수량 반영 + 상태 갱신.
    public void place(int goodQty, int defectQty) {
        this.processedQty += goodQty;
        this.defectQty += defectQty;

        int handled = this.processedQty + this.defectQty;
        if (handled >= this.orderedQty) {
            this.status = (this.defectQty > 0)
                    ? TransferOrderItemStatus.shortage
                    : TransferOrderItemStatus.completed;
        } else {
            this.status = TransferOrderItemStatus.in_progress;
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
