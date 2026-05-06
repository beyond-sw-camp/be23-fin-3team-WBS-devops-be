package com.beyond.wbs.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * @CheckPermission 어노테이션이 붙은 메서드의 권한 체크
 * Redis에 저장된 사용자 권한 목록과 비교
 */
@Aspect
@Component
public class PermissionAspect {

    private static final String PERM_KEY_PREFIX = "perm:";

    private final RedisTemplate<String, String> redisTemplate;

    public PermissionAspect(
            @Qualifier("accountRedis") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(checkPermission)")
    public Object check(ProceedingJoinPoint pjp, CheckPermission checkPermission) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.getRequestAttributes()).getRequest();

        // Developer는 모든 권한 통과
        String isDeveloper = request.getHeader("X-Is-Developer");
        if ("true".equals(isDeveloper)) {
            return pjp.proceed();
        }

        // User ID로 Redis에서 권한 조회
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            throw new SecurityException("인증 정보가 없습니다.");
        }

        String permissionStr = redisTemplate.opsForValue().get(PERM_KEY_PREFIX + userId);
        if (permissionStr == null || permissionStr.isBlank()) {
            throw new SecurityException("권한 정보가 없습니다. 다시 로그인해주세요.");
        }

        // "INBOUND:CREATE" 형식으로 체크
        String required = checkPermission.resource().name() + ":" + checkPermission.action().name();

        boolean hasPermission = List.of(permissionStr.split(","))
                .contains(required);

        if (!hasPermission) {
            throw new SecurityException("권한이 없습니다: " + required);
        }

        return pjp.proceed();
    }
}
