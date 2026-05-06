package com.beyond.wbs.store.controller;

import com.beyond.wbs.store.dtos.*;
import com.beyond.wbs.store.service.StoreService;
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
@RequestMapping("/store")
public class StoreController {

    private final StoreService storeService;

    @Autowired
    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid StoreCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.status(HttpStatus.OK).body(storeService.create(dto, clientId));
    }

    @AuditLog
    @PostMapping("/address/create")
    public ResponseEntity<?> createAddress(@RequestBody @Valid StoreAddressCreateDto dto) {
        return ResponseEntity.status(HttpStatus.OK).body(storeService.createAddress(dto));
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @RequestHeader("X-Client-Id") UUID clientId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<StoreResDto> list = storeService.findAll(clientId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.OK).body(storeService.findById(id));
    }

    @AuditLog(action = "비활성화")
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        storeService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    /**
     * 출고처 자동 웨이브 생성 토글.
     * true → WaveScheduler 가 매일 07:00 이 출고처의 출고지시서를 자동 웨이브 처리.
     * false → 자동 처리 제외 (수동 웨이브 생성만 가능).
     */
    @AuditLog(action = "자동웨이브설정")
    @PatchMapping("/{id}/auto-wave")
    public ResponseEntity<StoreResDto> toggleAutoWave(@PathVariable UUID id,
                                                       @RequestBody @Valid StoreAutoWaveToggleReqDto dto,
                                                       @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(storeService.toggleAutoWave(id, dto.getAutoWaveEnabled(), clientId));
    }
}