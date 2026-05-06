package com.beyond.wbs.account.auth;

import com.beyond.wbs.account.domain.RolePermission;
import com.beyond.wbs.account.domain.User;
import com.beyond.wbs.account.repository.RolePermissionRepository;
import com.beyond.wbs.account.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PermissionCacheService {

    private static final String PERM_KEY_PREFIX = "perm:";

    private final UserRepository userRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${jwt.expiration}")
    private int atExpirationMinutes;

    public PermissionCacheService(
            UserRepository userRepository,
            RolePermissionRepository rolePermissionRepository,
            @Qualifier("accountRedis") RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 로그인 시 호출 - 사용자의 권한 목록을 Redis에 캐싱
     *
     * Redis 저장 형태:
     * key:   perm:{userId}
     * value: "INBOUND:CREATE,INBOUND:READ,OUTBOUND:UPDATE,..." (콤마 구분)
     */
    public void cachePermissions(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        if (user.getRole() == null) {
            return;
        }

        List<RolePermission> rolePermissions = rolePermissionRepository
                .findByRoleIdWithPermission(user.getRole().getId());

        String permissionStr = rolePermissions.stream()
                .map(rp -> rp.getPermission().getResource().name() + ":" + rp.getPermission().getAction().name())
                .distinct()
                .collect(Collectors.joining(","));

        String key = PERM_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, permissionStr, atExpirationMinutes, TimeUnit.MINUTES);
    }

    /**
     * Redis 에서 캐싱된 권한 문자열을 가져와 List 로 분리.
     * 캐시 미스(만료/미존재) 시 DB 에서 다시 캐싱 후 반환.
     */
    public List<String> getPermissions(UUID userId) {
        String key = PERM_KEY_PREFIX + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null || cached.isBlank()) {
            cachePermissions(userId);
            cached = redisTemplate.opsForValue().get(key);
        }
        if (cached == null || cached.isBlank()) {
            return List.of();
        }
        return List.of(cached.split(","));
    }

    public void evictPermissions(UUID userId) {
        redisTemplate.delete(PERM_KEY_PREFIX + userId);
    }
}
