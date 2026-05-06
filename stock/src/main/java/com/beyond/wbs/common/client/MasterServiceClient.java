package com.beyond.wbs.common.client;

import com.beyond.wbs.common.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

/**
 * Master 모듈 Feign Client
 *
 * stock 모듈에서 master 모듈의 API를 호출할 때 사용.
 * @FeignClient(name = "master-service") → Eureka에서 master-service 주소를 자동 조회.
 */
@FeignClient(name = "master-service")
public interface MasterServiceClient {

    // ============ 창고 ============
    // master 쪽 /warehouse/detail 은 멀티테넌시 검증을 위해 X-Client-Id 헤더를 요구함
    @GetMapping("/warehouse/detail/{id}")
    WarehouseResDto getWarehouse(@PathVariable("id") UUID id,
                                 @RequestHeader("X-Client-Id") String clientId);

    // 클라이언트 범위 내 창고 전체 목록 (전 창고 재고 조회 등 교차 조회용)
    @GetMapping("/warehouse/list")
    WarehousePageResDto getWarehouses(@RequestParam("size") int size,
                                       @RequestHeader("X-Client-Id") UUID clientId);

    // ============ 구역 ============
    // 카테고리별 구역 조회 (적치 위치 추천용)
    @GetMapping("/zone/by-category/{categoryId}")
    List<ZoneResDto> getZonesByCategoryId(@PathVariable("categoryId") UUID categoryId,
                                          @RequestHeader("X-Client-Id") String clientId);

    // 창고 + ZoneType 조회 (불량 임시 보관존 등)
    @GetMapping("/zone/by-warehouse/{warehouseId}")
    List<ZoneResDto> getZonesByWarehouseIdAndType(@PathVariable("warehouseId") UUID warehouseId,
                                                  @RequestParam("type") String type,
                                                  @RequestHeader("X-Client-Id") String clientId);

    // ============ 랙 ============
    @GetMapping("/rack/detail/{id}")
    RackResDto getRack(@PathVariable("id") UUID id,
                       @RequestHeader("X-Client-Id") String clientId);

    // 협력사별 랙 조회 (적치 위치 추천용)
    @GetMapping("/rack/by-supplier/{supplierId}")
    List<RackResDto> getRacksBySupplierId(@PathVariable("supplierId") UUID supplierId,
                                          @RequestHeader("X-Client-Id") String clientId);

    // 구역별 랙 조회
    @GetMapping("/rack/by-zone/{zoneId}")
    List<RackResDto> getRacksByZoneId(@PathVariable("zoneId") UUID zoneId,
                                      @RequestHeader("X-Client-Id") String clientId);

    // ============ 로케이션 ============
    @GetMapping("/location/detail/{id}")
    LocationResDto getLocation(@PathVariable("id") UUID id,
                               @RequestHeader("X-Client-Id") String clientId);

    // 랙별 로케이션 목록 조회
    @GetMapping("/location/list")
    LocationPageResDto getLocationsByRackId(@RequestParam("rackId") UUID rackId,
                                            @RequestHeader("X-Client-Id") String clientId);

    // 창고별 로케이션 벌크 조회 (Zone/Rack/Location 조인 결과 일괄 반환).
    // Stock 서비스의 층별 재고 요약 등 벌크 조회용.
    @GetMapping("/location/by-warehouse/{warehouseId}")
    WarehouseLocationsResDto getLocationsByWarehouseId(
            @PathVariable("warehouseId") UUID warehouseId,
            @RequestHeader("X-Client-Id") UUID clientId);

    // 배치 로케이션 조회 — N+1 제거용. 여러 locationId 를 한 번에 조회.
    @PostMapping("/location/batch")
    List<LocationResDto> getLocations(@RequestBody List<UUID> ids,
                                      @RequestHeader("X-Client-Id") String clientId);

    // ============ 상품 ============
    @GetMapping("/product/detail/{id}")
    ProductResDto getProduct(@PathVariable("id") UUID id,
                             @RequestHeader("X-Client-Id") String clientId);

    // 배치 상품 조회 — N+1 제거용. 여러 productId 를 한 번에 조회.
    // URL 길이 제한 회피 위해 POST + body 로 id 리스트 전달.
    @PostMapping("/product/batch")
    List<ProductResDto> getProducts(@RequestBody List<UUID> ids,
                                    @RequestHeader("X-Client-Id") String clientId);

    // client 단위 전체 상품 조회 — 재고 부족 알림 baseline 용.
    // inventory 에 row 가 없어 입고 이력이 없는 상품도 알림 대상에 포함시키기 위해 사용.
    @GetMapping("/product/all-by-client")
    List<ProductResDto> getAllProducts(@RequestHeader("X-Client-Id") String clientId);

    // ============ 안전재고 ((상품 × 창고) 설정) ============
    // (상품, 창고) 단건 조회 — 없으면 응답 body 가 null (= 안전재고 미설정 = 알림 대상 제외).
    // low-stock 알림 판정 등 stock 측 검증용.
    @GetMapping("/product-warehouse-setting/{productId}/{warehouseId}")
    ProductWarehouseSettingResDto getProductWarehouseSetting(
            @PathVariable("productId") UUID productId,
            @PathVariable("warehouseId") UUID warehouseId,
            @RequestHeader("X-Client-Id") UUID clientId);

    // 한 client 의 모든 안전재고 설정 — low-stock 알림 일괄 조회용.
    @GetMapping("/product-warehouse-setting/by-client")
    List<ProductWarehouseSettingResDto> getProductWarehouseSettingsByClient(
            @RequestHeader("X-Client-Id") UUID clientId);

    // ============ 출고처 (Store) ============
    @GetMapping("/store/detail/{id}")
    StoreResDto getStore(@PathVariable("id") UUID id,
                         @RequestHeader("X-Client-Id") String clientId);

    // ============ 협력사 (Supplier) ============
    @GetMapping("/supplier/detail/{id}")
    SupplierResDto getSupplier(@PathVariable("id") UUID id,
                               @RequestHeader("X-Client-Id") String clientId);
}
