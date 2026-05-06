package com.beyond.wbs.etcinout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 기타출고가 가용재고 부족일 때, 운영자가 취소 후보로 선택할 수 있는 정식 출고지시서 정보.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationCandidateResDto {

    private UUID outboundOrderId;
    private String orderNo;
    private LocalDate scheduledDate;
    private UUID storeId;
    private String storeName;       // Feign 보강
    private Integer reservedQty;    // approved 면 reserve 합, draft 면 orderedQty 합
    private String status;          // "draft" 또는 "approved" — 프론트가 라벨 분기
}
