package com.beyond.wbs.audit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditLogSearchResDto {
    private String id;
    private String clientId;
    private String userId;
    private String serviceName;
    private String userName;
    private String action;
    private String httpMethod;
    private String requestUri;
    private String entityName;
    private Integer responseStatus;
    private String ipAddress;
    private Long durationMs;
    private String createdAt;
    private String requestBody;

    public static AuditLogSearchResDto from(AuditLogSearchDocument document) {
        return AuditLogSearchResDto.builder()
                .id(document.getId())
                .clientId(document.getClientId())
                .userId(document.getUserId())
                .serviceName(document.getServiceName())
                .userName(document.getUserName())
                .action(document.getAction())
                .httpMethod(document.getHttpMethod())
                .requestUri(document.getRequestUri())
                .entityName(document.getEntityName())
                .responseStatus(document.getResponseStatus())
                .ipAddress(document.getIpAddress())
                .durationMs(document.getDurationMs())
                .createdAt(document.getCreatedAt())
                .requestBody(document.getRequestBody())
                .build();
    }
}
