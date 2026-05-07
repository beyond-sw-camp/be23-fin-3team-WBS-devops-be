package com.beyond.wbs.etcinout.service;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.LocationPageResDto;
import com.beyond.wbs.common.client.dto.LocationResDto;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.RackResDto;
import com.beyond.wbs.common.client.dto.ZoneResDto;
import com.beyond.wbs.etcinout.domain.*;
import com.beyond.wbs.etcinout.dto.EtcInoutItemDto;
import com.beyond.wbs.etcinout.dto.EtcInoutItemResDto;
import com.beyond.wbs.etcinout.repository.EtcInoutOrderItemRepository;
import com.beyond.wbs.etcinout.repository.EtcInoutOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EtcInoutItemService {

    private final EtcInoutOrderRepository orderRepository;
    private final EtcInoutOrderItemRepository itemRepository;
    private final MasterServiceClient masterServiceClient;
    private final EtcInoutService etcInoutService;

    // ============================================================
    // Feign 조회 헬퍼
    // ============================================================

    private String fetchProductName(UUID productId, UUID clientId) {
        if (productId == null) return null;
        try {
            ProductResDto p = masterServiceClient.getProduct(productId, clientId.toString());
            return p != null ? p.getName() : null;
        } catch (Exception e) {
            log.warn("[Feign] product 조회 실패: {} - {}", productId, e.getMessage());
            return null;
        }
    }

    private LocationResDto fetchLocation(UUID locationId, UUID clientId) {
        if (locationId == null) return null;
        try {
            return masterServiceClient.getLocation(locationId, clientId.toString());
        } catch (Exception e) {
            log.warn("[Feign] location 조회 실패: {} - {}", locationId, e.getMessage());
            return null;
        }
    }

    // 기타입출고 품목 조회
    @Transactional(readOnly = true)
    public List<EtcInoutItemResDto> getItems(UUID clientId, UUID orderId) {

        EtcInoutOrder order = getOrderWithCompanyCheck(clientId, orderId);

        List<EtcInoutOrderItem> items = itemRepository.findByEtcOrderId(orderId);

        // 같은 상품/위치가 반복되므로 캐시 (N+1 방지)
        Map<UUID, String> productNameCache = new HashMap<>();
        Map<UUID, LocationResDto> locationCache = new HashMap<>();

        // 입고일 때만 창고의 DEFECT zone 첫 위치를 default 로 한 번 조회 → 모든 items 에 동일하게 박음
        LocationResDto defectLocation = order.getDirection() == Direction.in
                ? fetchDefaultDefectLocation(order.getWarehouseId(), clientId)
                : null;

        List<EtcInoutItemResDto> result = new ArrayList<>();
        for (EtcInoutOrderItem item : items) {
            String productName = productNameCache.computeIfAbsent(
                    item.getProductId(), id -> fetchProductName(id, clientId));
            LocationResDto location = item.getLocationId() == null ? null
                    : locationCache.computeIfAbsent(item.getLocationId(),
                            id -> fetchLocation(id, clientId));
            result.add(EtcInoutItemResDto.fromEntity(item, productName, location, defectLocation));
        }
        return result;
    }

    // 창고의 DEFECT zone → 첫 rack → 첫 location 한 번만 조회.
    // 모든 입고 items 에 default 로 박아 작업자가 불량 발견 시 안내용으로 표시.
    private LocationResDto fetchDefaultDefectLocation(UUID warehouseId, UUID clientId) {
        if (warehouseId == null || clientId == null) return null;
        try {
            String clientIdStr = clientId.toString();
            List<ZoneResDto> defectZones = masterServiceClient
                    .getZonesByWarehouseIdAndType(warehouseId, "DEFECT", clientIdStr);
            if (defectZones == null || defectZones.isEmpty()) return null;

            for (ZoneResDto zone : defectZones) {
                List<RackResDto> racks = masterServiceClient.getRacksByZoneId(zone.getId(), clientIdStr);
                if (racks == null || racks.isEmpty()) continue;
                for (RackResDto rack : racks) {
                    LocationPageResDto page = masterServiceClient
                            .getLocationsByRackId(rack.getId(), clientIdStr);
                    if (page != null && page.getContent() != null && !page.getContent().isEmpty()) {
                        return page.getContent().get(0);
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[Feign] 불량 default 위치 조회 실패: warehouseId={} - {}", warehouseId, e.getMessage());
            return null;
        }
    }

    // 품목 추가 (draft 상태만)
    public EtcInoutItemResDto addItem(UUID clientId, UUID orderId, EtcInoutItemDto dto) {

        EtcInoutOrder order = getOrderWithCompanyCheck(clientId, orderId);

        if (order.getStatus() != EtcInoutStatus.draft) {
            throw new IllegalStateException("임시저장(draft) 상태에서만 품목을 추가할 수 있습니다.");
        }

        EtcInoutOrderItem item = EtcInoutOrderItem.builder()
                .etcOrderId(orderId)
                .productId(dto.getProductId())
                .locationId(dto.getLocationId())
                .qty(dto.getQty())
                .lotNo(dto.getLotNo())
                .condition(dto.getCondition())
                .note(dto.getNote())
                .build();

        EtcInoutOrderItem savedItem = itemRepository.save(item);

        // 출고면 누적 가용재고 초과 여부 검증 (트랜잭션 롤백)
        etcInoutService.validateOutboundStock(order);

        String productName = fetchProductName(savedItem.getProductId(), clientId);
        LocationResDto location = fetchLocation(savedItem.getLocationId(), clientId);
        return EtcInoutItemResDto.fromEntity(savedItem, productName, location);
    }

    // 품목 수정 (draft 상태만)
    public EtcInoutItemResDto updateItem(UUID clientId, UUID orderId, UUID itemId, EtcInoutItemDto dto) {

        EtcInoutOrder order = getOrderWithCompanyCheck(clientId, orderId);

        if (order.getStatus() != EtcInoutStatus.draft) {
            throw new IllegalStateException("임시저장(draft) 상태에서만 품목을 수정할 수 있습니다.");
        }

        EtcInoutOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("해당 품목이 없습니다."));

        if (!item.getEtcOrderId().equals(orderId)) {
            throw new IllegalArgumentException("해당 기타입출고 기록의 품목이 아닙니다.");
        }

        item.update(
                dto.getProductId(),
                dto.getLocationId(),
                dto.getQty(),
                dto.getLotNo(),
                dto.getCondition(),
                dto.getDefectReason(),
                dto.getNote()
        );

        // 출고면 누적 가용재고 초과 여부 검증 (트랜잭션 롤백)
        etcInoutService.validateOutboundStock(order);

        String productName = fetchProductName(item.getProductId(), clientId);
        LocationResDto location = fetchLocation(item.getLocationId(), clientId);
        return EtcInoutItemResDto.fromEntity(item, productName, location);
    }

    // 품목 삭제 (draft 상태만)
    public void deleteItem(UUID clientId, UUID orderId, UUID itemId) {

        EtcInoutOrder order = getOrderWithCompanyCheck(clientId, orderId);

        if (order.getStatus() != EtcInoutStatus.draft) {
            throw new IllegalStateException("임시저장(draft) 상태에서만 품목을 삭제할 수 있습니다.");
        }

        EtcInoutOrderItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("해당 품목이 없습니다."));

        if (!item.getEtcOrderId().equals(orderId)) {
            throw new IllegalArgumentException("해당 기타입출고 기록의 품목이 아닙니다.");
        }

        itemRepository.delete(item);
    }

    // 공통: 기록 조회 + 회사 확인
    private EtcInoutOrder getOrderWithCompanyCheck(UUID clientId, UUID orderId) {
        EtcInoutOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("해당 기타입출고 기록이 없습니다."));

        if (!order.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        return order;
    }
}
