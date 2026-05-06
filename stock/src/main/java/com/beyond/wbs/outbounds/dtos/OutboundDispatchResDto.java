package com.beyond.wbs.outbounds.dtos;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class OutboundDispatchResDto {
    // 출고 전표 ID
    private UUID id;

    // 출고지시서 번호 (전표 인쇄 헤더에 같이 노출)
    private String orderNo;

    // 출고 전표 번호
    private String dispatchNo;

    // 창고명 (TODO: Feign Client로 Master Service 조회)
    private String warehouseName;

    // 출고처명 (TODO: Feign Client로 Master Service 조회)
    private String storeName;

    // 담당자 ID — 이름 매핑 실패 시 FE 사용자 캐시로 fallback
    private UUID dispatchedBy;

    // 담당자명 (TODO: Feign Client로 Account Service 조회)
    private String dispatchedByName;

    // 출고일시
    private LocalDateTime dispatchedAt;

    // 생성일시
    private LocalDateTime createdAt;

    // 품목 목록
    private List<OutboundDispatchItemResDto> items;
}
