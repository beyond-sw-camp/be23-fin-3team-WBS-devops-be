package com.beyond.wbs.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 모바일 — 단일 PICK 또는 PLACE 작업 카드.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferTaskResDto {
    private UUID itemId;
    private UUID locationId;
    private UUID rackId;
    private String rackCode;
    private String locationCode;
    private UUID productId;
    private String productName;
    private Integer qty;             // 남은 처리 수량 (PICK: orderedQty - pickedQty, PLACE: pickedQty - 처리완료)
    private String status;
}
