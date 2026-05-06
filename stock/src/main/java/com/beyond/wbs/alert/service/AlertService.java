package com.beyond.wbs.alert.service;

import com.beyond.wbs.alert.dto.LowStockAlertDto;
import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.ProductResDto;
import com.beyond.wbs.common.client.dto.ProductWarehouseSettingResDto;
import com.beyond.wbs.common.client.dto.WarehouseResDto;
import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.websocket.WebSocketPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 알림 서비스
 *
 * (상품 × 창고) 별 안전재고를 master 의 product_warehouse_settings 에서 조회한다.
 * 설정이 없는 (상품, 창고) 조합은 안전재고 미설정으로 간주 → 알림 대상 제외.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class AlertService {

    private static final String REDIS_KEY_PREFIX = "low_stock:";
    private static final String TYPE_ADDED = "low_stock_added";
    private static final String TYPE_RESOLVED = "low_stock_resolved";

    private final InventoryRepository inventoryRepository;
    private final MasterServiceClient masterServiceClient;
    private final WebSocketPublisher webSocketPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AlertAuditLogger alertAuditLogger;

    public AlertService(InventoryRepository inventoryRepository,
                         MasterServiceClient masterServiceClient,
                         WebSocketPublisher webSocketPublisher,
                         @Qualifier("accountRedis") RedisTemplate<String, String> redisTemplate,
                         ObjectMapper objectMapper,
                         AlertAuditLogger alertAuditLogger) {
        this.inventoryRepository = inventoryRepository;
        this.masterServiceClient = masterServiceClient;
        this.webSocketPublisher = webSocketPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.alertAuditLogger = alertAuditLogger;
    }

    /**
     * 재고 부족 알림 목록을 조회한다.
     *
     * 새 모델 (창고별 안전재고):
     *  - master 에서 해당 client 의 모든 (상품 × 창고) 안전재고 설정 일괄 조회
     *  - 각 설정에 대해 inventory 의 가용재고 합산해서 부족 여부 판정
     *  - 설정이 없는 (상품, 창고) 는 알림 대상 X (= 미설정)
     */
    public List<LowStockAlertDto> getLowStockAlerts(UUID clientId) {
        // 1) master 에서 안전재고 설정 일괄 조회
        List<ProductWarehouseSettingResDto> settings;
        try {
            settings = masterServiceClient.getProductWarehouseSettingsByClient(clientId);
        } catch (Exception e) {
            log.warn("[Alert] 안전재고 설정 일괄 조회 실패: {}", e.getMessage());
            return new ArrayList<>();
        }
        if (settings == null || settings.isEmpty()) return new ArrayList<>();

        // 2) inventory (productId|warehouseId) 단위 가용재고 합산
        Map<String, Integer> availableByKey = new HashMap<>();
        for (Inventory inv : inventoryRepository.findAll()) {
            if (!clientId.equals(inv.getClientId())) continue;
            if (inv.getLocationId() == null) continue;
            String key = inv.getProductId() + "|" + inv.getWarehouseId();
            availableByKey.merge(key, inv.getAvailableQty(), Integer::sum);
        }

        // 3) 각 설정마다 부족 여부 판정 — minStockQty 이하면 알림 대상
        List<LowStockAlertDto> result = new ArrayList<>();
        for (ProductWarehouseSettingResDto s : settings) {
            int minStock = s.getMinStockQty() != null ? s.getMinStockQty() : 0;
            if (minStock <= 0) continue; // 0 이하는 의미 없음 — 미설정과 동일 처리

            int availableQty = availableByKey.getOrDefault(
                    s.getProductId() + "|" + s.getWarehouseId(), 0);

            if (availableQty <= minStock) {
                result.add(LowStockAlertDto.builder()
                        .productId(s.getProductId())
                        .productName(s.getProductName())
                        .sku(s.getSku())
                        .warehouseId(s.getWarehouseId())
                        .warehouseName(s.getWarehouseName())
                        .availableQty(availableQty)
                        .minStockQty(minStock)
                        .build());
            }
        }

        // 가용재고 오름차순 (가장 부족한 것이 위로)
        result.sort(Comparator.comparingInt(LowStockAlertDto::getAvailableQty));

        return result;
    }

    /**
     * 재고 변동 직후 (예: 출고 승인 reserve) 호출 — 해당 (상품 × 창고) 의 부족 상태가
     * 직전과 달라졌을 때만 WebSocket push.
     *
     * 부족 판정 (가용재고 기준 — SO 부족 알림과 다름):
     *  - 안전재고 설정이 없으면 알림 대상 X (= 미설정)
     *  - 설정 있고 minStockQty > 0 이면, 가용재고 ≤ minStockQty 일 때 부족
     *
     * Diff 처리 (SO 부족 알림과 동일 패턴):
     *  - Redis Hash "low_stock:{clientId}" 에 (productId|warehouseId) 별 부족 스냅샷 보관
     *  - 신규 부족 발생 → "low_stock_added" push + Redis 저장
     *  - 부족 해소     → "low_stock_resolved" push + Redis 삭제
     *  - 변화 없음     → push 안 함 (스팸 차단)
     *
     * 실패 시 throw 안 함 — 알림 push 실패가 진행 중인 비즈니스 트랜잭션을 막으면 안 됨.
     */
    @Transactional(readOnly = true)
    public void notifyLowStockIfNeeded(UUID productId, UUID warehouseId, UUID clientId) {
        try {
            // 1) 안전재고 설정 단건 조회 — 없으면 미설정 → 알림 대상 X
            ProductWarehouseSettingResDto setting;
            try {
                setting = masterServiceClient.getProductWarehouseSetting(productId, warehouseId, clientId);
            } catch (Exception e) {
                log.warn("[Alert push] 안전재고 설정 조회 실패: {}", e.getMessage());
                return;
            }
            int minStock = (setting != null && setting.getMinStockQty() != null) ? setting.getMinStockQty() : 0;

            // 2) 가용재고 합산
            int availableQty = 0;
            for (Inventory inv : inventoryRepository.findAll()) {
                if (!clientId.equals(inv.getClientId())) continue;
                if (!productId.equals(inv.getProductId())) continue;
                if (!warehouseId.equals(inv.getWarehouseId())) continue;
                if (inv.getLocationId() == null) continue;
                availableQty += inv.getAvailableQty();
            }

            // 3) 부족 판정 — 설정 자체가 없거나 minStock<=0 이면 부족 아님
            boolean currentlyShortage = (minStock > 0) && (availableQty <= minStock);

            // 4) Redis 의 직전 상태 조회
            String redisKey = REDIS_KEY_PREFIX + clientId;
            String hashField = productId + "|" + warehouseId;
            String previousJson = (String) redisTemplate.opsForHash().get(redisKey, hashField);
            boolean wasShortage = previousJson != null;

            String destination = "/topic/admin/alerts/" + clientId;

            // 5) 신규 부족 → added push + Redis 저장
            if (currentlyShortage && !wasShortage) {
                String productName = setting.getProductName();
                String sku = setting.getSku();
                // 설정에 productName/sku 가 비어있을 가능성에 대비 fallback
                if (productName == null || sku == null) {
                    try {
                        ProductResDto p = masterServiceClient.getProduct(productId, clientId.toString());
                        if (p != null) {
                            if (productName == null) productName = p.getName();
                            if (sku == null) sku = p.getSku();
                        }
                    } catch (Exception ignored) {}
                }
                String warehouseName = setting.getWarehouseName();
                if (warehouseName == null) warehouseName = resolveWarehouseName(warehouseId, clientId);

                LowStockAlertDto alert = LowStockAlertDto.builder()
                        .type(TYPE_ADDED)
                        .productId(productId)
                        .productName(productName)
                        .sku(sku)
                        .warehouseId(warehouseId)
                        .warehouseName(warehouseName)
                        .availableQty(availableQty)
                        .minStockQty(minStock)
                        .build();
                webSocketPublisher.send(destination, alert);
                alertAuditLogger.log(AlertAuditLogger.ACTION_LOW_STOCK_ADDED, clientId, alert);
                try {
                    redisTemplate.opsForHash().put(redisKey, hashField, objectMapper.writeValueAsString(alert));
                } catch (Exception e) {
                    log.warn("[Alert push] Redis 저장 실패 key={} - {}", hashField, e.getMessage());
                }
                return;
            }

            // 6) 부족 해소 → resolved push + Redis 삭제 (직전 스냅샷 그대로 사용)
            if (!currentlyShortage && wasShortage) {
                LowStockAlertDto alert;
                try {
                    alert = objectMapper.readValue(previousJson, LowStockAlertDto.class);
                } catch (Exception e) {
                    log.warn("[Alert push] Redis 스냅샷 파싱 실패 key={} - {}", hashField, e.getMessage());
                    redisTemplate.opsForHash().delete(redisKey, hashField);
                    return;
                }
                alert.setType(TYPE_RESOLVED);
                webSocketPublisher.send(destination, alert);
                alertAuditLogger.log(AlertAuditLogger.ACTION_LOW_STOCK_RESOLVED, clientId, alert);
                redisTemplate.opsForHash().delete(redisKey, hashField);
                return;
            }

            // 7) 변화 없음 → push 안 함
        } catch (Exception e) {
            log.warn("[Alert push] 예상치 못한 실패: productId={}, warehouseId={} - {}",
                    productId, warehouseId, e.getMessage());
        }
    }

    private String resolveWarehouseName(UUID warehouseId, UUID clientId) {
        try {
            WarehouseResDto w = masterServiceClient.getWarehouse(warehouseId, clientId.toString());
            return w != null ? w.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
