package com.beyond.wbs.alert.service;

import com.beyond.wbs.alert.dto.SalesOrderShortageAlertDto;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.StoreResDto;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.outbounds.domain.ErpSalesOrderItems;
import com.beyond.wbs.outbounds.domain.ErpSalesOrders;
import com.beyond.wbs.outbounds.repository.ErpSalesOrderItemRepository;
import com.beyond.wbs.outbounds.repository.ErpSalesOrderRepository;
import com.beyond.wbs.websocket.WebSocketPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 수주서(SO) 기반 재고 부족 알림 서비스.
 *
 * 트리거: 재고/SO 변동 Kafka 이벤트 (InventoryEventConsumer 가 호출).
 * 정책: 정교 — 신규 부족 / 해소만 push (현재 부족 상태인 SO ID 들을 Redis Hash 에 보관해 diff).
 *   - Redis key: "so_shortage:{clientId}"
 *   - field    : SO ID
 *   - value    : 부족 스냅샷 JSON (해소 시 그대로 push 에 사용)
 *
 * 가용재고 정의 (ATP, 출고 승인 정책과 일관):
 *   product 의 모든 창고에서 sum(available + incoming + pending).
 *   같은 product 를 여러 SO 가 요구할 경우 SO.scheduledDate ASC (빠른 출고 SO 우선).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class SalesOrderShortageService {

    private static final String REDIS_KEY_PREFIX = "so_shortage:";

    private static final String TYPE_ADDED = "so_shortage_added";
    private static final String TYPE_RESOLVED = "so_shortage_resolved";

    private final ErpSalesOrderItemRepository erpSalesOrderItemRepository;
    private final ErpSalesOrderRepository erpSalesOrderRepository;
    private final InventoryRepository inventoryRepository;
    private final MasterServiceClient masterServiceClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketPublisher webSocketPublisher;
    private final ObjectMapper objectMapper;
    private final AlertAuditLogger alertAuditLogger;

    public SalesOrderShortageService(
            ErpSalesOrderItemRepository erpSalesOrderItemRepository,
            ErpSalesOrderRepository erpSalesOrderRepository,
            InventoryRepository inventoryRepository,
            MasterServiceClient masterServiceClient,
            @Qualifier("accountRedis") RedisTemplate<String, String> redisTemplate,
            WebSocketPublisher webSocketPublisher,
            ObjectMapper objectMapper,
            AlertAuditLogger alertAuditLogger) {
        this.erpSalesOrderItemRepository = erpSalesOrderItemRepository;
        this.erpSalesOrderRepository = erpSalesOrderRepository;
        this.inventoryRepository = inventoryRepository;
        this.masterServiceClient = masterServiceClient;
        this.redisTemplate = redisTemplate;
        this.webSocketPublisher = webSocketPublisher;
        this.objectMapper = objectMapper;
        this.alertAuditLogger = alertAuditLogger;
    }

    /**
     * SO 부족 상태 재계산 + 변동분 (added / resolved / updated) WebSocket push.
     *
     * 동작:
     *  - 신규 부족 (current 에만 있음)            → "so_shortage_added" push + Redis 저장
     *  - 부족 해소 (previous 에만 있음)           → "so_shortage_resolved" push + Redis 삭제
     *  - 양쪽에 있고 items 변경 (부분 해소 등)     → "so_shortage_added" 다시 push (FE store 가 upsert) + Redis 갱신
     *  - 양쪽에 있고 items 동일                   → push 안 함 (스팸 방지)
     *
     * 실패해도 throw 하지 않음 — 알림 실패가 진행 중인 비즈니스 트랜잭션을 막으면 안 됨.
     */
    public void refresh(UUID clientId) {
        if (clientId == null) return;
        try {
            Map<UUID, SalesOrderShortageAlertDto> current = computeCurrentShortages(clientId);
            Map<UUID, SalesOrderShortageAlertDto> previous = loadPrevious(clientId);

            Set<UUID> currentIds = current.keySet();
            Set<UUID> previousIds = previous.keySet();

            Set<UUID> added = new HashSet<>(currentIds);
            added.removeAll(previousIds);

            Set<UUID> resolved = new HashSet<>(previousIds);
            resolved.removeAll(currentIds);

            // 양쪽에 있는 SO 중 items 가 달라진 것만 추출 (부분 해소 / 새 부족 추가 등)
            Set<UUID> bothIds = new HashSet<>(currentIds);
            bothIds.retainAll(previousIds);
            Set<UUID> updated = new HashSet<>();
            for (UUID soId : bothIds) {
                if (!sameItems(current.get(soId), previous.get(soId))) {
                    updated.add(soId);
                }
            }

            String redisKey = REDIS_KEY_PREFIX + clientId;
            String destination = "/topic/admin/alerts/" + clientId;

            // 신규 부족 — "added" push + audit "출고불가발생"
            for (UUID soId : added) {
                SalesOrderShortageAlertDto alert = current.get(soId);
                alert.setType(TYPE_ADDED);
                webSocketPublisher.send(destination, alert);
                alertAuditLogger.log(AlertAuditLogger.ACTION_SO_SHORTAGE_ADDED, clientId, alert);
                try {
                    redisTemplate.opsForHash().put(redisKey, soId.toString(), objectMapper.writeValueAsString(alert));
                } catch (Exception e) {
                    log.warn("[SO 부족] Redis 저장 실패 soId={} - {}", soId, e.getMessage());
                }
            }

            // 기존 부족 SO 의 수량 변동(부분 해소) — WS 는 "added" 로 동일 (FE store 가 upsert),
            // audit 만 "출고불가부분해소" 로 분리해 알림 이력에서 신규/부분해소 구분 가능.
            for (UUID soId : updated) {
                SalesOrderShortageAlertDto alert = current.get(soId);
                alert.setType(TYPE_ADDED);
                webSocketPublisher.send(destination, alert);
                alertAuditLogger.log(AlertAuditLogger.ACTION_SO_SHORTAGE_UPDATED, clientId, alert);
                try {
                    redisTemplate.opsForHash().put(redisKey, soId.toString(), objectMapper.writeValueAsString(alert));
                } catch (Exception e) {
                    log.warn("[SO 부족] Redis 저장 실패 soId={} - {}", soId, e.getMessage());
                }
            }

            // 부족 해소 push + Redis 에서 삭제
            for (UUID soId : resolved) {
                SalesOrderShortageAlertDto alert = previous.get(soId);
                alert.setType(TYPE_RESOLVED);
                webSocketPublisher.send(destination, alert);
                alertAuditLogger.log(AlertAuditLogger.ACTION_SO_SHORTAGE_RESOLVED, clientId, alert);
                redisTemplate.opsForHash().delete(redisKey, soId.toString());
            }

            if (!added.isEmpty() || !resolved.isEmpty() || !updated.isEmpty()) {
                log.info("[SO 부족 갱신] clientId={} added={} updated={} resolved={}",
                        clientId, added.size(), updated.size(), resolved.size());
            }
        } catch (Exception e) {
            log.warn("[SO 부족] refresh 실패 clientId={} - {}", clientId, e.getMessage());
        }
    }

    /**
     * 두 알림의 items 가 동일한지 비교 — productId / shortageQty / availableQty / requiredQty 모두 일치해야 동일.
     * 순서 무관 (productId 로 정렬).
     */
    private boolean sameItems(SalesOrderShortageAlertDto a, SalesOrderShortageAlertDto b) {
        List<SalesOrderShortageAlertDto.ShortageItem> aItems =
                a.getItems() != null ? a.getItems() : List.of();
        List<SalesOrderShortageAlertDto.ShortageItem> bItems =
                b.getItems() != null ? b.getItems() : List.of();
        if (aItems.size() != bItems.size()) return false;

        Comparator<SalesOrderShortageAlertDto.ShortageItem> byProduct =
                Comparator.comparing(it -> it.getProductId() != null ? it.getProductId().toString() : "");
        List<SalesOrderShortageAlertDto.ShortageItem> aSorted = new ArrayList<>(aItems);
        List<SalesOrderShortageAlertDto.ShortageItem> bSorted = new ArrayList<>(bItems);
        aSorted.sort(byProduct);
        bSorted.sort(byProduct);

        for (int i = 0; i < aSorted.size(); i++) {
            SalesOrderShortageAlertDto.ShortageItem ai = aSorted.get(i);
            SalesOrderShortageAlertDto.ShortageItem bi = bSorted.get(i);
            if (!java.util.Objects.equals(ai.getProductId(), bi.getProductId())) return false;
            if (ai.getRequiredQty() != bi.getRequiredQty()) return false;
            if (ai.getAvailableQty() != bi.getAvailableQty()) return false;
            if (ai.getShortageQty() != bi.getShortageQty()) return false;
        }
        return true;
    }

    /**
     * 현재 부족 상태 계산.
     *
     * 알고리즘:
     *  1) allocatedQty<qty 인 SO 라인을 client 별로 조회 (출고예정일 ASC)
     *  2) productId 단위로 그룹핑
     *  3) 각 productId 마다:
     *     a) ATP = sum(available + incoming + pending) — 모든 창고 합산
     *     b) SO 라인을 출고예정일 ASC 정렬 → 앞 SO 부터 ATP 차감
     *     c) ATP 부족한 SO 라인을 부족 알림에 추가
     */
    private Map<UUID, SalesOrderShortageAlertDto> computeCurrentShortages(UUID clientId) {
        List<ErpSalesOrderItems> openItems = erpSalesOrderItemRepository.findOpenByClientId(clientId);
        if (openItems.isEmpty()) return new HashMap<>();

        // 부모 SO 일괄 조회 (storeName / scheduledDate 채움)
        Set<UUID> soIds = new HashSet<>();
        for (ErpSalesOrderItems it : openItems) soIds.add(it.getSalesOrderId());
        Map<UUID, ErpSalesOrders> soById = new HashMap<>();
        for (ErpSalesOrders so : erpSalesOrderRepository.findAllById(soIds)) {
            soById.put(so.getId(), so);
        }

        // productId → 라인 그룹
        Map<UUID, List<ErpSalesOrderItems>> byProduct = new HashMap<>();
        for (ErpSalesOrderItems it : openItems) {
            byProduct.computeIfAbsent(it.getProductId(), k -> new ArrayList<>()).add(it);
        }

        // product / store 이름 캐시 (loop 내부 N+1 방지)
        Map<UUID, ProductResDto> productCache = new HashMap<>();
        Map<UUID, String> storeNameCache = new HashMap<>();

        Map<UUID, SalesOrderShortageAlertDto> result = new HashMap<>();

        for (Map.Entry<UUID, List<ErpSalesOrderItems>> entry : byProduct.entrySet()) {
            UUID productId = entry.getKey();
            List<ErpSalesOrderItems> items = entry.getValue();

            // ATP 계산 (해당 client + product 의 모든 inventory row 합산)
            int atp = 0;
            for (Inventory inv : inventoryRepository.findByProductId(productId)) {
                if (!clientId.equals(inv.getClientId())) continue;
                atp += nullSafe(inv.getAvailableQty()) + nullSafe(inv.getIncomingQty()) + nullSafe(inv.getPendingQty());
            }

            // 출고예정일 ASC 정렬 (빠른 SO 우선 배분)
            items.sort(Comparator.comparing(it -> {
                ErpSalesOrders so = soById.get(it.getSalesOrderId());
                return so != null && so.getScheduledDate() != null ? so.getScheduledDate() : LocalDate.MAX;
            }));

            int remaining = atp;
            for (ErpSalesOrderItems item : items) {
                int required = item.getQty() - nullSafe(item.getAllocatedQty());
                if (required <= 0) continue;

                int availableForThis = Math.min(required, Math.max(0, remaining));
                int shortage = required - availableForThis;
                remaining -= availableForThis;

                if (shortage > 0) {
                    UUID soId = item.getSalesOrderId();
                    ErpSalesOrders so = soById.get(soId);
                    SalesOrderShortageAlertDto alert = result.computeIfAbsent(soId, sid -> {
                        String storeName = so != null ? resolveStoreName(so.getStoreId(), clientId, storeNameCache) : null;
                        return SalesOrderShortageAlertDto.builder()
                                .salesOrderId(soId)
                                .soNo(so != null ? so.getSalesOrderNumber() : null)
                                .scheduledDate(so != null ? so.getScheduledDate() : null)
                                .storeName(storeName)
                                .items(new ArrayList<>())
                                .build();
                    });
                    ProductResDto product = resolveProduct(productId, clientId, productCache);
                    alert.getItems().add(SalesOrderShortageAlertDto.ShortageItem.builder()
                            .productId(productId)
                            .productName(product != null ? product.getName() : null)
                            .sku(product != null ? product.getSku() : null)
                            .requiredQty(required)
                            .availableQty(availableForThis)
                            .shortageQty(shortage)
                            .build());
                }
            }
        }
        return result;
    }

    /**
     * 현재 부족 상태 SO 목록 조회 (Redis 스냅샷 기반).
     *
     * FE 가 페이지 진입 시 호출해 baseline 채우는 용도.
     * type 필드는 비워서 반환 — FE 는 "현재 부족 중" 으로 단순 표시.
     *
     * 호출 시점에 refresh() 를 먼저 실행해 Redis 스냅샷을 최신화한다.
     *  - 사유: 평소엔 InventoryEventConsumer 가 inventory 이벤트마다 refresh 를 트리거하지만,
     *    이벤트가 한 번도 안 일어난 시점(예: 서버 재시작 직후)에는 Redis 가 비어있어 빈 배열만 반환.
     *  - baseline 조회는 보통 로그인 직후 1회 호출이라 cost 부담 적음. 사용자가 진입한 순간
     *    "현재 부족 중인 SO" 를 정확히 보여주는 게 우선.
     */
    public List<SalesOrderShortageAlertDto> getCurrentShortages(UUID clientId) {
        if (clientId == null) return new ArrayList<>();
        // baseline 호출 시 항상 최신화 — 이벤트 누락된 케이스도 자동 복구
        refresh(clientId);
        return new ArrayList<>(loadPrevious(clientId).values());
    }

    private Map<UUID, SalesOrderShortageAlertDto> loadPrevious(UUID clientId) {
        Map<UUID, SalesOrderShortageAlertDto> result = new HashMap<>();
        String redisKey = REDIS_KEY_PREFIX + clientId;
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(redisKey);
        if (raw == null || raw.isEmpty()) return result;
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            try {
                UUID soId = UUID.fromString(e.getKey().toString());
                SalesOrderShortageAlertDto dto = objectMapper.readValue(e.getValue().toString(), SalesOrderShortageAlertDto.class);
                result.put(soId, dto);
            } catch (Exception ex) {
                log.warn("[SO 부족] Redis 스냅샷 파싱 실패 - {}", ex.getMessage());
            }
        }
        return result;
    }

    private ProductResDto resolveProduct(UUID productId, UUID clientId, Map<UUID, ProductResDto> cache) {
        if (productId == null) return null;
        if (cache.containsKey(productId)) return cache.get(productId);
        ProductResDto p = null;
        try {
            p = masterServiceClient.getProduct(productId, clientId.toString());
        } catch (Exception e) {
            log.warn("[SO 부족] product 조회 실패 productId={} - {}", productId, e.getMessage());
        }
        cache.put(productId, p);
        return p;
    }

    private String resolveStoreName(UUID storeId, UUID clientId, Map<UUID, String> cache) {
        if (storeId == null) return null;
        if (cache.containsKey(storeId)) return cache.get(storeId);
        String name = null;
        try {
            StoreResDto s = masterServiceClient.getStore(storeId, clientId.toString());
            if (s != null) name = s.getName();
        } catch (Exception e) {
            log.warn("[SO 부족] store 조회 실패 storeId={} - {}", storeId, e.getMessage());
        }
        cache.put(storeId, name);
        return name;
    }

    private static int nullSafe(Integer v) {
        return v == null ? 0 : v;
    }
}
