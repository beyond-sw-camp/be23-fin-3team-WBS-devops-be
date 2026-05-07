package com.beyond.wbs.etcinout.assignment;

import java.util.UUID;

/**
 * 기타입출고 작업자 자동 배당 전략.
 *
 * <p>운영자가 기타입고 기록 생성 시 시스템이 자동으로 작업자 1명을 배정한다.
 * 한 작업자가 검수 → 적치 → 완료까지 한 흐름으로 처리.
 *
 * <p>임시 구현: {@link FixedEtcInoutAssignmentStrategy} — 고정 UUID 반환.
 * 추후 AI 담당자가 라운드 로빈 / 작업량 균등 등으로 교체.
 */
public interface EtcInoutAssignmentStrategy {

    UUID assign(UUID clientId, UUID requesterId);
}
