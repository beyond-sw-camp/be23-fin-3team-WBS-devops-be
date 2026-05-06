package com.beyond.wbs.product.controller;

import com.beyond.wbs.product.dtos.ProductCreateDto;
import com.beyond.wbs.product.dtos.ProductListResDto;
import com.beyond.wbs.product.dtos.ProductOptionResDto;
import com.beyond.wbs.product.dtos.ProductSearchCondition;
import com.beyond.wbs.product.dtos.ProductUpdateDto;
import com.beyond.wbs.product.service.ProductOptionService;
import com.beyond.wbs.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/product")
public class ProductController {

    private final ProductService productService;
    private final ProductOptionService productOptionService;

    @Autowired
    public ProductController(ProductService productService,
                             ProductOptionService productOptionService) {
        this.productService = productService;
        this.productOptionService = productOptionService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid ProductCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = productService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @RequestHeader("X-Client-Id") UUID clientId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductListResDto> list = productService.findAll(clientId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    /**
     * client 단위 전체 상품 조회 (페이징 없음).
     * 사용처: Stock 서비스의 재고 부족 알림 baseline 등 내부 Feign 호출 전용.
     *
     * 예) GET /product/all-by-client
     */
    @GetMapping("/all-by-client")
    public ResponseEntity<?> findAllByClient(
            @RequestHeader("X-Client-Id") UUID clientId) {
        List<ProductListResDto> list = productService.findAllByClient(clientId);
        return ResponseEntity.ok(list);
    }

    /**
     * 상품 키워드 검색 — 상품명 / SKU / 바코드 LIKE 매칭.
     * keyword 없으면 전체 조회.
     *
     * 예) GET /product/search?keyword=USB&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ProductListResDto> list = productService.search(clientId, keyword, pageable);
        return ResponseEntity.ok(list);
    }

    /**
     * 상품 멀티필터 검색 — Specification 동적 쿼리.
     * 모든 파라미터 옵셔널. 제공된 필드만 AND 결합.
     *
     * 예) GET /product/search-advanced?brand=GH&isActive=true&optionValueIds=uuid1,uuid2
     * 옵션 매칭은 OR (uuid1 또는 uuid2 중 하나라도 매칭).
     */
    @AuditLog
    @GetMapping("/search-advanced")
    public ResponseEntity<?> searchAdvanced(
            @RequestHeader("X-Client-Id") UUID clientId,
            ProductSearchCondition cond,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<ProductListResDto> list = productService.searchAdvanced(clientId, cond, pageable);
        return ResponseEntity.ok(list);
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        ProductListResDto dto = productService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /**
     * 상품에 매핑된 옵션 목록 조회 — 상세 화면의 옵션 칩 표시용.
     * 응답: List<ProductOptionResDto> (옵션 타입/값 정보 포함)
     */
    @AuditLog
    @GetMapping("/{id}/options")
    public ResponseEntity<List<ProductOptionResDto>> findOptions(@PathVariable UUID id) {
        return ResponseEntity.ok(productOptionService.findByProductId(id));
    }

    /**
     * 바코드 정확매칭 조회 — 스캐너 입력 등 단일 상품 즉시 식별용.
     * 통합검색(/search) 의 LIKE 매칭과 분리해 의미를 명확히 한다.
     *
     * 예) GET /product/by-barcode?barcode=8809001000101
     * 응답: 200 + ProductListResDto / 404 (없을 때)
     */
    @AuditLog
    @GetMapping("/by-barcode")
    public ResponseEntity<?> findByBarcode(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam String barcode) {
        return productService.findByBarcode(clientId, barcode)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * 배치 상품 조회 — 여러 UUID 를 body 로 받아 한 번에 반환.
     *
     * 내부 서비스(Feign) 호출에서 N+1 을 제거하기 위한 엔드포인트.
     * URL 길이 제한 회피를 위해 POST 사용. 조회 목적이지만 body 로 id 배열을 전달.
     *
     * 요청 예시:
     *   POST /product/batch
     *   Body: ["01935c00-...", "01935c00-..."]
     *
     * 응답: 입력과 순서 무관. 존재하지 않는 id 는 결과에서 제외 (부분 성공).
     */
    @AuditLog
    @PostMapping("/batch")
    public ResponseEntity<?> findByIds(@RequestBody List<UUID> ids) {
        List<ProductListResDto> list = productService.findByIds(ids);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    /**
     * 상품 수정 — 부분 업데이트. null 필드는 변경하지 않음.
     * sku 는 수정 불가.
     *
     * 예) PATCH /product/update/{id}
     */
    @AuditLog
    @PatchMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody @Valid ProductUpdateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        ProductListResDto result = productService.update(id, dto, clientId);
        return ResponseEntity.ok(result);
    }

    @AuditLog(action = "비활성화")
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        productService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    /**
     * SKU 제안값 조회 — 상품 등록 폼에서 상품그룹 선택 시 호출.
     *
     * 프론트 흐름: 사용자가 상품그룹 드롭다운 선택 → 이 API 호출 → SKU 입력칸에 미리 채움.
     * 응답 예시: "MON-001" (카테고리코드 + 3자리 순번)
     *
     * 관리자는 이 값을 그대로 쓰거나 직접 수정해서 저장 가능.
     */
    @AuditLog
    @GetMapping("/suggest-sku")
    public ResponseEntity<?> suggestSku(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam UUID productGroupId) {
        String sku = productService.suggestNextSku(clientId, productGroupId);
        return ResponseEntity.ok(sku);
    }
}