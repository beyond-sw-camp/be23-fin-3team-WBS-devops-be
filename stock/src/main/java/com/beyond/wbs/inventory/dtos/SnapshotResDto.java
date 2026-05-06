package com.beyond.wbs.inventory.dtos;

import com.beyond.wbs.inventory.domain.InventorySnapshot;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 월별 마감 스냅샷 조회 응답
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class SnapshotResDto {
    private UUID id;
    private UUID productId;
    private UUID warehouseId;
    private UUID locationId;
    private LocalDate snapshotMonth;
    private Integer openQty;
    private Integer closeQty;
    private Integer inboundQty;
    private Integer outboundQty;

    // Feign 조회 결과 (이름 표시용)
    private String productName;
    private String warehouseName;

    public static SnapshotResDto fromEntity(InventorySnapshot s,
                                             String productName,
                                             String warehouseName) {
        return SnapshotResDto.builder()
                .id(s.getId())
                .productId(s.getProductId())
                .warehouseId(s.getWarehouseId())
                .locationId(s.getLocationId())
                .snapshotMonth(s.getSnapshotMonth())
                .openQty(s.getOpenQty())
                .closeQty(s.getCloseQty())
                .inboundQty(s.getInboundQty())
                .outboundQty(s.getOutboundQty())
                .productName(productName)
                .warehouseName(warehouseName)
                .build();
    }
}
