package com.beyond.wbs.etcinout.repository;

import com.beyond.wbs.etcinout.domain.EtcInoutOrder;
import com.beyond.wbs.etcinout.domain.EtcInoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EtcInoutOrderRepository extends JpaRepository<EtcInoutOrder, UUID>,
        JpaSpecificationExecutor<EtcInoutOrder> {

    // 모바일: 본인에게 배정된 기록 목록 (상태 필터 옵셔널)
    Page<EtcInoutOrder> findByClientIdAndAssignedToOrderByCreatedAtDesc(
            UUID clientId, UUID assignedTo, Pageable pageable);

    Page<EtcInoutOrder> findByClientIdAndAssignedToAndStatusInOrderByCreatedAtDesc(
            UUID clientId, UUID assignedTo, List<EtcInoutStatus> statuses, Pageable pageable);
}