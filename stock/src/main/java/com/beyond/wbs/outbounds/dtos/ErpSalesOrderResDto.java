package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.ErpSalesOrderStatus;
import com.beyond.wbs.outbounds.domain.SalesOrderProcessStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class ErpSalesOrderResDto {
    // ERP 수주서 ID
    private UUID id;

    // 고객사 ID
    private UUID clientId;

    // 출고처 ID
    private UUID storeId;

    // 출고처명 (TODO: Feign Client로 Master Service 조회)
    private String storeName;

    // 수주서 번호
    private String soNo;

    // 수주서 상태
    private ErpSalesOrderStatus status;

    // 수주일
    private LocalDate orderDate;

    // 출고 예정일
    private LocalDate scheduledDate;

    // 배송지
    private String shippingAddress;

    // 비고
    private String note;

    // 생성일시
    private LocalDateTime createdAt;

    // 이미 출고지시서로 전환되었는지 여부 — 구식 (단일 SO → OB) 호환용
    private Boolean alreadyConverted;

    // ── FE 선택 화면용 보강 필드 ──

    /** 처리 상태 — allocatedQty(분배된 양) 기준 NOT_STARTED / PARTIAL / COMPLETED */
    private SalesOrderProcessStatus processStatus;

    /** 출고 진행률 % — dispatchedQty(실제 출고된 양) 기준 0~100 */
    private Integer dispatchProgressPercent;

    /** 총 품목 종류 수 (SO 라인 row 개수) */
    private Integer itemCount;

    /** 총 주문 수량 합 (SUM of qty) */
    private Integer totalOrderedQty;

    /**
     * 품목 미리보기 — 행 1줄에 "콜라 100, 사이다 50" 같은 표시용.
     * 처음 3개 품목까지. 그 이상은 "외 N건" 표시는 FE 에서 처리 (itemCount 활용).
     */
    private List<ItemPreview> itemPreview;

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    @Builder
    public static class ItemPreview {
        private UUID productId;
        private String productName;
        private Integer qty;
    }
}
