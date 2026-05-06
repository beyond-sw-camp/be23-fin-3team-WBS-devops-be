package com.beyond.wbs.scheduler.service;

import com.beyond.wbs.common.SystemUser;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.StoreResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.outbounds.domain.ErpSalesOrders;
import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import com.beyond.wbs.outbounds.domain.OutboundOrders;
import com.beyond.wbs.outbounds.domain.OutboundPickinglist;
import com.beyond.wbs.outbounds.domain.OutboundSalesOrderLinks;
import com.beyond.wbs.outbounds.domain.PickingList;
import com.beyond.wbs.outbounds.dtos.WaveCreateReqDto;
import com.beyond.wbs.outbounds.repository.ErpSalesOrderRepository;
import com.beyond.wbs.outbounds.repository.OutboundOrderRepository;
import com.beyond.wbs.outbounds.repository.OutboundPickinglistRepository;
import com.beyond.wbs.outbounds.repository.OutboundSalesOrderLinksRepository;
import com.beyond.wbs.outbounds.repository.PickinglistRepository;
import com.beyond.wbs.outbounds.service.PickingListService;
import com.beyond.wbs.scheduler.domain.SchedulerHistory;
import com.beyond.wbs.scheduler.domain.SchedulerHistoryDetail;
import com.beyond.wbs.scheduler.repository.SchedulerHistoryDetailRepository;
import com.beyond.wbs.scheduler.repository.SchedulerHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 출고지시서 → 웨이브 자동 생성 스케줄러.
 *
 * 실행 시점: 매일 07:00
 * 대상: status=approved AND scheduled_date = 오늘
 * 처리:
 *   - 동일 clientId + warehouseId 끼리 묶어서 {@link PickingListService#createWave} 호출
 *   - createWave 는 "한 웨이브 = 한 창고" 제약이 있으므로 그에 맞춰 전처리
 *   - 그룹 단위 실패가 발생해도 다른 그룹은 계속 진행
 * 이력:
 *   - 실행 결과를 {@link SchedulerHistory} 에 저장 (수동 트리거 포함)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaveScheduler {

    private static final String JOB_NAME = "DAILY_WAVE_GENERATION";

    // 배치 작업의 created_by 마커 — 공용 SystemUser.ID 사용

    private final OutboundOrderRepository outboundOrderRepository;
    private final PickingListService pickingListService;
    private final SchedulerHistoryRepository schedulerHistoryRepository;
    private final SchedulerHistoryDetailRepository schedulerHistoryDetailRepository;
    private final OutboundSalesOrderLinksRepository outboundSalesOrderLinksRepository;
    private final OutboundPickinglistRepository outboundPickinglistRepository;
    private final PickinglistRepository pickinglistRepository;
    private final ErpSalesOrderRepository erpSalesOrderRepository;
    private final MasterServiceClient masterServiceClient;

    /** cron 자동 실행 진입점 */
    @Scheduled(cron = "0 0 7 * * *")
    public void runDaily() {
        run(false);
    }

    /**
     * 수동 트리거 진입점.
     * 시연/관리자 버튼 용. 이력에 {@code triggeredManually=true} 로 기록됨.
     */
    public SchedulerHistory runManually() {
        return run(true);
    }

    @Transactional
    public SchedulerHistory run(boolean manual) {
        SchedulerHistory history = schedulerHistoryRepository.save(
                SchedulerHistory.start(JOB_NAME, manual));
        LocalDate today = LocalDate.now();

        log.info("[WaveScheduler] 시작 date={} manual={}", today, manual);

        try {
            List<OutboundOrders> candidates = outboundOrderRepository
                    .findByStatusAndScheduledDate(OutboundOrderStatus.approved, today);

            if (candidates.isEmpty()) {
                log.info("[WaveScheduler] 대상 없음");
                history.markSkipped();
                return schedulerHistoryRepository.save(history);
            }

            // store.autoWaveEnabled = true 인 출고처의 OB 만 자동 처리 (opt-in 정책)
            // 등장하는 storeId 별로 한 번씩만 master 조회 — 캐시.
            Map<UUID, Boolean> autoWaveByStore = new HashMap<>();
            List<OutboundOrders> filtered = candidates.stream()
                    .filter(ob -> {
                        UUID storeId = ob.getStoreId();
                        if (storeId == null) return false;
                        Boolean enabled = autoWaveByStore.computeIfAbsent(storeId, sid -> {
                            try {
                                StoreResDto store = masterServiceClient.getStore(sid, ob.getClientId().toString());
                                return store != null && Boolean.TRUE.equals(store.getAutoWaveEnabled());
                            } catch (Exception e) {
                                log.warn("[WaveScheduler] store 조회 실패 storeId={} - {}", sid, e.getMessage());
                                return false;
                            }
                        });
                        return Boolean.TRUE.equals(enabled);
                    })
                    .toList();

            int excluded = candidates.size() - filtered.size();
            if (excluded > 0) {
                log.info("[WaveScheduler] autoWaveEnabled=false 출고처로 인해 {}건 제외", excluded);
            }
            if (filtered.isEmpty()) {
                log.info("[WaveScheduler] 자동 처리 대상 없음 (모든 출고처가 opt-out)");
                history.markSkipped();
                return schedulerHistoryRepository.save(history);
            }

            // client + warehouse 단위 그룹화 (createWave 가 단일 창고만 허용)
            Map<String, List<OutboundOrders>> groups = filtered.stream()
                    .collect(Collectors.groupingBy(
                            o -> o.getClientId() + "|" + o.getWarehouseId()));

            int successCount = 0;
            int failedCount = 0;
            StringBuilder failures = new StringBuilder();

            // 이름 캐시 (group 별로 한 번만 master 조회)
            Map<UUID, String> warehouseNameCache = new HashMap<>();
            Map<UUID, String> storeNameCache = new HashMap<>();
            // 처리된 OB 들 모았다가 detail 한 번에 저장
            List<OutboundOrders> processedObs = new ArrayList<>();
            // 방금 만든 PickingList ID 들 — saveDetails 에서 OB ↔ PL 매핑 정확히 잡기 위함
            Set<UUID> createdPickingListIds = new HashSet<>();

            for (Map.Entry<String, List<OutboundOrders>> e : groups.entrySet()) {
                List<OutboundOrders> group = e.getValue();
                UUID clientId = group.get(0).getClientId();

                WaveCreateReqDto dto = WaveCreateReqDto.builder()
                        .outboundOrderIds(group.stream().map(OutboundOrders::getId).toList())
                        .build();

                try {
                    List<UUID> newPlIds = pickingListService.createWave(dto, SystemUser.ID, clientId);
                    successCount++;
                    processedObs.addAll(group);
                    if (newPlIds != null) createdPickingListIds.addAll(newPlIds);
                } catch (Exception ex) {
                    failedCount++;
                    String msg = String.format("[%s] %s", e.getKey(), ex.getMessage());
                    log.error("[WaveScheduler] 웨이브 생성 실패 {}", msg, ex);
                    if (failures.length() > 0) failures.append(" | ");
                    failures.append(msg);
                }
            }

            // detail 저장 — OB × SO 한 행씩 (방금 만든 PL 만 매핑 대상)
            if (!processedObs.isEmpty()) {
                saveDetails(history.getId(), processedObs, createdPickingListIds,
                        warehouseNameCache, storeNameCache);
            }

            int targetCount = filtered.size();
            if (failedCount == 0) {
                history.markSuccess(targetCount, successCount);
            } else {
                history.markPartial(targetCount, successCount, failedCount, failures.toString());
            }
            log.info("[WaveScheduler] 완료 target={}, wave={}, failed={}, excluded={}",
                    targetCount, successCount, failedCount, excluded);

        } catch (Exception ex) {
            log.error("[WaveScheduler] 전체 실패", ex);
            history.markFailed(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }

        return schedulerHistoryRepository.save(history);
    }

    /**
     * 처리된 OB 들의 상세 (창고/출고처/SO) 를 모아 SchedulerHistoryDetail 에 일괄 저장.
     *
     * 한 OB 가 여러 SO 와 연결된 경우 (분할 출고) SO 수만큼 행이 들어감.
     * SO 가 없는 OB (manual 등) 은 SO 정보 null 로 한 행만 저장.
     */
    private void saveDetails(UUID historyId, List<OutboundOrders> obs,
                              Set<UUID> createdPickingListIds,
                              Map<UUID, String> warehouseNameCache,
                              Map<UUID, String> storeNameCache) {
        // OB ID 일괄 → 활성 SO 링크 batch fetch
        Set<UUID> obIds = new HashSet<>();
        for (OutboundOrders ob : obs) obIds.add(ob.getId());
        List<OutboundSalesOrderLinks> links = obIds.isEmpty()
                ? List.of()
                : outboundSalesOrderLinksRepository.findByOutboundOrderIdInAndCancelledAtIsNull(obIds);

        // OB → 연결 SO ID 들
        Map<UUID, List<UUID>> soIdsByOb = new HashMap<>();
        Set<UUID> allSoIds = new HashSet<>();
        for (OutboundSalesOrderLinks l : links) {
            soIdsByOb.computeIfAbsent(l.getOutboundOrderId(), k -> new ArrayList<>())
                    .add(l.getSalesOrderId());
            allSoIds.add(l.getSalesOrderId());
        }

        // SO 번호 일괄 조회
        Map<UUID, String> soNoById = new HashMap<>();
        if (!allSoIds.isEmpty()) {
            for (ErpSalesOrders so : erpSalesOrderRepository.findAllById(allSoIds)) {
                soNoById.put(so.getId(), so.getSalesOrderNumber());
            }
        }

        // OB → pickingListId 매핑 (방금 만든 PL 만 대상)
        // 다리 테이블에서 createdPickingListIds 에 속한 매핑만 골라 OB 와 연결.
        Map<UUID, UUID> pickingListIdByOb = new HashMap<>();
        if (!createdPickingListIds.isEmpty()) {
            for (UUID plId : createdPickingListIds) {
                List<OutboundPickinglist> mappings = outboundPickinglistRepository.findByPickingListId(plId);
                for (OutboundPickinglist m : mappings) {
                    pickingListIdByOb.put(m.getOutboundOrderId(), plId);
                }
            }
        }

        // 피킹리스트 번호 일괄 조회
        Map<UUID, String> pickingNoById = new HashMap<>();
        if (!createdPickingListIds.isEmpty()) {
            for (PickingList pl : pickinglistRepository.findAllById(createdPickingListIds)) {
                pickingNoById.put(pl.getId(), pl.getPickingNo());
            }
        }

        List<SchedulerHistoryDetail> rows = new ArrayList<>();
        for (OutboundOrders ob : obs) {
            UUID clientId = ob.getClientId();
            String warehouseName = warehouseNameCache.computeIfAbsent(
                    ob.getWarehouseId(), wid -> resolveWarehouseName(wid, clientId));
            String storeName = ob.getStoreId() == null ? null : storeNameCache.computeIfAbsent(
                    ob.getStoreId(), sid -> resolveStoreName(sid, clientId));
            UUID pickingListId = pickingListIdByOb.get(ob.getId());
            String pickingNo = pickingListId != null ? pickingNoById.get(pickingListId) : null;

            List<UUID> soIds = soIdsByOb.get(ob.getId());
            if (soIds == null || soIds.isEmpty()) {
                // SO 없는 OB (manual 등) → SO 정보 null 로 한 행
                rows.add(SchedulerHistoryDetail.builder()
                        .historyId(historyId)
                        .warehouseId(ob.getWarehouseId())
                        .warehouseName(warehouseName)
                        .outboundOrderId(ob.getId())
                        .orderNo(ob.getOrderNo())
                        .storeId(ob.getStoreId())
                        .storeName(storeName)
                        .pickingListId(pickingListId)
                        .pickingNo(pickingNo)
                        .build());
            } else {
                // 같은 SO 가 여러 라인으로 묶일 수 있어 dedup
                for (UUID soId : new HashSet<>(soIds)) {
                    rows.add(SchedulerHistoryDetail.builder()
                            .historyId(historyId)
                            .warehouseId(ob.getWarehouseId())
                            .warehouseName(warehouseName)
                            .outboundOrderId(ob.getId())
                            .orderNo(ob.getOrderNo())
                            .storeId(ob.getStoreId())
                            .storeName(storeName)
                            .salesOrderId(soId)
                            .soNo(soNoById.get(soId))
                            .pickingListId(pickingListId)
                            .pickingNo(pickingNo)
                            .build());
                }
            }
        }
        schedulerHistoryDetailRepository.saveAll(rows);
    }

    private String resolveWarehouseName(UUID warehouseId, UUID clientId) {
        try {
            WarehouseResDto w = masterServiceClient.getWarehouse(warehouseId, clientId.toString());
            return w != null ? w.getName() : null;
        } catch (Exception e) {
            log.warn("[WaveScheduler] warehouse 조회 실패 id={} - {}", warehouseId, e.getMessage());
            return null;
        }
    }

    private String resolveStoreName(UUID storeId, UUID clientId) {
        try {
            StoreResDto s = masterServiceClient.getStore(storeId, clientId.toString());
            return s != null ? s.getName() : null;
        } catch (Exception e) {
            log.warn("[WaveScheduler] store 조회 실패 id={} - {}", storeId, e.getMessage());
            return null;
        }
    }
}
