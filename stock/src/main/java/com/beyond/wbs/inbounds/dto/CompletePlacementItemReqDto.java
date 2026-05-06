package com.beyond.wbs.inbounds.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 적치 품목 개별 완료 요청 바디.
 * 값이 없거나 defectQty=0 이면 전량 정상 적치.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompletePlacementItemReqDto {

    // 적치 중 작업자가 발견한 불량 수량 (0 또는 null 이면 불량 없음)
    private Integer defectQty;

    // 불량 사유 (예: "파손", "분실", "이물질")
    private String defectReason;
}
