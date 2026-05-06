package com.beyond.wbs.exception;

import com.beyond.wbs.dtos.CommonErrorDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

// servlet 환경(MVC)에서만 등록 — WebFlux 모듈(ai-service 등)에서는 빈 생성 스킵.
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
@RestControllerAdvice
public class CommonExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> illegal(IllegalArgumentException e){ // 에러 주입됨
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(400)
                .error_message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    // 비즈니스 상태 위반 — 재고 없음, 승인 안 된 지시서 등 (400)
    // Why: IllegalStateException 이 500 으로 떨어지면 사용자에게 원인이 전달되지 않음.
    // 재고 없이 웨이브 생성 · 미승인 상태에서 완료 처리 같은 케이스는 비즈니스 예외이므로 400.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<?> illegalState(IllegalStateException e){
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(400)
                .error_message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    //    Valid 어노테이션
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> notValid(MethodArgumentNotValidException e){ // 에러 주입됨
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(400)
                .error_message(e.getFieldError().getDefaultMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> noSuchElement(NoSuchElementException e){ // 에러 주입됨
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(404)
                .error_message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(dto);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> entityNotFound(EntityNotFoundException e){ // 에러 주입됨
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(404)
                .error_message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(dto);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<?> securityException(SecurityException e){
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(403)
                .error_message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(dto);
    }

    // 필수 @RequestParam 누락 — 기본 Spring 동작은 400. catch-all 이 삼키지 않도록 명시.
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<?> missingParam(MissingServletRequestParameterException e){
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(400)
                .error_message("필수 파라미터 누락: " + e.getParameterName())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    // 파라미터 타입 불일치 (ex. UUID 자리에 빈 문자열 / 잘못된 형식) — 400.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> typeMismatch(MethodArgumentTypeMismatchException e){
        e.printStackTrace();
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(400)
                .error_message("파라미터 형식 오류: " + e.getName())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(dto);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> exception(Exception e){
        e.printStackTrace();
        if ("org.springframework.web.servlet.resource.NoResourceFoundException".equals(e.getClass().getName())) {
            CommonErrorDto dto = CommonErrorDto.builder()
                    .status_code(404)
                    .error_message("요청 경로를 찾을 수 없습니다: " + resolveResourcePath(e))
                    .build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(dto);
        }
        CommonErrorDto dto = CommonErrorDto.builder()
                .status_code(500)
                .error_message(e.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(dto);
    }

    private String resolveResourcePath(Exception e) {
        try {
            Object resourcePath = e.getClass().getMethod("getResourcePath").invoke(e);
            return String.valueOf(resourcePath);
        } catch (ReflectiveOperationException ignored) {
            return e.getMessage();
        }
    }

}
