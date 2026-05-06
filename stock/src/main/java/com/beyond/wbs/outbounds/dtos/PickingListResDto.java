package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.PickingListStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
public class PickingListResDto {

    // 피킹리스트 ID
    private UUID id;

    // 피킹리스트 번호 (PK-00001)
    private String pickingNo;

    // 창고명 (Feign client로 Master Service 조회)
    private String warehouseName;

    // 담당자 UUID — 프론트에서 로컬 사용자 캐시로 이름 해석 (Feign 실패 대비 폴백)
    private UUID assignedTo;

    // 담당자명 (Feign client로 Account Service users.name 조회)
    private String assignedToName;

    // 생성자명 (Feign client로 Account Service users.name 조회)
    private String createdByName;

    // 피킹리스트 상태
    private PickingListStatus status;

    // 피킹 시작일시
    private LocalDateTime startedAt;

    // 피킹 완료일시
    private LocalDateTime completedAt;

    // 생성일시
    private LocalDateTime createdAt;
}

