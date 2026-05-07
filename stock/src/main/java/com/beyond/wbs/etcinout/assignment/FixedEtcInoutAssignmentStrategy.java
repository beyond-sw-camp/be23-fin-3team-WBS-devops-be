package com.beyond.wbs.etcinout.assignment;

import com.beyond.wbs.assignment.WorkAssignmentService;
import com.beyond.wbs.assignment.WorkTaskType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 기타 입출고 자동 배정 구현체.
 */
@Component
@RequiredArgsConstructor
public class FixedEtcInoutAssignmentStrategy implements EtcInoutAssignmentStrategy {

    private final WorkAssignmentService workAssignmentService;

    @Override
    public UUID assign(UUID clientId, UUID requesterId) {
        return workAssignmentService.assign(WorkTaskType.ETC_INOUT, clientId, requesterId);
    }
}
