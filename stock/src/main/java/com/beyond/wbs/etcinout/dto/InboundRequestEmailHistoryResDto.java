package com.beyond.wbs.etcinout.dto;

import com.beyond.wbs.etcinout.domain.InboundRequestEmail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 메일 발송 이력 응답 row.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundRequestEmailHistoryResDto {

    private UUID id;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private String sentByName;
    private String sentByEmail;
    private String recipient;
    private String subject;
    private String status;          // sent | failed
    private String errorMessage;

    public static InboundRequestEmailHistoryResDto fromEntity(InboundRequestEmail entity) {
        return InboundRequestEmailHistoryResDto.builder()
                .id(entity.getId())
                .sentAt(entity.getSentAt())
                .createdAt(entity.getCreatedAt())
                .sentByName(entity.getSentByName())
                .sentByEmail(entity.getSentByEmail())
                .recipient(entity.getRecipient())
                .subject(entity.getSubject())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
