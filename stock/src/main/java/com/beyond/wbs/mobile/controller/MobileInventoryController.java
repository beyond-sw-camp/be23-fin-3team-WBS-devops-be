package com.beyond.wbs.mobile.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.WarehousePageResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.inventory.dtos.InventoryByRackResDto;
import com.beyond.wbs.inventory.service.InventoryService;
import com.beyond.wbs.mobile.dto.MobileInventorySearchResDto;
import com.beyond.wbs.mobile.dto.MobileProductLocationResDto;
import com.beyond.wbs.mobile.dto.MobileRackInventoryResDto;
import com.beyond.wbs.mobile.dto.MobileRackLocationResDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 모바일 앱 — 재고 조회 탭
 *  1) 랙 QR 스캔 → 그 랙의 로케이션·재고 조회 (client 범위 전 창고 대상)
 *  2) 상품명 검색 → client 범위 전 창고에서 해당 상품의 위치·수량 조회
 *
 * 두 엔드포인트 모두 client 소속 창고 전부를 순회해 기존 InventoryService.getInventoriesByRack
 * 결과를 누적한 뒤 모바일용으로 필터링/그룹핑한다.
 */
@RestController
@RequestMapping("/mobile/inventory")
public class MobileInventoryController {

    private final InventoryService inventoryService;
    private final MasterServiceClient masterServiceClient;

    public MobileInventoryController(InventoryService inventoryService,
                                     MasterServiceClient masterServiceClient) {
        this.inventoryService = inventoryService;
        this.masterServiceClient = masterServiceClient;
    }

    // ─────────────────────────────────────────────────────────────
    // 랙 QR 조회 — client 전 창고에서 rackId 매칭
    // ─────────────────────────────────────────────────────────────
    @CheckPermission(resource = Resource.INVENTORY, action = Action.READ)
    @GetMapping("/by-rack/{rackId}")
    public ResponseEntity<MobileRackInventoryResDto> getByRack(
            @PathVariable UUID rackId,
            @RequestHeader("X-Client-Id") String clientId) {

        UUID clientUUID = UUID.fromString(clientId);

        InventoryByRackResDto.RackGroup rack = loadAllRacks(clientUUID).stream()
                .filter(r -> rackId.equals(r.getRackId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("랙을 찾을 수 없습니다."));

        // 1 로케이션 = 1 SKU (DB UNIQUE 제약). 상품이 없으면 product 필드가 null.
        List<MobileRackLocationResDto> slots = rack.getLocations().stream()
                .map(loc -> MobileRackLocationResDto.builder()
                        .locationCode(loc.getLocationCode())
                        .productName(loc.getProductName())
                        .sku(loc.getProductSku())
                        .availableQty(loc.getAvailableQty() != null ? loc.getAvailableQty() : 0)
                        .defectQty(loc.getDefectQty() != null ? loc.getDefectQty() : 0)
                        .build())
                .collect(Collectors.toList());

        MobileRackInventoryResDto body = MobileRackInventoryResDto.builder()
                .rackCode(rack.getRackCode())
                .locations(slots)
                .build();

        return ResponseEntity.ok(body);
    }

    // ─────────────────────────────────────────────────────────────
    // 상품명 검색 — client 전 창고 재고에서 이름 매칭
    // ─────────────────────────────────────────────────────────────
    @CheckPermission(resource = Resource.INVENTORY, action = Action.READ)
    @GetMapping("/search")
    public ResponseEntity<List<MobileInventorySearchResDto>> search(
            @RequestParam String keyword,
            @RequestHeader("X-Client-Id") String clientId) {

        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("검색어를 입력해주세요.");
        }
        String needle = keyword.trim().toLowerCase();

        // productId 기준 그룹핑 — 등장 순서 보존
        Map<UUID, MobileInventorySearchResDto> byProduct = new LinkedHashMap<>();

        for (InventoryByRackResDto.RackGroup rack : loadAllRacks(UUID.fromString(clientId))) {
            for (InventoryByRackResDto.LocationInventory loc : rack.getLocations()) {
                if (loc.getProductId() == null) continue;
                if (loc.getProductName() == null) continue;
                int avail = loc.getAvailableQty() != null ? loc.getAvailableQty() : 0;
                int defect = loc.getDefectQty() != null ? loc.getDefectQty() : 0;
                if (avail + defect <= 0) continue;
                if (!loc.getProductName().toLowerCase().contains(needle)) continue;

                MobileInventorySearchResDto entry = byProduct.computeIfAbsent(loc.getProductId(), pid ->
                        MobileInventorySearchResDto.builder()
                                .productName(loc.getProductName())
                                .sku(loc.getProductSku())
                                .locations(new ArrayList<>())
                                .build());

                entry.getLocations().add(MobileProductLocationResDto.builder()
                        .rackCode(rack.getRackCode())
                        .locationCode(loc.getLocationCode())
                        .availableQty(avail)
                        .defectQty(defect)
                        .build());
            }
        }

        List<MobileInventorySearchResDto> result = new ArrayList<>(byProduct.values());
        // 관련성 정렬:
        //   1) 매칭 품질 (정확 → 접두사 → 부분일치)
        //   2) 상품명 길이 (짧을수록 키워드 비중이 커서 우선)
        //   3) 가나다·알파벳순
        result.sort(Comparator
                .comparingInt((MobileInventorySearchResDto p) -> rankMatch(p.getProductName(), needle))
                .thenComparingInt(p -> p.getProductName() != null ? p.getProductName().length() : Integer.MAX_VALUE)
                .thenComparing(MobileInventorySearchResDto::getProductName, Comparator.nullsLast(String::compareTo)));

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────
    // 내부 — client 소속 전 창고의 rack-group 전부 로드
    // ─────────────────────────────────────────────────────────────
    private List<InventoryByRackResDto.RackGroup> loadAllRacks(UUID clientId) {
        WarehousePageResDto page = masterServiceClient.getWarehouses(1000, clientId);
        if (page == null || page.getContent() == null || page.getContent().isEmpty()) {
            return Collections.emptyList();
        }
        List<InventoryByRackResDto.RackGroup> all = new ArrayList<>();
        for (WarehouseResDto wh : page.getContent()) {
            if (wh == null || wh.getId() == null) continue;
            if (Boolean.FALSE.equals(wh.getIsActive())) continue;
            InventoryByRackResDto part = inventoryService.getInventoriesByRack(wh.getId(), clientId);
            if (part != null && part.getRacks() != null) {
                all.addAll(part.getRacks());
            }
        }
        return all;
    }

    /**
     * 검색 매칭 품질 순위.(룰 선언)
     * 낮을수록 상위 노출.
     *   0 = 정확 일치 (exact)
     *   1 = 접두사 일치 (startsWith)
     *   2 = 부분 일치 (contains)
     *  99 = 이름 null (방어적, 실제로 위 필터에서 걸러짐)
     */
    private static int rankMatch(String name, String needle) {
        if (name == null) return 99;
        String n = name.toLowerCase();
        if (n.equals(needle)) return 0;
        if (n.startsWith(needle)) return 1;
        return 2;
    }
}
