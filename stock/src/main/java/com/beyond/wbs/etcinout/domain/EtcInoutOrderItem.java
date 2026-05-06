package com.beyond.wbs.etcinout.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "etc_inout_order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EtcInoutOrderItem {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID id;

    // 기타입출고 지시서
    @Column(name = "etc_order_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID etcOrderId;

    // 상품
    @Column(name = "product_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID productId;

    // 위치
    @Column(name = "location_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID locationId;

    // 요청 수량
    @Column(name = "qty", nullable = false)
    private Integer qty;

    // 처리 수량 (정상 수량)
    @Builder.Default
    @Column(name = "processed_qty", nullable = false)
    private Integer processedQty = 0;

    // 불량 수량 (입고)
    @Builder.Default
    @Column(name = "defect_qty", nullable = false)
    private Integer defectQty = 0;

    // 실제 픽업 수량 (출고)
    @Builder.Default
    @Column(name = "picked_qty", nullable = false)
    private Integer pickedQty = 0;

    // lot번호
    @Column(name = "lot_no", length = 50)
    private String lotNo;

    //물품 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition")
    private ItemCondition condition;

    // 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private EtcInoutItemStatus status = EtcInoutItemStatus.pending;

    // 상세 사유
    @Column(name = "defect_reason")
    private String defectReason;

    // 비고
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // 처리자 id
    @Column(name = "processed_by", columnDefinition = "BINARY(16)")
    private UUID processedBy;

    // 처리 일시
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    // 품목 수정
    public void update(UUID productId, UUID locationId, Integer qty,
                       String lotNo, ItemCondition condition, String defectReason, String note) {
        this.productId = productId;
        this.locationId = locationId;
        this.qty = qty;
        this.lotNo = lotNo;
        this.condition = condition;
        this.defectReason = defectReason;
        this.note = note;
    }

    // 상태 변경
    public void updateStatus(EtcInoutItemStatus status) {
        this.status = status;
    }

    // 모바일 작업자가 출고 픽업 결과 기록 (위치 변경 권한 없음)
    public void recordPickResult(int pickedQty, UUID processedBy) {
        if (pickedQty < 0 || pickedQty > this.qty) {
            throw new IllegalArgumentException(
                    "픽업 수량(" + pickedQty + ")이 요청 수량(" + this.qty + ") 범위를 벗어납니다.");
        }
        this.pickedQty = pickedQty;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.status = pickedQty < this.qty
                ? EtcInoutItemStatus.shortage
                : EtcInoutItemStatus.completed;
    }

    // 모바일 작업자가 현장에서 정상/불량 split + 위치 + 사유 입력 후 완료 처리
    public void recordWorkResult(int processedQty, int defectQty,
                                 UUID locationId, String defectReason,
                                 UUID processedBy) {
        if (processedQty < 0 || defectQty < 0) {
            throw new IllegalArgumentException("정상/불량 수량은 0 이상이어야 합니다.");
        }
        if (processedQty + defectQty != this.qty) {
            throw new IllegalArgumentException(
                    "정상(" + processedQty + ") + 불량(" + defectQty
                            + ") 합계가 요청 수량(" + this.qty + ")과 일치해야 합니다.");
        }
        this.processedQty = processedQty;
        this.defectQty = defectQty;
        if (locationId != null) {
            this.locationId = locationId;
        }
        if (defectReason != null) {
            this.defectReason = defectReason;
        }
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.status = EtcInoutItemStatus.completed;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.status == null) {
            this.status = EtcInoutItemStatus.pending;
        }
        if (this.processedQty == null) {
            this.processedQty = 0;
        }
        if (this.defectQty == null) {
            this.defectQty = 0;
        }
        if (this.pickedQty == null) {
            this.pickedQty = 0;
        }
    }
}