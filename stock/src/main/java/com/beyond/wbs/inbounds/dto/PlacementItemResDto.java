package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.inbounds.domain.PlacementItems;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PlacementItemResDto {
    private UUID id;
    private UUID placementOrderId;
    private UUID inboundOrderId;
    private UUID warehouseId;
    private String orderNo;
    private String placementNo;
    private Integer seq;
    private UUID productId;
    private UUID locationId;
    private String locationCode;
    private String sku;
    private String productName;
    private Integer qty;
    private Integer defectQty;   // 적치 중 작업자가 보고한 불량 수량
    private String lotNo;
    private String zoneName;
    private UUID rackId;
    private String rackCode;
    private boolean isPlaced;
    private boolean defect;
    private String unassignedReason;

    public static PlacementItemResDto fromEntity(PlacementItems item, int seq,
                                                  String zoneName, UUID rackId, String rackCode,
                                                  String locationCode,
                                                  UUID inboundOrderId, UUID warehouseId, String orderNo,
                                                  String placementNo, ProductResDto product) {
        String sku = product != null && product.getSku() != null ? product.getSku() : "";
        String name = product != null && product.getName() != null ? product.getName() : "";
        return PlacementItemResDto.builder()
                .id(item.getId())
                .placementOrderId(item.getPlacementOrderId())
                .inboundOrderId(inboundOrderId)
                .warehouseId(warehouseId)
                .orderNo(orderNo)
                .placementNo(placementNo)
                .productId(item.getProductId())
                .locationId(item.getLocationId())
                .locationCode(locationCode)
                .sku(sku)
                .productName(name)
                .qty(item.getQty())
                .defectQty(item.getDefectQty())
                .lotNo(item.getLotNo())
                .zoneName(zoneName)
                .rackId(rackId)
                .rackCode(rackCode)
                .seq(seq)
                .isPlaced(item.isPlaced())
                .defect(item.isDefect())
                .build();
    }

    public static PlacementItemResDto fromEntity(PlacementItems item, int seq,
                                                  String zoneName, UUID rackId, String rackCode,
                                                  String locationCode,
                                                  UUID inboundOrderId, UUID warehouseId, String orderNo,
                                                  String placementNo) {
        return fromEntity(item, seq, zoneName, rackId, rackCode, locationCode,
                inboundOrderId, warehouseId, orderNo, placementNo, null);
    }
}
