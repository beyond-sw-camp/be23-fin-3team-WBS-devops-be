package com.beyond.wbs.product.controller;

import com.beyond.wbs.product.dtos.ProductCategoryCreateDto;
import com.beyond.wbs.product.dtos.ProductCategoryResDto;
import com.beyond.wbs.product.dtos.ProductCategoryUpdateDto;
import com.beyond.wbs.product.service.ProductCategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/product/category")
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @Autowired
    public ProductCategoryController(ProductCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid ProductCategoryCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = categoryService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findRoots(@RequestHeader("X-Client-Id") UUID clientId) {
        List<ProductCategoryResDto> list = categoryService.findRoots(clientId);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @GetMapping("/children/{parentId}")
    public ResponseEntity<?> findChildren(@PathVariable UUID parentId) {
        List<ProductCategoryResDto> list = categoryService.findChildren(parentId);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        ProductCategoryResDto dto = categoryService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    @AuditLog
    @PatchMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody @Valid ProductCategoryUpdateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        ProductCategoryResDto res = categoryService.update(id, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @AuditLog(action = "비활성화")
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        categoryService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    @AuditLog(action = "활성화")
    @PutMapping("/activate/{id}")
    public ResponseEntity<?> activate(@PathVariable UUID id,
                                      @RequestHeader("X-Client-Id") UUID clientId) {
        categoryService.activate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }
}