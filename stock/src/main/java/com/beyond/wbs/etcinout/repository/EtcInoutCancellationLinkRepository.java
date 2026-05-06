package com.beyond.wbs.etcinout.repository;

import com.beyond.wbs.etcinout.domain.EtcInoutCancellationLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EtcInoutCancellationLinkRepository
        extends JpaRepository<EtcInoutCancellationLink, UUID> {

    // 한 기타출고가 어떤 출고지시서들을 취소시켰는지
    List<EtcInoutCancellationLink> findByEtcOrderId(UUID etcOrderId);

    // 한 출고지시서가 어떤 기타출고 때문에 취소됐는지 (역조회)
    List<EtcInoutCancellationLink> findByCancelledOutboundId(UUID cancelledOutboundId);
}
