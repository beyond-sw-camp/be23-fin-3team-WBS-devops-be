package com.beyond.wbs.account.controller;

import com.beyond.wbs.account.dtos.ClientCreateReqDto;
import com.beyond.wbs.account.dtos.ClientDetailResDto;
import com.beyond.wbs.account.dtos.ClientResDto;
import com.beyond.wbs.account.service.DeveloperService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

/**
 * 개발자(플랫폼 운영자) 전용 API
 * Gateway에서 DEVELOPER role 체크로 접근 제어
 */
@RestController
@RequestMapping("/developer")
public class DeveloperController {
    private final DeveloperService developerService;

    public DeveloperController(DeveloperService developerService) {
        this.developerService = developerService;
    }

    // 회사 생성 + admin 계정 동시 생성
    @AuditLog
    @PostMapping("/clients")
    public ResponseEntity<?> createCompany(@RequestBody @Valid ClientCreateReqDto dto) {
        UUID clientId = developerService.createCompanyWithAdmin(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(clientId);
    }

    // 회사 목록 조회 (활성 회사만)
    @AuditLog
    @GetMapping("/clients")
    public ResponseEntity<List<ClientResDto>> getClients() {
        List<ClientResDto> clients = developerService.getClients();
        return ResponseEntity.ok(clients);
    }

    // 회사 상세 조회 (소속 사용자 포함)
    @AuditLog
    @GetMapping("/clients/{clientId}")
    public ResponseEntity<ClientDetailResDto> getClientDetail(@PathVariable UUID clientId) {
        ClientDetailResDto detail = developerService.getClientDetail(clientId);
        return ResponseEntity.ok(detail);
    }

    // 회사 비활성화 (소속 사용자 전체 비활성화)
    @AuditLog(action = "비활성화")
    @DeleteMapping("/clients/{clientId}")
    public ResponseEntity<Void> deactivateClient(@PathVariable UUID clientId) {
        developerService.deactivateClient(clientId);
        return ResponseEntity.ok().build();
    }
}
