package com.beyond.wbs.etcinout.assignment;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 임시 구현체 — account 모듈 InitialDataLoad.FIXED_OPERATOR1_ID 와 동일한 UUID 를 반환한다.
 * AI 담당자가 실제 배당 로직 구현체를 만들면 이 클래스는 제거 대상.
 */
@Component
public class FixedEtcInoutAssignmentStrategy implements EtcInoutAssignmentStrategy {

    // account InitialDataLoad.FIXED_OPERATOR1_ID 와 반드시 일치해야 함
    private static final UUID FIXED_OPERATOR1_ID =
            UUID.fromString("01935c00-0000-7200-8000-000000000001");

    @Override
    public UUID assign() {
        return FIXED_OPERATOR1_ID;
    }
}
