package com.beyond.wbs.product.controller;

import com.beyond.wbs.audit.AuditLog;
import com.beyond.wbs.product.dtos.ProductOptionValueCreateDto;
import com.beyond.wbs.product.dtos.ProductOptionValueResDto;
import com.beyond.wbs.product.service.ProductOptionValueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 상품 옵션 값 컨트롤러 — 옵션 타입에 속하는 값 목록 관리.
 *
 * 등록은 ADMIN 만 가능. 조회는 모든 role 허용.
 */
@RestController
@RequestMapping("/product/option-value")
public class ProductOptionValueController {

    private final ProductOptionValueService service;

    public ProductOptionValueController(ProductOptionValueService service) {
        this.service = service;
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<List<ProductOptionValueResDto>> list(
            @RequestParam UUID optionTypeId) {
        return ResponseEntity.ok(service.findByOptionTypeId(optionTypeId));
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody @Valid ProductOptionValueCreateDto dto) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UUID id = service.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }
}
