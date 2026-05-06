package com.beyond.wbs.transfer.dto;

import com.beyond.wbs.transfer.domain.TransferExecution;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferExecutionResDto {
    private UUID id;
    private UUID transferOrderId;
    private UUID orderItemId;
    private UUID productId;
    private UUID fromLocationId;
    private UUID toLocationId;
    private Integer qty;
    private Integer defectQty;
    private String lotNo;
    private UUID executedBy;
    private String executedAt;

    public static TransferExecutionResDto fromEntity(TransferExecution e) {
        return TransferExecutionResDto.builder()
                .id(e.getId())
                .transferOrderId(e.getTransferOrderId())
                .orderItemId(e.getOrderItemId())
                .productId(e.getProductId())
                .fromLocationId(e.getFromLocationId())
                .toLocationId(e.getToLocationId())
                .qty(e.getQty())
                .defectQty(e.getDefectQty())
                .lotNo(e.getLotNo())
                .executedBy(e.getExecutedBy())
                .executedAt(e.getExecutedAt().toString())
                .build();
    }
}
