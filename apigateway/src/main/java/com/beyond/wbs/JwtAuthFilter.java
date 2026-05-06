package com.beyond.wbs;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Base64;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // StripPrefix 등 route 필터보다 먼저 실행되어야 원본 경로 확인 가능
public class JwtAuthFilter implements GlobalFilter {

    @Value("${jwt.secretKey}")
    private String stSecretKey;

    private Key secretKey;

    @PostConstruct
    public void init() {
        secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(stSecretKey),
                SignatureAlgorithm.HS512.getJcaName()
        );
    }

    // 인증 불필요 경로 (정확 일치: endsWith)
    private static final List<String> ALLOWED_PATH = List.of(
            "/user/doLogin",
            "/user/refresh-at"
    );

    // 인증 불필요 경로 (prefix 일치: startsWith)
    //   - /ai-service/** : AI 기능은 개발 단계에서 공개. 추후 JWT 연동 시 제거 예정.
    private static final List<String> PUBLIC_PREFIX = List.of(
            "/ai-service"
    );

    // WebSocket 핸드셰이크 경로 — 표준 브라우저 WebSocket API 가 HTTP Authorization 헤더를
    // 못 박으므로, 단기 ticket 을 query string {@code ?ticket=...} 으로 받아 검증한다.
    private static final String WS_HANDSHAKE_PATH = "/stock-service/ws";

    // 회사 admin 전용 경로
    private static final List<String> ADMIN_ONLY_PATH = List.of("/admin");

    // 개발자 전용 경로
    private static final List<String> DEVELOPER_ONLY_PATH = List.of("/developer");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String bearerToken = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String urlPath = exchange.getRequest().getURI().getRawPath();

        // 인증이 필요 없는 경로는 필터 통과
        if (ALLOWED_PATH.stream().anyMatch(urlPath::endsWith)) {
            return chain.filter(exchange);
        }
        if (PUBLIC_PREFIX.stream().anyMatch(urlPath::startsWith)) {
            return chain.filter(exchange);
        }

        try {
            // WebSocket 핸드셰이크는 HTTP 헤더에 토큰을 못 박으므로 query string 의 ticket 을 사용.
            String token;
            if (urlPath.startsWith(WS_HANDSHAKE_PATH)) {
                String ticket = exchange.getRequest().getQueryParams().getFirst("ticket");
                if (ticket == null || ticket.isBlank()) {
                    throw new IllegalArgumentException("WebSocket ticket 이 없습니다.");
                }
                token = ticket;
            } else {
                if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
                    throw new IllegalArgumentException("token이 없거나, 형식이 잘못되었습니다.");
                }
                token = bearerToken.substring(7);
            }

            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String userId = claims.getSubject();
            String clientId = claims.get("clientId", String.class);
            String role = claims.get("role", String.class);
            Boolean isDeveloperObj = claims.get("isDeveloper", Boolean.class);

            boolean isAdmin = "ADMIN".equals(role);
            boolean isDeveloper = isDeveloperObj != null && isDeveloperObj;

            // developer 전용 경로 검증
            if (DEVELOPER_ONLY_PATH.stream().anyMatch(urlPath::contains) && !isDeveloper) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // admin 전용 경로 검증 (Developer는 admin 경로 접근 불가)
            if (ADMIN_ONLY_PATH.stream().anyMatch(urlPath::contains) && !isAdmin) {
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }

            // 서비스 모듈로 헤더 전달
            ServerWebExchange serverWebExchange = exchange.mutate()
                    .request(r -> r
                            .header("X-User-Id", userId != null ? userId : "")
                            .header("X-Client-Id", clientId != null ? clientId : "")
                            .header("X-User-Role", role != null ? role : "")
                            .header("X-Is-Developer", String.valueOf(isDeveloper))
                            .header("X-Is-Admin", String.valueOf(isAdmin)))
                    .build();

            return chain.filter(serverWebExchange);

        } catch (Exception e) {
            e.printStackTrace();
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }
}
