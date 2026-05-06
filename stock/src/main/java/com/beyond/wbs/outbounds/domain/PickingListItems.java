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
@Table(name = "picking_list_items")
public class PickingListItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Stock Service 내부 참조 (피킹 리스트)
    @Column(name = "picking_list_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID pickingListId;

    // Stock Service 내부 참조 (출고지시서 품목)
    @Column(name = "outbound_order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID outboundOrderItemId;

    // Master Service 참조 (상품)
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // Master Service 참조 (위치 - 랙) / notnull 설정해야함 -> inventory 엔티티 연결 후 다시 nullable false설정
    @Column(name = "location_id", columnDefinition = "BINARY(16)")
    private UUID locationId;

    // 목표 피킹 수량
    @Column(nullable = false)
    private Integer qty;

    // 실제 피킹 수량 (기본값 0)
    @Column(name = "picked_qty", nullable = false)
    @Builder.Default
    private Integer pickedQty = 0;

    // LOT 번호
    @Column(name = "lot_no")
    private String lotNo;

    // 피킹 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PickingListItemStatus status = PickingListItemStatus.pending;

    // 피킹 작업자
    @Column(name = "picked_by", columnDefinition = "BINARY(16)")
    private UUID pickedBy;

    // 피킹 완료 일시
    @Column(name = "picked_at")
    private LocalDateTime pickedAt;

    @PrePersist
    // DB에 저장(INSERT)하기 직전에 자동으로 실행됨
    public void prePersist() {
        if (this.id == null) {
            // id가 없으면 자동으로 UUID 생성해서 넣어줌
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    // 피킹 수량 입력
    public void pick(Integer pickedQty, PickingListItemStatus status, UUID pickedBy) {
        this.pickedQty = pickedQty;
        this.status = status;
        this.pickedBy = pickedBy;
        this.pickedAt = LocalDateTime.now();
    }

    // 하위 호환
    public void pick(Integer pickedQty, PickingListItemStatus status) {
        pick(pickedQty, status, null);
    }

}
