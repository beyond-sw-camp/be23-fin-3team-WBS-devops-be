package com.beyond.wbs.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 수주서(SO) 기반 재고 부족 알림 DTO.
 *
 * 같은 WebSocket 채널 (/topic/admin/alerts/{clientId}) 로 보내고
 * payload 의 {@code type} 필드로 기존 low_stock 과 구분한다.
 *
 * type 값:
 *   - "so_shortage_added"    : 새로 부족 상태가 된 SO. items = 현재 부족 스냅샷.
 *   - "so_shortage_resolved" : 부족 해소된 SO. items = 직전 부족 상태 스냅샷 (Redis 에 보관된 것).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SalesOrderShortageAlertDto {
    private String type;                  // "so_shortage_added" | "so_shortage_resolved"
    private UUID salesOrderId;
    private String soNo;
    private LocalDate scheduledDate;      // 출고 예정일
    private String storeName;             // 출고처
    private List<ShortageItem> items;     // 부족 라인 (added/resolved 둘 다 채워짐)

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @Builder
    public static class ShortageItem {
        private UUID productId;
        private String productName;
        private String sku;
        private int requiredQty;          // 이 SO 라인에서 필요한 잔여 수량 (qty - allocatedQty)
        private int availableQty;         // 이 SO 가 가져갈 수 있는 ATP (앞 SO 들 차감 후)
        private int shortageQty;          // requiredQty - availableQty (양수)
    }
}
