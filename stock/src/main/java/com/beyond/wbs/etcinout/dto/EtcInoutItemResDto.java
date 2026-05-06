package com.beyond.wbs.etcinout.dto;

import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.etcinout.domain.EtcInoutItemStatus;
import com.beyond.wbs.etcinout.domain.EtcInoutOrderItem;
import com.beyond.wbs.etcinout.domain.ItemCondition;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutItemResDto {

    private UUID id;
    private UUID productId;
    private String productName;

    // 위치 — UUID + 사람 친화 코드들
    private UUID locationId;
    private String locationCode;
    private Integer floorNo;
    private UUID rackId;
    private String rackCode;
    private UUID zoneId;
    private String zoneCode;
    private String zoneName;

    private Integer qty;
    private Integer processedQty;
    private Integer defectQty;
    private Integer pickedQty;
    private String lotNo;
    private ItemCondition condition;
    private EtcInoutItemStatus status;
    private String defectReason;
    private String note;
    private UUID processedBy;
    private LocalDateTime processedAt;

    public static EtcInoutItemResDto fromEntity(EtcInoutOrderItem item, String productName) {
        return fromEntity(item, productName, null);
    }

    public static EtcInoutItemResDto fromEntity(EtcInoutOrderItem item,
                                                 String productName,
                                                 LocationResDto location) {
        return EtcInoutItemResDto.builder()
                .id(item.getId())
                .productId(item.getProductId())
                .productName(productName)
                .locationId(item.getLocationId())
                .locationCode(location != null ? location.getCode() : null)
                .floorNo(location != null ? location.getFloorNo() : null)
                .rackId(location != null ? location.getRackId() : null)
                .rackCode(location != null ? location.getRackCode() : null)
                .zoneId(location != null ? location.getZoneId() : null)
                .zoneCode(location != null ? location.getZoneCode() : null)
                .zoneName(location != null ? location.getZoneName() : null)
                .qty(item.getQty())
                .processedQty(item.getProcessedQty())
                .defectQty(item.getDefectQty())
                .pickedQty(item.getPickedQty())
                .lotNo(item.getLotNo())
                .condition(item.getCondition())
                .status(item.getStatus())
                .defectReason(item.getDefectReason())
                .note(item.getNote())
                .processedBy(item.getProcessedBy())
                .processedAt(item.getProcessedAt())
                .build();
    }
}
