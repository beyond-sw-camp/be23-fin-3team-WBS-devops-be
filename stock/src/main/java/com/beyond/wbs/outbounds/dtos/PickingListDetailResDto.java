package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.PickingListStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class PickingListDetailResDto {
    // 피킹리스트 ID
    private UUID id;

    // 피킹리스트 번호 (PK-00001)
    private String pickingNo;

    // 창고명 (TODO: Feign Client로 Master Service 조회)
    private String warehouseName;

    // 담당자 UUID — 프론트에서 로컬 사용자 캐시로 이름 해석 (Feign 실패 대비 폴백)
    private UUID assignedTo;

    // 담당자명 (TODO: Feign Client로 Account Service 조회)
    private String assignedToName;

    // 생성자명 (TODO: Feign Client로 Account Service 조회)
    private String createdByName;

    // 피킹리스트 상태
    private PickingListStatus status;

    // 피킹 시작 일시
    private LocalDateTime startedAt;

    // 피킹 완료 일시
    private LocalDateTime completedAt;

    // 생성 일시
    private LocalDateTime createdAt;

    // 피킹 품목 목록
    private List<PickingListItemResDto> items;

}
