package com.beyond.wbs.product.controller;

import com.beyond.wbs.audit.AuditLog;
import com.beyond.wbs.product.dtos.ProductOptionTypeCreateDto;
import com.beyond.wbs.product.dtos.ProductOptionTypeResDto;
import com.beyond.wbs.product.service.ProductOptionTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 상품 옵션 타입 컨트롤러 — 회사(client)별 옵션 마스터.
 *
 * 등록은 ADMIN 만 가능 (Gateway가 주입한 X-User-Role 로 검사).
 * 조회는 모든 role 허용.
 */
@RestController
@RequestMapping("/product/option-type")
public class ProductOptionTypeController {

    private final ProductOptionTypeService service;

    public ProductOptionTypeController(ProductOptionTypeService service) {
        this.service = service;
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<List<ProductOptionTypeResDto>> list(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(service.findAll(clientId));
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody @Valid ProductOptionTypeCreateDto dto) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        UUID id = service.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }
}
