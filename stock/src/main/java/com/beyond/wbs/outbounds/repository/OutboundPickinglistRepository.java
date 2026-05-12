package com.beyond.wbs.outbounds.repository;

import com.beyond.wbs.outbounds.domain.OutboundPickinglist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboundPickinglistRepository extends JpaRepository<OutboundPickinglist, UUID> {

    // 특정 출고지시서에 연결된 피킹리스트 매핑 전체
    List<OutboundPickinglist> findByOutboundOrderId(UUID outboundOrderId);

    // 특정 피킹리스트에 엮인 출고지시서 매핑 전체
    List<OutboundPickinglist> findByPickingListId(UUID pickingListId);

    // 페이지 단위 N+1 회피용 — 여러 피킹리스트의 매핑을 한 번에 조회
    List<OutboundPickinglist> findByPickingListIdIn(List<UUID> pickingListIds);
}
