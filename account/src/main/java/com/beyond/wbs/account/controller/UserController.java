package com.beyond.wbs.account.controller;

import com.beyond.wbs.account.service.UserService;
import com.beyond.wbs.account.service.LoginAuditService;
import com.beyond.wbs.account.auth.JwtTokenProvider;
import com.beyond.wbs.account.domain.User;
import com.beyond.wbs.account.dtos.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

/**
 * 모든 사용자가 사용하는 API (로그인, 내 정보, 토큰 갱신)
 */
@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAuditService loginAuditService;

    @Autowired
    public UserController(UserService userService, JwtTokenProvider jwtTokenProvider, LoginAuditService loginAuditService) {
        this.userService = userService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.loginAuditService = loginAuditService;
    }

    @PostMapping("/doLogin")
    public ResponseEntity<?> doLogin(@RequestBody UserLoginReqDto dto, HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String ipAddress = getClientIp(request);
        try {
            User user = userService.login(dto);
            UserLoginResDto userLoginResDto = UserLoginResDto.builder()
                    .accessToken(jwtTokenProvider.createToken(user))
                    .refreshToken(jwtTokenProvider.createRtToken(user))
                    .build();
            loginAuditService.recordSuccess(user, ipAddress, System.currentTimeMillis() - startTime);
            return ResponseEntity.ok(userLoginResDto);
        } catch (RuntimeException e) {
            loginAuditService.recordFailure(dto, ipAddress, System.currentTimeMillis() - startTime);
            throw e;
        }
    }

    @AuditLog
    @GetMapping("/myinfo")
    public ResponseEntity<?> findMyInfo(@RequestHeader("X-User-Id") String userId) {
        UserDetailResDto dto = userService.findById(UUID.fromString(userId));
        return ResponseEntity.ok(dto);
    }

    /**
     * WebSocket 연결 전용 단기 ticket 발급.
     *
     * <p>응답의 {@code ticket} 을 ws URL 의 {@code ?ticket=} query 로 붙여 사용한다.
     * 5분 후 만료되며, 일반 JWT 와 같은 secret 으로 서명되므로 게이트웨이에서 검증된다.
     */
    @AuditLog
    @GetMapping("/ws-ticket")
    public ResponseEntity<?> issueWsTicket(@RequestHeader("X-User-Id") String userId) {
        String ticket = jwtTokenProvider.createWsTicket(UUID.fromString(userId));
        return ResponseEntity.ok(java.util.Map.of(
                "ticket", ticket,
                "expiresAt", java.time.Instant.now().plusSeconds(300).toString()
        ));
    }

    @AuditLog
    @PostMapping("/refresh-at")
    public ResponseEntity<?> refreshAt(@RequestBody RefreshTokenDto dto) {
        User user = jwtTokenProvider.validateRt(dto.getRefreshToken());
        UserLoginResDto userLoginResDto = UserLoginResDto.builder()
                .accessToken(jwtTokenProvider.createToken(user))
                .refreshToken(null)
                .build();
        return ResponseEntity.ok(userLoginResDto);
    }

    @AuditLog
    @PatchMapping("/password")
    public ResponseEntity<?> changePassword(
            @RequestBody @Valid PasswordChangeReqDto dto,
            @RequestHeader("X-User-Id") String userId) {
        userService.changePassword(UUID.fromString(userId), dto.getCurrentPassword(), dto.getNewPassword());
        return ResponseEntity.ok().build();
    }

    // 본인 정보 수정 (이메일, 전화번호)
    @AuditLog
    @PatchMapping("/myinfo")
    public ResponseEntity<?> updateMyInfo(
            @RequestBody MyInfoUpdateReqDto dto,
            @RequestHeader("X-User-Id") String userId) {
        userService.updateMyInfo(UUID.fromString(userId), dto);
        return ResponseEntity.ok().build();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            return request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }
}
