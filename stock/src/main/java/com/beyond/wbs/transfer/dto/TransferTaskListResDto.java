package com.beyond.wbs.transfer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 모바일 — 이동지시서 작업 목록.
 *
 * <p>PICK 단계 → PLACE 단계 순서로 진행. 각 리스트는 rackCode → locationCode 정렬.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferTaskListResDto {
    private UUID orderId;
    private String orderNo;
    private String fromWarehouseName;
    private String toWarehouseName;
    private String status;
    private List<TransferTaskResDto> pickTasks;
    private List<TransferTaskResDto> placeTasks;
}
