package com.beyond.wbs.supplier.controller;

import com.beyond.wbs.supplier.dtos.*;
import com.beyond.wbs.supplier.service.SupplierService;
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
@RequestMapping("/supplier")
public class SupplierController {

    private final SupplierService supplierService;

    @Autowired
    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid SupplierCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.status(HttpStatus.OK).body(supplierService.create(dto, clientId));
    }

    @AuditLog
    @PostMapping("/product/create")
    public ResponseEntity<?> createSupplierProduct(@RequestBody @Valid SupplierProductCreateDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(supplierService.createSupplierProduct(dto));
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @RequestHeader("X-Client-Id") UUID clientId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SupplierResDto> list = supplierService.findAll(clientId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(supplierService.findById(id));
    }

    @AuditLog
    @GetMapping("/product/list/{supplierId}")
    public ResponseEntity<?> findSupplierProducts(@PathVariable UUID supplierId) {
        List<SupplierProductResDto> list = supplierService.findSupplierProducts(supplierId);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @PutMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody @Valid SupplierUpdateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        supplierService.update(id, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    @AuditLog(action = "비활성화")
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        supplierService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    @AuditLog(action = "활성화")
    @PutMapping("/activate/{id}")
    public ResponseEntity<?> activate(@PathVariable UUID id,
                                      @RequestHeader("X-Client-Id") UUID clientId) {
        supplierService.activate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }
}
