package com.beyond.wbs.inbounds.domain;

public enum PlacementOrderStatus {
    pending,        // 대기 (생성됨, 작업 전)
    in_progress,    // 적치 작업 중 (일부 완료)
    completed;      // 적치 완료
}
