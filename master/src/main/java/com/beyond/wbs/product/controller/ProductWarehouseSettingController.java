package com.beyond.wbs.product.controller;

import com.beyond.wbs.audit.AuditLog;
import com.beyond.wbs.product.dtos.ProductWarehouseSettingResDto;
import com.beyond.wbs.product.dtos.ProductWarehouseSettingUpsertReqDto;
import com.beyond.wbs.product.service.ProductWarehouseSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * (상품 × 창고) 안전재고 설정 컨트롤러.
 *
 * 엔드포인트:
 *  GET    /product-warehouse-setting/{productId}/{warehouseId}     단건 조회 (Feign 용)
 *  GET    /product-warehouse-setting/by-product/{productId}        한 상품의 창고별 설정 일람
 *  PUT    /product-warehouse-setting/{productId}/{warehouseId}     upsert (있으면 수정 / 없으면 생성)
 *  DELETE /product-warehouse-setting/{productId}/{warehouseId}     삭제 (= 미설정 상태로 되돌림)
 */
@RestController
@RequestMapping("/product-warehouse-setting")
@RequiredArgsConstructor
public class ProductWarehouseSettingController {

    private final ProductWarehouseSettingService service;

    /**
     * 단건 조회 — stock 의 low-stock 알림 판정 시 Feign 으로 호출.
     * 설정이 없으면 응답 body 가 null (= 안전재고 미설정 = 알림 대상 제외).
     */
    @AuditLog
    @GetMapping("/{productId}/{warehouseId}")
    public ResponseEntity<ProductWarehouseSettingResDto> findOne(
            @PathVariable UUID productId,
            @PathVariable UUID warehouseId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(service.findOne(productId, warehouseId, clientId));
    }

    /**
     * 한 상품의 창고별 안전재고 일람 — 상품 상세 화면에서 안전재고 표 표시용.
     * 설정한 창고만 반환 (미설정 창고는 응답에 없음).
     */
    @AuditLog
    @GetMapping("/by-product/{productId}")
    public ResponseEntity<List<ProductWarehouseSettingResDto>> findByProduct(
            @PathVariable UUID productId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(service.findByProduct(productId, clientId));
    }

    /**
     * 한 창고의 상품별 안전재고 일람 — 창고 운영 화면용.
     * 설정한 상품만 반환 (미설정 상품은 응답에 없음).
     */
    @AuditLog
    @GetMapping("/by-warehouse/{warehouseId}")
    public ResponseEntity<List<ProductWarehouseSettingResDto>> findByWarehouse(
            @PathVariable UUID warehouseId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(service.findByWarehouse(warehouseId, clientId));
    }

    /**
     * 한 client 의 모든 안전재고 설정 — low-stock 알림 일괄 조회용 (Feign).
     * stock 모듈의 AlertService.getLowStockAlerts 가 호출.
     */
    @AuditLog
    @GetMapping("/by-client")
    public ResponseEntity<List<ProductWarehouseSettingResDto>> findByClient(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(service.findByClient(clientId));
    }

    /**
     * 안전재고 upsert — (상품, 창고) 조합에 대해 있으면 값 변경, 없으면 새로 생성.
     * 안전재고 신규 입력 / 변경 모두 이 한 endpoint 로 처리.
     */
    @AuditLog
    @PutMapping("/{productId}/{warehouseId}")
    public ResponseEntity<ProductWarehouseSettingResDto> upsert(
            @PathVariable UUID productId,
            @PathVariable UUID warehouseId,
            @RequestBody @Valid ProductWarehouseSettingUpsertReqDto dto,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(service.upsert(productId, warehouseId, dto, clientId));
    }

    /**
     * 안전재고 삭제 — (상품, 창고) 의 안전재고 설정을 지움.
     * 삭제 후엔 미설정 상태가 되어 low-stock 알림 대상에서 제외됨.
     */
    @AuditLog
    @DeleteMapping("/{productId}/{warehouseId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID productId,
            @PathVariable UUID warehouseId,
            @RequestHeader("X-Client-Id") UUID clientId) {
        service.delete(productId, warehouseId, clientId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
