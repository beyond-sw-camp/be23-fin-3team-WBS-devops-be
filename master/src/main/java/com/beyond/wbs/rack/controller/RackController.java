package com.beyond.wbs.rack.controller;

import com.beyond.wbs.rack.dtos.RackCreateDto;
import com.beyond.wbs.rack.dtos.RackListResDto;
import com.beyond.wbs.rack.dtos.RackUpdateDto;
import com.beyond.wbs.rack.service.RackService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/rack")
public class RackController {

    private final RackService rackService;
    @Autowired
    public RackController(RackService rackService) {
        this.rackService = rackService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid RackCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = rackService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    /**
     * 통합 목록 — warehouseId / zoneId 필터 지원.
     * 둘 다 비우면 전체.
     */
    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(@RequestParam(required = false) UUID warehouseId,
                                     @RequestParam(required = false) UUID zoneId) {
        List<RackListResDto> list = rackService.findByFilters(warehouseId, zoneId);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    // 협력사별 랙 조회 (적치 위치 추천용 — 기존 호환)
    @AuditLog
    @GetMapping("/by-supplier/{supplierId}")
    public ResponseEntity<?> findBySupplierId(@PathVariable UUID supplierId) {
        return ResponseEntity.ok(rackService.findBySupplierId(supplierId));
    }

    // 구역별 랙 조회 (기존 호환)
    @AuditLog
    @GetMapping("/by-zone/{zoneId}")
    public ResponseEntity<?> findByZoneId(@PathVariable UUID zoneId) {
        return ResponseEntity.ok(rackService.findByZoneId(zoneId));
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        RackListResDto dto = rackService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    @AuditLog
    @PatchMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody @Valid RackUpdateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        RackListResDto res = rackService.update(id, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @AuditLog(action = "비활성화")
    @PatchMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        rackService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    @AuditLog(action = "활성화")
    @PatchMapping("/activate/{id}")
    public ResponseEntity<?> activate(@PathVariable UUID id,
                                      @RequestHeader("X-Client-Id") UUID clientId) {
        rackService.activate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }
}
