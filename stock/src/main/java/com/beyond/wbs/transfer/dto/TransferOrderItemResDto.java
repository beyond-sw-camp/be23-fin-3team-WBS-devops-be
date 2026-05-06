package com.beyond.wbs.transfer.dto;

import com.beyond.wbs.transfer.domain.TransferOrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferOrderItemResDto {
    private UUID id;
    private UUID transferOrderId;
    private UUID productId;
    private String productName;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer orderedQty;
    private Integer processedQty;
    private Integer defectQty;
    private String lotNo;
    private String status;

    public static TransferOrderItemResDto fromEntity(TransferOrderItem item, String productName) {
        return TransferOrderItemResDto.builder()
                .id(item.getId())
                .transferOrderId(item.getTransferOrderId())
                .productId(item.getProductId())
                .productName(productName)
                .fromLocationId(item.getFromLocationId())
                .toLocationId(item.getToLocationId())
                .orderedQty(item.getOrderedQty())
                .processedQty(item.getProcessedQty())
                .defectQty(item.getDefectQty())
                .lotNo(item.getLotNo())
                .status(item.getStatus().name())
                .build();
    }
}
