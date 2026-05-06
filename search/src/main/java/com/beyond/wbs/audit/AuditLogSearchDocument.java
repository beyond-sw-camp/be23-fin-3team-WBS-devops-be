package com.beyond.wbs.audit;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * ES 감사 로그 문서.
 *
 * 인덱스: audit-logs-v2
 * 전문 검색 대상: userName, requestUri, entityName
 * 필터 대상: action, userId, clientId, responseStatus
 * 범위 검색: createdAt
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
@Document(indexName = "audit-logs-v2")
public class AuditLogSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String clientId;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String serviceName;

    @Field(type = FieldType.Text)
    private String userName;

    @Field(type = FieldType.Keyword)
    private String action;

    @Field(type = FieldType.Keyword)
    private String httpMethod;

    @Field(type = FieldType.Text)
    private String requestUri;

    @Field(type = FieldType.Keyword)
    private String entityName;

    @Field(type = FieldType.Integer)
    private Integer responseStatus;

    @Field(type = FieldType.Keyword)
    private String ipAddress;

    @Field(type = FieldType.Search_As_You_Type)
    private String suggestText;

    @Field(type = FieldType.Long)
    private Long durationMs;

    @Field(type = FieldType.Date)
    private String createdAt;

    /**
     * 알림 이벤트(AlertAuditLogger)의 payload(JSON). 검색 인덱싱은 안 하고 원본 보관용으로만.
     * NotificationPage 가 행 클릭 시 이동할 SO/Product 정보가 여기에 들어있다.
     */
    @Field(type = FieldType.Keyword, index = false)
    private String requestBody;
}
