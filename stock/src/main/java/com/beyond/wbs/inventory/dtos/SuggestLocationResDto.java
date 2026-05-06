package com.beyond.wbs.inventory.dtos;

import lombok.*;

import java.util.UUID;

/**
 * 적치 위치 추천 응답 DTO
 * - 프론트에서 검수 화면에 추천 결과를 보여줄 때 사용
 * - 작업자가 확인 후 수정도 가능
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@ToString
@Builder
public class SuggestLocationResDto {
    private UUID locationId;
    private UUID rackId;
    private String rackCode;
    private String locationCode;
    private Integer floorNo;
    private String reason;
    private Integer currentQty;
    private Integer maxCapacity;        // 최대 수용량 (null = 무제한)
    private Integer currentUsed;        // 점유 수량: available + reserved + pending (defect/incoming 제외)
    private Integer availableCapacity;  // 수용 가능 잔여 = max - used (음수는 0; null = 무제한)
    private Integer remainCapacity;     // [legacy] availableCapacity 와 동일 값. 기존 호출자 호환.
}
