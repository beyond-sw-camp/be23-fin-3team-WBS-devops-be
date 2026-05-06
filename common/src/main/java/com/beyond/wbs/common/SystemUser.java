package com.beyond.wbs.common;

import java.util.UUID;

/**
 * 시스템 자동 작업의 행위자 식별자.
 *
 * 다음 케이스에서 사용:
 *  - 스케줄러/배치 (예: 매일 웨이브 자동생성, 월별 재고 스냅샷)
 *  - 자동 예약 (적치 완료 시 미예약 출고지시서 자동 reserve)
 *  - 기타 사용자 트리거 없는 백엔드 자동 처리
 *
 * 실제 user 테이블의 레코드와 무관한 sentinel 값이다.
 * created_by 컬럼에 이 UUID 가 들어있으면 "시스템이 한 작업" 으로 식별 가능.
 */
public final class SystemUser {

    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SystemUser() {}
}
