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

/**
 * 입고 전표 품목 - 순수 검수 기록만 담당
 * 적치 관련(위치, 완료 여부)은 PlacementItems에서 관리
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "inbound_receipt_items")
public class InboundReceiptItems {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 입고전표 ID
    @Column(name = "receipt_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID receiptId;

    // 입고지시서품목 ID
    @Column(name = "order_item_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderItemId;

    // 상품 ID
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // 입고 수량
    @Column(nullable = false)
    private Integer qty;

    // LOT 번호
    @Column(name = "lot_no", length = 50)
    private String lotNo;

    // 유통기한
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    // 상태 (normal / defect / damaged)
    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", nullable = false, length = 20)
    private InboundReceiptItemCondition itemCondition;

    // 검수자
    @Column(name = "inspected_by", columnDefinition = "BINARY(16)")
    private UUID inspectedBy;

    // 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 정상품 검수 기록 생성
    public static InboundReceiptItems createNormal(UUID receiptId, UUID orderItemId, UUID productId,
                                                    int qty, String lotNo, UUID inspectedBy) {
        return InboundReceiptItems.builder()
                .receiptId(receiptId)
                .orderItemId(orderItemId)
                .productId(productId)
                .qty(qty)
                .lotNo(lotNo)
                .itemCondition(InboundReceiptItemCondition.normal)
                .inspectedBy(inspectedBy)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // 하위 호환
    public static InboundReceiptItems createNormal(UUID receiptId, UUID orderItemId, UUID productId,
                                                    int qty, String lotNo) {
        return createNormal(receiptId, orderItemId, productId, qty, lotNo, null);
    }

    // 불량품 검수 기록 생성
    public static InboundReceiptItems createDefect(UUID receiptId, UUID orderItemId, UUID productId,
                                                    int qty, String lotNo, UUID inspectedBy) {
        return InboundReceiptItems.builder()
                .receiptId(receiptId)
                .orderItemId(orderItemId)
                .productId(productId)
                .qty(qty)
                .lotNo(lotNo)
                .itemCondition(InboundReceiptItemCondition.defect)
                .inspectedBy(inspectedBy)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // 하위 호환
    public static InboundReceiptItems createDefect(UUID receiptId, UUID orderItemId, UUID productId,
                                                    int qty, String lotNo) {
        return createDefect(receiptId, orderItemId, productId, qty, lotNo, null);
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
