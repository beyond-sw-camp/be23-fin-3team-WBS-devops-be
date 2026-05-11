package com.beyond.wbs.statistic.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 처리 필요 지시서 1건 — 입고/출고/이동 통합 표현.
 *
 * 카테고리(category):
 *  - DELAYED  : scheduledDate < today (마감일 지남)
 *  - TODAY    : scheduledDate == today (오늘 마감)
 *  - UPCOMING : scheduledDate > today (미래 예정, 통합 페이지에서만 노출)
 *
 * 코드에 마감시각 / 운영시간 개념이 없어 시각 단위 분류(URGENT)는 사용하지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingOrderItemDto {
    private String type;          // INBOUND | OUTBOUND | TRANSFER
    private UUID orderId;
    private String orderNo;
    private String partnerName;   // 입고:공급사명 / 출고:거래처명 / 이동:목적지창고명
    private String status;        // 도메인 상태값 문자열
    private LocalDate scheduledDate;  // 입고/이동:expectedDate, 출고:scheduledDate
    private String category;      // DELAYED | TODAY | UPCOMING
    private Integer delayDays;    // DELAYED 일 때만 양수, 그 외 0
    private Integer itemCount;
    private Integer totalQty;
    private LocalDateTime createdAt;
    private UUID assignedTo;
}
