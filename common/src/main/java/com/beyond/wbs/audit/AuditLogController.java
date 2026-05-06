package com.beyond.wbs.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 감사 로그 조회 컨트롤러.
 * 각 모듈에서 자동으로 /audit-logs 엔드포인트가 활성화된다.
 *
 * 예) GET /audit-logs?action=생성&from=2026-04-01&to=2026-04-20&page=0&size=20
 */
@RestController
@RequestMapping("/audit-logs")
@RequiredArgsConstructor
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
        name = "jakarta.servlet.http.HttpServletRequest")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<?> findAll(
            @RequestHeader("X-Client-Id") String clientId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AuditLogResDto> result = auditLogRepository.findByFilters(
                UUID.fromString(clientId),
                action,
                from != null ? from.atStartOfDay() : null,
                to != null ? to.plusDays(1).atStartOfDay() : null,
                pageable
        ).map(AuditLogResDto::from);

        return ResponseEntity.ok(result);
    }
}
