package com.beyond.wbs.outbounds.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 출고전표 목록 한 행.
 *
 * 1행 = 1전표. 부모 출고지시서 정보 + 출처 수주서 (분할 출고로 N개 가능) 포함.
 *
 * originType:
 *   - "sales_order" : originRefs 에 OutboundSalesOrderLinks 통해 연결된 SO 들 (1~N개)
 *   - "manual"      : originRefs = empty list
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class OutboundDispatchListItemResDto {
    // 전표
    private UUID id;
    private String dispatchNo;
    private LocalDateTime dispatchedAt;
    private LocalDateTime createdAt;

    // 부모 출고지시서
    private UUID outboundOrderId;
    private String orderNo;

    // 출처 (수주서 / 수동)
    private String originType;
    private List<OriginRef> originRefs;

    // 창고
    private UUID warehouseId;
    private String warehouseName;

    // 출고처 (Store)
    private UUID storeId;
    private String storeName;

    // 출고 담당자
    private UUID dispatchedBy;
    private String dispatchedByName;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class OriginRef {
        private UUID id;     // SO 등 출처 ID
        private String no;   // SO 번호 (so_no)
    }
}
