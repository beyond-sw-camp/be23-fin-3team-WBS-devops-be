package com.beyond.wbs.layout.controller;

import com.beyond.wbs.layout.dtos.*;
import com.beyond.wbs.layout.service.LayoutService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/layout")
public class LayoutController {

    private final LayoutService layoutService;

    @Autowired
    public LayoutController(LayoutService layoutService) {
        this.layoutService = layoutService;
    }

    // 창고 캔버스 저장 — 단일 (PUT)
    @AuditLog
    @PutMapping("/warehouse/save")
    public ResponseEntity<?> saveWarehouseLayout(@RequestBody @Valid WarehouseLayoutReqDto dto,
                                                 @RequestHeader("X-Client-Id") UUID clientId) {
        WarehouseLayoutResDto res = layoutService.saveWarehouseLayout(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    // 구역 레이아웃 일괄 저장 (PUT) — items upsert + deletedZoneIds 삭제
    @AuditLog
    @PutMapping("/zone/save")
    public ResponseEntity<?> saveZoneLayouts(@RequestBody @Valid ZoneLayoutSaveDto dto,
                                             @RequestHeader("X-Client-Id") UUID clientId) {
        List<ZoneLayoutResDto> res = layoutService.saveZoneLayouts(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    // 랙 레이아웃 일괄 저장 (PUT) — items upsert + deletedRackIds 삭제
    @AuditLog
    @PutMapping("/rack/save")
    public ResponseEntity<?> saveRackLayouts(@RequestBody @Valid RackLayoutSaveDto dto,
                                             @RequestHeader("X-Client-Id") UUID clientId) {
        List<RackLayoutResDto> res = layoutService.saveRackLayouts(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    // ── Legacy 단건 저장 (점진 마이그레이션 동안 유지) ──
    @AuditLog
    @PostMapping("/warehouse/save")
    public ResponseEntity<?> saveWarehouseLayoutLegacy(@RequestBody @Valid WarehouseLayoutReqDto dto,
                                                       @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(layoutService.saveWarehouseLayout(dto, clientId));
    }

    @AuditLog
    @PostMapping("/zone/save")
    public ResponseEntity<?> saveZoneLayoutLegacy(@RequestBody @Valid ZoneLayoutReqDto dto,
                                                  @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(layoutService.saveZoneLayout(dto, clientId));
    }

    @AuditLog
    @PostMapping("/rack/save")
    public ResponseEntity<?> saveRackLayoutLegacy(@RequestBody @Valid RackLayoutReqDto dto,
                                                  @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(layoutService.saveRackLayout(dto, clientId));
    }

    // 창고 레이아웃 조회
    @AuditLog
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<?> findWarehouseLayout(@PathVariable UUID warehouseId) {
        WarehouseLayoutResDto res = layoutService.findWarehouseLayout(warehouseId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    // 창고 기준 존 레이아웃 전체 조회
    @AuditLog
    @GetMapping("/zone/{warehouseId}")
    public ResponseEntity<?> findZoneLayouts(@PathVariable UUID warehouseId) {
        List<ZoneLayoutResDto> res = layoutService.findZoneLayouts(warehouseId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    // 존 기준 랙 레이아웃 전체 조회
    @AuditLog
    @GetMapping("/rack/{zoneId}")
    public ResponseEntity<?> findRackLayouts(@PathVariable UUID zoneId) {
        List<RackLayoutResDto> res = layoutService.findRackLayouts(zoneId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    // 창고 기준 랙 레이아웃 전체 조회 (신규)
    @AuditLog
    @GetMapping("/rack/warehouse/{warehouseId}")
    public ResponseEntity<?> findRackLayoutsByWarehouse(@PathVariable UUID warehouseId) {
        List<RackLayoutResDto> res = layoutService.findRackLayoutsByWarehouse(warehouseId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }
}