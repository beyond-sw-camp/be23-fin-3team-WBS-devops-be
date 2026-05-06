package com.beyond.wbs.product.service;

import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.domain.ProductWarehouseSetting;
import com.beyond.wbs.product.dtos.ProductWarehouseSettingResDto;
import com.beyond.wbs.product.dtos.ProductWarehouseSettingUpsertReqDto;
import com.beyond.wbs.product.repository.ProductRepository;
import com.beyond.wbs.product.repository.ProductWarehouseSettingRepository;
import com.beyond.wbs.warehouse.domain.Warehouse;
import com.beyond.wbs.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * (상품 × 창고) 안전재고 설정 서비스.
 *
 * - 단건 조회: stock 의 low-stock 알림 판정용 (Feign).
 * - 상품별 목록: 관리자 화면에서 한 상품의 창고별 안전재고 일람.
 * - upsert: 안전재고 신규 설정 / 변경.
 * - delete: 안전재고 미설정 상태로 되돌림 (= 알림 대상 제외).
 *
 * 권한 검증: product / warehouse 가 모두 같은 client 소유여야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductWarehouseSettingService {

    private final ProductWarehouseSettingRepository repository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;

    /**
     * 단건 조회 — Feign (stock alert) 에서 호출.
     * 없으면 null (= 안전재고 미설정).
     */
    public ProductWarehouseSettingResDto findOne(UUID productId, UUID warehouseId, UUID clientId) {
        Optional<ProductWarehouseSetting> opt = repository.findByProductIdAndWarehouseId(productId, warehouseId);
        if (opt.isEmpty()) return null;

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));
        if (!product.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        return ProductWarehouseSettingResDto.fromEntity(opt.get());
    }

    /**
     * 한 상품의 창고별 안전재고 목록 — 설정된 창고만 반환.
     */
    public List<ProductWarehouseSettingResDto> findByProduct(UUID productId, UUID clientId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));
        if (!product.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        List<ProductWarehouseSetting> settings = repository.findByProductId(productId);
        if (settings.isEmpty()) return List.of();

        Set<UUID> warehouseIds = new HashSet<>();
        for (ProductWarehouseSetting s : settings) warehouseIds.add(s.getWarehouseId());
        Map<UUID, String> warehouseNameMap = new HashMap<>();
        for (Warehouse w : warehouseRepository.findAllById(warehouseIds)) {
            warehouseNameMap.put(w.getId(), w.getName());
        }

        return settings.stream()
                .map(s -> ProductWarehouseSettingResDto.fromEntity(
                        s, product.getName(), product.getSku(),
                        warehouseNameMap.get(s.getWarehouseId())))
                .collect(Collectors.toList());
    }

    /**
     * 한 창고의 상품별 안전재고 목록 — 창고 운영 화면용.
     * 설정한 상품만 반환 (미설정 상품은 응답에 없음).
     */
    public List<ProductWarehouseSettingResDto> findByWarehouse(UUID warehouseId, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        List<ProductWarehouseSetting> settings = repository.findByWarehouseId(warehouseId);
        if (settings.isEmpty()) return List.of();

        Set<UUID> productIds = new HashSet<>();
        for (ProductWarehouseSetting s : settings) productIds.add(s.getProductId());
        Map<UUID, Product> productMap = new HashMap<>();
        for (Product p : productRepository.findAllById(productIds)) {
            productMap.put(p.getId(), p);
        }

        return settings.stream()
                .map(s -> {
                    Product p = productMap.get(s.getProductId());
                    return ProductWarehouseSettingResDto.fromEntity(
                            s,
                            p != null ? p.getName() : null,
                            p != null ? p.getSku() : null,
                            warehouse.getName());
                })
                .collect(Collectors.toList());
    }

    /**
     * 한 client 의 모든 (상품 × 창고) 안전재고 설정 — low-stock 알림 일괄 조회용.
     * stock 모듈이 Feign 으로 호출.
     */
    public List<ProductWarehouseSettingResDto> findByClient(UUID clientId) {
        List<ProductWarehouseSetting> settings = repository.findAllByClientId(clientId);
        if (settings.isEmpty()) return List.of();

        Set<UUID> productIds = new HashSet<>();
        Set<UUID> warehouseIds = new HashSet<>();
        for (ProductWarehouseSetting s : settings) {
            productIds.add(s.getProductId());
            warehouseIds.add(s.getWarehouseId());
        }
        Map<UUID, Product> productMap = new HashMap<>();
        for (Product p : productRepository.findAllById(productIds)) {
            productMap.put(p.getId(), p);
        }
        Map<UUID, String> warehouseNameMap = new HashMap<>();
        for (Warehouse w : warehouseRepository.findAllById(warehouseIds)) {
            warehouseNameMap.put(w.getId(), w.getName());
        }

        return settings.stream()
                .map(s -> {
                    Product p = productMap.get(s.getProductId());
                    return ProductWarehouseSettingResDto.fromEntity(
                            s,
                            p != null ? p.getName() : null,
                            p != null ? p.getSku() : null,
                            warehouseNameMap.get(s.getWarehouseId()));
                })
                .collect(Collectors.toList());
    }

    /**
     * 안전재고 upsert — 있으면 수정, 없으면 생성.
     */
    @Transactional
    public ProductWarehouseSettingResDto upsert(UUID productId, UUID warehouseId,
                                                  ProductWarehouseSettingUpsertReqDto dto, UUID clientId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));
        if (!product.getClientId().equals(clientId)) {
            throw new SecurityException("상품 접근 권한이 없습니다.");
        }
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("창고 접근 권한이 없습니다.");
        }

        ProductWarehouseSetting setting = repository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseGet(() -> ProductWarehouseSetting.builder()
                        .productId(productId)
                        .warehouseId(warehouseId)
                        .minStockQty(0)
                        .build());

        setting.updateMinStockQty(dto.getMinStockQty());
        ProductWarehouseSetting saved = repository.save(setting);

        return ProductWarehouseSettingResDto.fromEntity(
                saved, product.getName(), product.getSku(), warehouse.getName());
    }

    /**
     * 안전재고 삭제 — 미설정 상태로 되돌림 (알림 대상 제외).
     */
    @Transactional
    public void delete(UUID productId, UUID warehouseId, UUID clientId) {
        ProductWarehouseSetting setting = repository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("안전재고 설정을 찾을 수 없습니다."));

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));
        if (!product.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        repository.delete(setting);
    }
}
