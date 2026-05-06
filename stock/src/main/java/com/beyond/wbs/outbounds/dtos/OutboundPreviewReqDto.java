package com.beyond.wbs.outbounds.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * 출고지시서 생성 전 창고별 재고 미리보기 요청.
 *
 * UX 정책상 "같은 출고예정일" 의 SO 만 한 번에 묶을 수 있음 — FE 단에서 차단되어 있으나
 * 서버에서도 검증한다. 같은 묶음 안에서 출고처(store)가 다르면 응답에서 store 별로 그룹핑된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboundPreviewReqDto {
    /** 사용자가 선택한 ERP 수주서 ID 목록 */
    private List<UUID> salesOrderIds;

    /** 자기 자신 제외용 — 추가 출고지시서 생성 시점에 미리 만들어둔 OB(draft) 가 있으면 그 ID. null 이면 전체 포함. */
    private UUID excludeOutboundOrderId;
}
