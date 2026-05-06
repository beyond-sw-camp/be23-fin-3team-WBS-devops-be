package com.beyond.wbs.inventory.service;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.*;
import com.beyond.wbs.inbounds.domain.ErpPurchaseOrderItems;
import com.beyond.wbs.inbounds.domain.ErpPurchaseOrders;
import com.beyond.wbs.inbounds.dto.RecommendWarehousesResDto;
import com.beyond.wbs.inbounds.dto.RecommendWarehousesResDto.PoItem;
import com.beyond.wbs.inbounds.dto.RecommendWarehousesResDto.PoRecommendation;
import com.beyond.wbs.inbounds.dto.RecommendWarehousesResDto.WarehouseCandidate;
import com.beyond.wbs.inbounds.repository.ErpPurchaseOrderItemRepository;
import com.beyond.wbs.inbounds.repository.ErpPurchaseOrderRepository;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 발주서 → 입고지시서 생성 시 "어느 창고로 들어가는 게 좋은가" 추천.
 *
 * 적치 위치 추천(PlacementSuggestionService) 과 정합:
 *  - 적치 단계는 (창고 + 상품) 기준으로 (협력사 랙 ∩ 카테고리 zone) 교집합 위치를 찾음.
 *  - 입고 단계는 그 한 단계 위 — 어느 창고에 들어가야 적치 단계가 매끄러운지 추천.
 *  - 따라서 같은 신호 (협력사 랙 보유, 카테고리 zone 보유) 를 사용해 점수 매김.
 *
 * 점수 (※ 초기값 — 운영 데이터 누적 후 가중치 튜닝 예정):
 *   supplierRackCount * 10  (협력사 전용 랙 — 적치 1순위 신호)
 * + categoryZoneCount  * 5  (카테고리 매칭 zone — 적치 2순위 신호)
 * + 1                       (활성 NORMAL 창고 가산점, 동점이어도 후보 노출)
 *
 * 가용 슬롯 (emptyLocations) 은 점수에 안 들어감 — 정보 표시용.
 * 사용자가 "추천이지만 빈자리 부족" 같은 상황을 직접 판단하도록 함.
 * (점수 ↔ 가용성 trade-off 자동화는 운영 데이터 보고 결정)
 *
 * 호출 최적화: PO 한 건 평가에서 supplier rack / category zone 셋은 각각 1번만 fetch 후
 *   모든 후보 창고 평가에 공통 재사용. 창고별로는 layout (zone/rack/location ID 셋 + 가용 슬롯) 만 캐시.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseRecommendationService {

    private final ErpPurchaseOrderRepository erpPoRepository;
    private final ErpPurchaseOrderItemRepository erpPoItemRepository;
    private final MasterServiceClient masterServiceClient;
    private final InventoryRepository inventoryRepository;

    // 가중치 — 초기값. 운영 데이터 누적 후 튜닝 예정.
    private static final int WEIGHT_SUPPLIER_RACK = 10;
    private static final int WEIGHT_CATEGORY_ZONE = 5;
    private static final int WEIGHT_BASELINE = 1;

    private static final String NORMAL_WAREHOUSE_TYPE = "NORMAL";
    /** master /warehouse/list 가 페이징 응답이라 size 가 필수.
     *  단일 클라이언트 창고 수가 이 값 미만이라고 가정 — 부족하면 master 페이징 처리 추가 필요. */
    private static final int WAREHOUSE_FETCH_LIMIT = Integer.MAX_VALUE;

    public RecommendWarehousesResDto recommendForPos(List<UUID> poIds, UUID clientId) {
        if (poIds == null || poIds.isEmpty()) {
            return RecommendWarehousesResDto.builder().recommendations(List.of()).build();
        }
        String clientIdStr = clientId.toString();

        // 1. 활성 NORMAL 창고 한 번만 로드 (모든 PO 공통)
        WarehousePageResDto whPage = masterServiceClient.getWarehouses(WAREHOUSE_FETCH_LIMIT, clientId);
        List<WarehouseResDto> warehouses = (whPage == null || whPage.getContent() == null)
                ? List.of()
                : whPage.getContent().stream()
                    .filter(w -> !Boolean.FALSE.equals(w.getIsActive()))
                    .filter(w -> NORMAL_WAREHOUSE_TYPE.equals(w.getWarehouseType()))
                    .toList();

        // 창고별 (zoneIds, rackIds) 캐시 — 여러 PO 가 같은 창고를 평가하므로 한 번만 조회
        Map<UUID, WarehouseLayout> layoutCache = new HashMap<>();
        for (WarehouseResDto wh : warehouses) {
            layoutCache.put(wh.getId(), loadLayout(wh.getId(), clientId));
        }

        List<PoRecommendation> recommendations = new ArrayList<>();
        for (UUID poId : poIds) {
            ErpPurchaseOrders po = erpPoRepository.findById(poId).orElse(null);
            if (po == null || !po.getClientId().equals(clientId)) {
                log.warn("[추천] PO 조회 실패 또는 권한 없음: poId={}", poId);
                continue;
            }
            recommendations.add(buildPoRecommendation(po, warehouses, layoutCache, clientIdStr));
        }
        return RecommendWarehousesResDto.builder().recommendations(recommendations).build();
    }

    private PoRecommendation buildPoRecommendation(
            ErpPurchaseOrders po,
            List<WarehouseResDto> warehouses,
            Map<UUID, WarehouseLayout> layoutCache,
            String clientIdStr) {

        UUID supplierId = po.getSupplierId();
        String supplierName = safeFetchSupplierName(supplierId, clientIdStr);

        // PO 품목 + 상품 정보 (배치 조회)
        List<ErpPurchaseOrderItems> itemEntities = erpPoItemRepository.findByPurchaseOrderId(po.getId());
        Map<UUID, ProductResDto> productMap = batchFetchProducts(itemEntities, clientIdStr);
        List<PoItem> items = itemEntities.stream().map(it -> {
            ProductResDto p = productMap.get(it.getProductId());
            return PoItem.builder()
                    .productId(it.getProductId())
                    .sku(p != null ? p.getSku() : null)
                    .productName(p != null ? p.getName() : null)
                    .qty(it.getQty())
                    .build();
        }).toList();

        // 협력사 전용 랙 — PO 한 건당 1번 호출. 각 후보 창고의 layout.rackIds 와 교집합으로 창고별 보유 수 산출.
        Set<UUID> supplierRackIds = supplierId == null
                ? Set.of()
                : masterServiceClient.getRacksBySupplierId(supplierId, clientIdStr).stream()
                    .map(RackResDto::getId)
                    .collect(Collectors.toSet());

        // PO 품목들의 카테고리 zone 셋 (마찬가지로 한 번만 조회 후 모든 창고 평가에 재사용)
        Set<UUID> categoryZoneIds = collectCategoryZoneIds(productMap.values(), clientIdStr);

        // 창고별 점수 계산
        List<WarehouseCandidate> candidates = new ArrayList<>();
        for (WarehouseResDto wh : warehouses) {
            WarehouseLayout layout = layoutCache.get(wh.getId());
            if (layout == null) layout = WarehouseLayout.empty();

            int supplierRackCount = (int) layout.rackIds.stream()
                    .filter(supplierRackIds::contains)
                    .count();
            int categoryZoneCount = (int) layout.zoneIds.stream()
                    .filter(categoryZoneIds::contains)
                    .count();

            int score = supplierRackCount * WEIGHT_SUPPLIER_RACK
                      + categoryZoneCount * WEIGHT_CATEGORY_ZONE
                      + WEIGHT_BASELINE;
            String reason = buildReason(supplierRackCount, categoryZoneCount);

            candidates.add(WarehouseCandidate.builder()
                    .warehouseId(wh.getId())
                    .warehouseCode(wh.getCode())
                    .warehouseName(wh.getName())
                    .supplierRackCount(supplierRackCount)
                    .categoryZoneCount(categoryZoneCount)
                    .totalLocations(layout.totalLocations())
                    .emptyLocations(layout.emptyLocations())
                    .fitScore(score)
                    .reason(reason)
                    .build());
        }

        // 정렬:
        //  1) 점수 내림차순
        //  2) 동점 → 빈 슬롯 많은 순 (emptyLocations DESC) — "동점이면 빈자리 많은 데로" 직관
        //  3) 최후 tiebreaker → 창고 코드 오름차순 (안정적 결정)
        candidates.sort(Comparator
                .comparingInt(WarehouseCandidate::getFitScore).reversed()
                .thenComparing(Comparator.comparingInt(WarehouseCandidate::getEmptyLocations).reversed())
                .thenComparing(WarehouseCandidate::getWarehouseCode,
                        Comparator.nullsLast(String::compareTo)));

        UUID recommendedWarehouseId = candidates.isEmpty() ? null : candidates.get(0).getWarehouseId();

        return PoRecommendation.builder()
                .purchaseOrderId(po.getId())
                .poNo(po.getPoNo())
                .supplierId(supplierId)
                .supplierName(supplierName)
                .scheduledDate(po.getScheduledDate())
                .items(items)
                .recommendedWarehouseId(recommendedWarehouseId)
                .candidates(candidates)
                .build();
    }

    private WarehouseLayout loadLayout(UUID warehouseId, UUID clientId) {
        try {
            WarehouseLocationsResDto locs = masterServiceClient.getLocationsByWarehouseId(warehouseId, clientId);
            Set<UUID> zoneIds = new HashSet<>();
            Set<UUID> rackIds = new HashSet<>();
            Set<UUID> locationIds = new HashSet<>();
            if (locs != null && locs.getItems() != null) {
                for (WarehouseLocationsResDto.LocationSummaryItem item : locs.getItems()) {
                    if (item.getZoneId() != null) zoneIds.add(item.getZoneId());
                    if (item.getRackId() != null) rackIds.add(item.getRackId());
                    if (item.getLocationId() != null) locationIds.add(item.getLocationId());
                }
            }

            // 비어있는 location 수 계산 — inventory 행이 있는 location 은 점유로 봄.
            // (current_qty=0 이어도 row 가 존재하면 SKU 가 한 번이라도 들어왔던 위치 → one-SKU-per-location 정책상 점유 취급)
            int emptyLocations = locationIds.size();
            if (!locationIds.isEmpty()) {
                List<Inventory> invs = inventoryRepository.findByWarehouseId(warehouseId);
                Set<UUID> occupiedLocs = invs.stream()
                        .map(Inventory::getLocationId)
                        .filter(Objects::nonNull)
                        .filter(locationIds::contains)
                        .collect(Collectors.toSet());
                emptyLocations = locationIds.size() - occupiedLocs.size();
            }

            return new WarehouseLayout(zoneIds, rackIds, locationIds.size(), emptyLocations);
        } catch (Exception e) {
            log.warn("[추천] 창고 레이아웃 조회 실패: warehouseId={}, err={}", warehouseId, e.getMessage());
            return WarehouseLayout.empty();
        }
    }

    private Map<UUID, ProductResDto> batchFetchProducts(List<ErpPurchaseOrderItems> items, String clientIdStr) {
        if (items == null || items.isEmpty()) return Map.of();
        List<UUID> productIds = items.stream()
                .map(ErpPurchaseOrderItems::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) return Map.of();
        try {
            List<ProductResDto> products = masterServiceClient.getProducts(productIds, clientIdStr);
            return products.stream().collect(Collectors.toMap(ProductResDto::getId, p -> p, (a, b) -> a));
        } catch (Exception e) {
            log.warn("[추천] 상품 배치 조회 실패: {}", e.getMessage());
            return Map.of(); 
        }
    }

    /** PO 품목들의 categoryId 를 모아 그 카테고리에 속한 zone ID 셋 반환. */
    private Set<UUID> collectCategoryZoneIds(Collection<ProductResDto> products, String clientIdStr) {
        if (products == null || products.isEmpty()) return Set.of();
        Set<UUID> categoryIds = products.stream()
                .map(ProductResDto::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) return Set.of();

        Set<UUID> zoneIds = new HashSet<>();
        for (UUID categoryId : categoryIds) {
            try {
                List<ZoneResDto> zones = masterServiceClient.getZonesByCategoryId(categoryId, clientIdStr);
                if (zones == null) continue;
                for (ZoneResDto z : zones) {
                    if (z.getId() != null) zoneIds.add(z.getId());
                }
            } catch (Exception e) {
                log.warn("[추천] 카테고리 zone 조회 실패: categoryId={}, err={}", categoryId, e.getMessage());
            }
        }
        return zoneIds;
    }

    private String safeFetchSupplierName(UUID supplierId, String clientIdStr) {
        if (supplierId == null) return null;
        try {
            SupplierResDto s = masterServiceClient.getSupplier(supplierId, clientIdStr);
            return s != null ? s.getName() : null;
        } catch (Exception e) {
            log.warn("[추천] 협력사 조회 실패: supplierId={}, err={}", supplierId, e.getMessage());
            return null;
        }
    }

    private static String buildReason(int supplierRackCount, int categoryZoneCount) {
        if (supplierRackCount > 0 && categoryZoneCount > 0) {
            return String.format("협력사 전용 랙 %d개 · 카테고리 매칭 zone %d개", supplierRackCount, categoryZoneCount);
        }
        if (supplierRackCount > 0) {
            return String.format("협력사 전용 랙 %d개 보유", supplierRackCount);
        }
        if (categoryZoneCount > 0) {
            return String.format("카테고리 매칭 zone %d개 (협력사 랙 없음 — 적치 단계 자동 배정)", categoryZoneCount);
        }
        return "전용 랙·zone 없음 — 입고 후 적치 단계에서 위치 수동 지정";
    }

    /** 창고 단위 zone/rack/location 캐시 + 가용 슬롯 수 */
    private record WarehouseLayout(Set<UUID> zoneIds, Set<UUID> rackIds,
                                   int totalLocations, int emptyLocations) {
        static WarehouseLayout empty() {
            return new WarehouseLayout(Set.of(), Set.of(), 0, 0);
        }
    }
}
