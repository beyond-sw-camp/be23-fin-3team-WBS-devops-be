package com.beyond.wbs.audit;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AuditLogResDto {
    private UUID id;
    private UUID clientId;
    private UUID userId;
    private String userName;
    private String action;
    private String httpMethod;
    private String requestUri;
    private String entityName;
    private Integer responseStatus;
    private String ipAddress;
    private Long durationMs;
    private LocalDateTime createdAt;

    public static AuditLogResDto from(AuditLogEntity entity) {
        return AuditLogResDto.builder()
                .id(entity.getId())
                .clientId(entity.getClientId())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .action(entity.getAction())
                .httpMethod(entity.getHttpMethod())
                .requestUri(entity.getRequestUri())
                .entityName(entity.getEntityName())
                .responseStatus(entity.getResponseStatus())
                .ipAddress(entity.getIpAddress())
                .durationMs(entity.getDurationMs())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
