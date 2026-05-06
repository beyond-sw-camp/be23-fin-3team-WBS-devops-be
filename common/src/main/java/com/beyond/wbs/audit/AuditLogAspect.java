package com.beyond.wbs.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * 감사 로그 AOP Aspect.
 * @AuditLog 어노테이션이 붙은 메서드의 요청/응답을 자동 기록한다.
 *
 * - userName은 X-User-Name 헤더에서 추출 (Gateway에서 JWT 파싱 시 설정)
 * - 로그인 API는 인증 전이므로 X-User-Id/X-User-Name이 없음
 *   → 성공 시 action="로그인", 실패 시 action="로그인실패"로 기록
 */
@Slf4j
@Aspect
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
        name = "jakarta.servlet.http.HttpServletRequest")
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogEventPublisher auditLogEventPublisher;

    public AuditLogAspect(AuditLogRepository auditLogRepository, AuditLogEventPublisher auditLogEventPublisher) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogEventPublisher = auditLogEventPublisher;
    }

    private static final Map<String, String> HTTP_METHOD_ACTION = Map.of(
            "GET", "조회",
            "POST", "생성",
            "PUT", "수정",
            "PATCH", "수정",
            "DELETE", "삭제"
    );

    @Around("@annotation(auditLog)")
    public Object audit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return pjp.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        String httpMethod = request.getMethod();
        String requestUri = request.getRequestURI();
        String ipAddress = getClientIp(request);

        UUID clientId = parseUuid(request.getHeader("X-Client-Id"));
        UUID userId = parseUuid(request.getHeader("X-User-Id"));
        String userName = request.getHeader("X-User-Name");

        // 로그인 API 판별
        boolean isLoginRequest = requestUri.contains("/doLogin");

        // action 결정
        String action;
        if (isLoginRequest) {
            action = "로그인";  // 실패 시 catch 블록에서 "로그인실패"로 변경
        } else if (!auditLog.action().isEmpty()) {
            action = auditLog.action();
        } else {
            action = HTTP_METHOD_ACTION.getOrDefault(httpMethod, httpMethod);
        }

        String className = pjp.getTarget().getClass().getSimpleName();
        String entityName = className.replace("Controller", "").toLowerCase();

        Integer responseStatus = null;
        try {
            Object result = pjp.proceed();

            if (result instanceof ResponseEntity<?> re) {
                responseStatus = re.getStatusCode().value();
            }

            long duration = System.currentTimeMillis() - startTime;

            saveLog(clientId, userId, userName, action, httpMethod, requestUri,
                    entityName, responseStatus, ipAddress, duration);

            return result;

        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - startTime;

            // 로그인 실패 시 action 변경 + 401 기록
            String failAction = isLoginRequest ? "로그인실패" : action;
            int failStatus = isLoginRequest ? 401 : 500;

            saveLog(clientId, userId, userName, failAction, httpMethod, requestUri,
                    entityName, failStatus, ipAddress, duration);

            throw ex;
        }
    }

    private void saveLog(UUID clientId, UUID userId, String userName, String action,
                         String httpMethod, String requestUri, String entityName,
                         Integer responseStatus, String ipAddress, long durationMs) {
        try {
            AuditLogEntity savedLog = auditLogRepository.save(AuditLogEntity.builder()
                    .clientId(clientId)
                    .userId(userId)
                    .userName(userName)
                    .action(action)
                    .httpMethod(httpMethod)
                    .requestUri(requestUri)
                    .entityName(entityName)
                    .responseStatus(responseStatus)
                    .ipAddress(ipAddress)
                    .durationMs(durationMs)
                    .build());
            auditLogEventPublisher.publish(savedLog);

        } catch (Exception e) {
            log.error("[감사로그] 저장 실패: {}", e.getMessage());
        }
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
