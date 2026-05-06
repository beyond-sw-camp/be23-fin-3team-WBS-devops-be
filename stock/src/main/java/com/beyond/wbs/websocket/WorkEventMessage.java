package com.beyond.wbs.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 모바일 작업자 액션이 발생했음을 웹 관리자에게 알리는 메시지.
 *
 * <p>모듈/액션 종류와 상관없이 공통 형식으로 통일.
 * 프론트는 module + type 조합으로 분기 처리.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkEventMessage {

    /** 모듈 식별자: "transfer", "inbound", "outbound", "placement" 등 */
    private String module;

    /** 액션 종류: "PICKED", "PLACED", "COMPLETED", "RECEIVED", "DISPATCHED" 등 */
    private String type;

    /** 회사(테넌트) 식별자 — destination 분리 외에 페이로드 단에서도 검증 가능하도록 포함 */
    private UUID clientId;

    /** 대상 지시서 ID */
    private UUID orderId;

    /** 표시용 지시서 번호 (예: "TR-2026-001") */
    private String orderNo;

    /** 액션 수행자 ID */
    private UUID userId;

    /** 발생 시각 */
    private LocalDateTime occurredAt;
}
