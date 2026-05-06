package com.beyond.wbs.inventory.dtos;

import com.beyond.wbs.inventory.domain.StockCountItem;
import com.beyond.wbs.inventory.domain.StockCountItemStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountItemResDto {
    private UUID id;
    private UUID productId;
    private UUID locationId;
    private Integer systemQty;  // 시스템 수량
    private Integer countQty;   // 실사 수량
    private Integer diffQty;    // 차이 (countQty - systemQty)
    private StockCountItemStatus status;
    private UUID countedBy;
    private LocalDateTime countedAt;
    private String note;

    // 모바일 enrichment — master 에서 조회한 사람이 읽을 정보
    private String productSku;
    private String productName;
    private String locationCode;
    private Integer floorNo;
    private UUID rackId;
    private String rackCode;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;

    public static StockCountItemResDto from(StockCountItem item) {
        return StockCountItemResDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .locationId(item.getLocationId())
                .systemQty(item.getSystemQty())
                .countQty(item.getCountQty())
                .diffQty(item.getDiffQty())
                .status(item.getStatus())
                .countedBy(item.getCountedBy())
                .countedAt(item.getCountedAt())
                .note(item.getNote())
                .build();
    }
}