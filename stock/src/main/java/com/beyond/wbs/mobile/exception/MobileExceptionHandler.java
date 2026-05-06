package com.beyond.wbs.mobile.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@Slf4j
@RestControllerAdvice(basePackages = "com.beyond.wbs.mobile")
public class MobileExceptionHandler {

    // 못 찾음 (404)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        log.warn("[Mobile] NOT_FOUND: {}", e.getMessage());
        return ResponseEntity.status(404).body(Map.of(
                "code", "NOT_FOUND",
                "message", e.getMessage()
        ));
    }

    // 상태 오류 — 승인 안 된 지시서, 미처리 품목 존재 등 (400)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException e) {
        log.warn("[Mobile] INVALID_STATUS: {}", e.getMessage());
        return ResponseEntity.status(400).body(Map.of(
                "code", "INVALID_STATUS",
                "message", e.getMessage()
        ));
    }

    // 잘못된 요청 — 수량 초과, 권한 불일치 등 (400)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[Mobile] BAD_REQUEST: {}", e.getMessage());
        return ResponseEntity.status(400).body(Map.of(
                "code", "BAD_REQUEST",
                "message", e.getMessage()
        ));
    }

    // @Valid 검증 실패 — ReceiveReqDto, PickItemReqDto 등 (400)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("입력값이 올바르지 않습니다.");
        log.warn("[Mobile] VALIDATION_ERROR: {}", message);
        return ResponseEntity.status(400).body(Map.of(
                "code", "VALIDATION_ERROR",
                "message", message
        ));
    }

    // 그 외 예상 못한 에러 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
        log.error("[Mobile] INTERNAL_ERROR: {}", e.getMessage(), e);
        return ResponseEntity.status(500).body(Map.of(
                "code", "INTERNAL_ERROR",
                "message", "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
        ));
    }
}
