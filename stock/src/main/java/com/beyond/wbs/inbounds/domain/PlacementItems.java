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
@Table(name = "placement_items")
public class PlacementItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 적치 지시서 ID
    @Column(name = "placement_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID placementOrderId;

    // 입고 지시서 품목 ID
    @Column(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    // 상품 ID
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 적치 위치 ID
    //  - 자동 추천 성공 → 위치 지정된 상태로 생성
    //  - 자동 추천 실패/용량 부족 → null 로 저장 ("위치 미정", 관리자 수동 지정 필요)
    @Column(name = "location_id", columnDefinition = "BINARY(16)")
    private UUID locationId;

    // 적치 수량 (계획된 정상 수량)
    @Column(nullable = false)
    private Integer qty;

    // 적치 중 작업자가 보고한 불량 수량 (0 이면 전량 정상 적치)
    @Builder.Default
    @Column(name = "defect_qty", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer defectQty = 0;

    // LOT 번호
    @Column(name = "lot_no", length = 50)
    private String lotNo;

    // 불량품 적치 여부 (true면 defect@null → defect@실위치)
    @Builder.Default
    @Column(name = "is_defect", nullable = false)
    private boolean isDefect = false;

    // 적치 완료 여부
    @Builder.Default
    @Column(nullable = false)
    private boolean isPlaced = false;

    // 적치 완료자
    @Column(name = "completed_by", columnDefinition = "BINARY(16)")
    private UUID completedBy;

    // 적치 완료 일시
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // 생성일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 적치 완료 처리
    public void completePlacement(UUID userId) {
        this.isPlaced = true;
        this.completedBy = userId;
        this.completedAt = LocalDateTime.now();
    }

    // 하위 호환 (userId 없는 호출)
    public void completePlacement() {
        this.isPlaced = true;
        this.completedAt = LocalDateTime.now();
    }

    // 적치 항목 생성 (정상품)
    public static PlacementItems create(UUID placementOrderId, UUID orderItemId,
                                         UUID productId, UUID locationId,
                                         int qty, String lotNo) {
        return create(placementOrderId, orderItemId, productId, locationId, qty, lotNo, false);
    }

    // 적치 항목 생성 (정상/불량 구분)
    public static PlacementItems create(UUID placementOrderId, UUID orderItemId,
                                         UUID productId, UUID locationId,
                                         int qty, String lotNo, boolean isDefect) {
        return PlacementItems.builder()
                .placementOrderId(placementOrderId)
                .orderItemId(orderItemId)
                .productId(productId)
                .locationId(locationId)
                .qty(qty)
                .lotNo(lotNo)
                .isDefect(isDefect)
                .isPlaced(false)
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
