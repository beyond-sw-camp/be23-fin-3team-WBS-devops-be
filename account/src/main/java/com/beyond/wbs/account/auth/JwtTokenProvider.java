package com.beyond.wbs.account.auth;

import com.beyond.wbs.account.domain.User;
import com.beyond.wbs.account.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secretKey}")
    private String stSecretKey;

    @Value("${jwt.expiration}")
    private int expiration;

    private Key secretKey;

    @Value("${jwt.secretKeyRt}")
    private String stSecretKeyRt;

    @Value("${jwt.expirationRt}")
    private int expirationRt;

    private Key secretKeyRt;

    private static final String RT_KEY_PREFIX = "rt:";

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final PermissionCacheService permissionCacheService;

    @Autowired
    public JwtTokenProvider(
            @Qualifier("accountRedis") RedisTemplate<String, String> redisTemplate,
            UserRepository userRepository,
            PermissionCacheService permissionCacheService
    ) {
        this.redisTemplate = redisTemplate;
        this.userRepository = userRepository;
        this.permissionCacheService = permissionCacheService;
    }

    @PostConstruct
    public void init() {
        secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(stSecretKey),
                SignatureAlgorithm.HS512.getJcaName()
        );

        secretKeyRt = new SecretKeySpec(
                Base64.getDecoder().decode(stSecretKeyRt),
                SignatureAlgorithm.HS512.getJcaName()
        );
    }

    public String createToken(User user) {
        Claims claims = Jwts.claims().setSubject(user.getId().toString());
        claims.put("clientId", user.getClient() != null ? user.getClient().getId().toString() : "");
        claims.put("isDeveloper", user.isDeveloper());

        String roleCode = user.getRole() != null ? user.getRole().getCode() : "";
        claims.put("role", roleCode);

        // Developer가 아닌 경우 권한 캐싱
        if (!user.isDeveloper()) {
            permissionCacheService.cachePermissions(user.getId());
        }

        Date now = new Date();

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expiration * 60 * 1000L))
                .signWith(secretKey)
                .compact();
    }

    /**
     * WebSocket 연결 전용 단기 ticket 발급.
     *
     * <p>일반 access token 과 같은 secret/clieam 으로 서명하되,
     * 만료시간만 5분으로 짧게 둔다
     */
    public String createWsTicket(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("entity is not found"));

        Claims claims = Jwts.claims().setSubject(user.getId().toString());
        claims.put("clientId", user.getClient() != null ? user.getClient().getId().toString() : "");
        claims.put("isDeveloper", user.isDeveloper());
        claims.put("role", user.getRole() != null ? user.getRole().getCode() : "");

        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 5 * 60 * 1000L))
                .signWith(secretKey)
                .compact();
    }

    public String createRtToken(User user) {
        Claims claims = Jwts.claims().setSubject(user.getId().toString());

        Date now = new Date();
        String token = Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expirationRt * 60 * 1000L))
                .signWith(secretKeyRt)
                .compact();

        redisTemplate.opsForValue().set(
                RT_KEY_PREFIX + user.getId(),
                token,
                expirationRt,
                TimeUnit.MINUTES
        );

        return token;
    }

    public User validateRt(String refreshToken) {
        Claims claims;

        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(secretKeyRt)
                    .build()
                    .parseClaimsJws(refreshToken)
                    .getBody();
        } catch (Exception e) {
            throw new IllegalArgumentException("잘못된 토큰입니다.");
        }

        UUID userId = UUID.fromString(claims.getSubject());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("entity is not found"));

        String redisRt = redisTemplate.opsForValue().get(RT_KEY_PREFIX + userId);
        if (redisRt == null || !redisRt.equals(refreshToken)) {
            throw new IllegalArgumentException("잘못된 토큰입니다.");
        }

        return user;
    }
}
