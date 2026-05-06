package com.beyond.wbs.transfer.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.transfer.dto.TransferExecutionResDto;
import com.beyond.wbs.transfer.service.TransferExecutionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/transferExecution")
public class TransferExecutionController {

    private final TransferExecutionService executionService;

    public TransferExecutionController(TransferExecutionService executionService) {
        this.executionService = executionService;
    }

    // 실행 이력 조회 (감사/KPI/타임라인용)
    @CheckPermission(resource = Resource.TRANSFER, action = Action.READ)
    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<Page<TransferExecutionResDto>> search(
            @RequestParam(required = false) UUID transferOrderId,
            @RequestParam(required = false) UUID executedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @PageableDefault(size = 20, sort = "executedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TransferExecutionResDto> result = executionService.search(
                transferOrderId, executedBy, fromDate, toDate, pageable);
        return ResponseEntity.ok(result);
    }
}
