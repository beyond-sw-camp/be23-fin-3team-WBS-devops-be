package com.beyond.wbs.account.service;

import com.beyond.wbs.account.domain.User;
import com.beyond.wbs.account.dtos.UserLoginReqDto;
import com.beyond.wbs.account.repository.UserRepository;
import com.beyond.wbs.audit.AuditLogEntity;
import com.beyond.wbs.audit.AuditLogEventPublisher;
import com.beyond.wbs.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginAuditService {

    private static final String HTTP_METHOD = "POST";
    private static final String REQUEST_URI = "/user/doLogin";
    private static final String ENTITY_NAME = "user";

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogEventPublisher auditLogEventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(User user, String ipAddress, long durationMs) {
        record(user, "로그인", 200, ipAddress, durationMs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(UserLoginReqDto dto, String ipAddress, long durationMs) {
        Optional<User> user = dto != null && dto.getLoginId() != null
                ? userRepository.findByLoginIdWithClient(dto.getLoginId())
                : Optional.empty();
        record(user.orElse(null), "로그인실패", 401, ipAddress, durationMs);
    }

    private void record(User user, String action, int responseStatus, String ipAddress, long durationMs) {
        UUID clientId = user != null && user.getClient() != null ? user.getClient().getId() : null;
        UUID userId = user != null ? user.getId() : null;
        String userName = user != null ? user.getName() : null;

        AuditLogEntity savedLog = auditLogRepository.save(AuditLogEntity.builder()
                .clientId(clientId)
                .userId(userId)
                .userName(userName)
                .action(action)
                .httpMethod(HTTP_METHOD)
                .requestUri(REQUEST_URI)
                .entityName(ENTITY_NAME)
                .responseStatus(responseStatus)
                .ipAddress(ipAddress)
                .durationMs(durationMs)
                .build());

        auditLogEventPublisher.publish(savedLog);
    }
}
