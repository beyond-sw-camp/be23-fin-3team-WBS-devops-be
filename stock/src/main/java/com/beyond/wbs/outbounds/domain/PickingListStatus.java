package com.beyond.wbs.outbounds.domain;

public enum PickingListStatus {
    pending,        // 대기
    in_progress,    // 진행중
    completed,      // 완료
    partial;        // 부분완료
    // 이 피킹작업 전체가 완료됐는가에 대한 상태
}
