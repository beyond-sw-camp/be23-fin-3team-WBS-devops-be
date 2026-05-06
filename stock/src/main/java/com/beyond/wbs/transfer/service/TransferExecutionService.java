package com.beyond.wbs.transfer.service;

import com.beyond.wbs.transfer.dto.TransferExecutionResDto;
import com.beyond.wbs.transfer.repository.TransferExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 이동 실행 이력 조회 전용 서비스
 * 쓰기는 TransferOrderService.processPickItem / processPlaceItem 에서 처리.
 */
@Service
public class TransferExecutionService {

    private final TransferExecutionRepository executionRepository;

    public TransferExecutionService(TransferExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransferExecutionResDto> search(UUID transferOrderId,
                                                  UUID executedBy,
                                                  LocalDateTime fromDate,
                                                  LocalDateTime toDate,
                                                  Pageable pageable) {
        return executionRepository.searchExecutions(
                transferOrderId, executedBy, fromDate, toDate, pageable
        ).map(TransferExecutionResDto::fromEntity);
    }
}
