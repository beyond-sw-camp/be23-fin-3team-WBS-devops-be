package com.beyond.wbs.warehouse.controller;

import com.beyond.wbs.warehouse.domain.WarehouseType;
import com.beyond.wbs.warehouse.dtos.WarehouseCreateDto;
import com.beyond.wbs.warehouse.dtos.WarehouseDetailResDto;
import com.beyond.wbs.warehouse.dtos.WarehouseListResDto;
import com.beyond.wbs.warehouse.dtos.WarehouseUpdateDto;
import com.beyond.wbs.warehouse.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/warehouse")
public class WarehouseController {

    private final WarehouseService warehouseService;
    @Autowired
    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid WarehouseCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = warehouseService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(required = false) WarehouseType warehouseType) {
        Page<WarehouseListResDto> list = warehouseService.findAll(pageable, clientId, warehouseType);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id,
                                      @RequestHeader("X-Client-Id") UUID clientId) {
        WarehouseDetailResDto dto = warehouseService.findDetail(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    // 창고 상세 정보 수정 (managerName / phone / notes)
    @AuditLog
    @PatchMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody @Valid WarehouseUpdateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        WarehouseDetailResDto res = warehouseService.updateDetail(id, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @AuditLog(action = "비활성화")
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        warehouseService.deactivate(id,clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    // 창고 재활성화 (deactivate 의 짝)
    @AuditLog(action = "활성화")
    @PatchMapping("/activate/{id}")
    public ResponseEntity<?> activate(@PathVariable UUID id,
                                      @RequestHeader("X-Client-Id") UUID clientId) {
        warehouseService.activate(id, clientId);
        return ResponseEntity.ok().build();
    }
}