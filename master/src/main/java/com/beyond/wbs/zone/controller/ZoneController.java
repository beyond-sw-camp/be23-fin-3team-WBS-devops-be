package com.beyond.wbs.zone.controller;

import com.beyond.wbs.zone.domain.ZoneType;
import com.beyond.wbs.zone.dtos.ZoneCreateDto;
import com.beyond.wbs.zone.dtos.ZoneListResDto;
import com.beyond.wbs.zone.dtos.ZoneUpdateDto;
import com.beyond.wbs.zone.service.ZoneService;
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
@RequestMapping("/zone")
public class ZoneController {

    private final ZoneService zoneService;

    @Autowired
    public ZoneController(ZoneService zoneService) {
        this.zoneService = zoneService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid ZoneCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = zoneService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @RequestParam UUID warehouseId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ZoneListResDto> list = zoneService.findAll(warehouseId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    // 카테고리별 구역 조회 (적치 위치 추천용)
    @AuditLog
    @GetMapping("/by-category/{categoryId}")
    public ResponseEntity<?> findByCategoryId(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(zoneService.findByCategoryId(categoryId));
    }

    // 창고 + 구역 타입 조회 (불량 임시 보관존 등 적치 추천용)
    @AuditLog
    @GetMapping("/by-warehouse/{warehouseId}")
    public ResponseEntity<?> findByWarehouseAndType(
            @PathVariable UUID warehouseId,
            @RequestParam(required = false) ZoneType type,
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(zoneService.findByWarehouseAndType(warehouseId, type, clientId));
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        ZoneListResDto dto = zoneService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    @AuditLog
    @PatchMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody @Valid ZoneUpdateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        ZoneListResDto res = zoneService.update(id, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @AuditLog(action = "비활성화")
    @PatchMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        zoneService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    @AuditLog(action = "활성화")
    @PatchMapping("/activate/{id}")
    public ResponseEntity<?> activate(@PathVariable UUID id,
                                      @RequestHeader("X-Client-Id") UUID clientId) {
        zoneService.activate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }
}