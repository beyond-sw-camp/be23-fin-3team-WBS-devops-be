package com.beyond.wbs.etcinout.dto;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 모바일 작업자 — 품목 1건 처리 완료 요청.
 *
 * <p>방향(direction)에 따라 사용 필드가 달라짐:
 * <ul>
 *   <li>입고(in): processedQty, defectQty, locationId, defectLocationId, defectReason
 *   <li>출고(out): pickedQty
 * </ul>
 *
 * <p>서버가 order.direction 으로 분기해서 적절한 필드만 사용.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutItemProcessReqDto {

    // 입고용
    @PositiveOrZero
    private Integer processedQty;   // 정상 수량
    @PositiveOrZero
    private Integer defectQty;      // 불량 수량
    private UUID locationId;        // 정상 적치 위치 (변경 시), null 이면 기존 유지
    private UUID defectLocationId;  // 불량 적치 위치 (defectQty>0 이면 필수)
    private String defectReason;    // 불량 사유

    // 출고용
    @PositiveOrZero
    private Integer pickedQty;      // 실제 픽업 수량
}
