package com.beyond.wbs.etcinout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 입고 요청 메일 발송 응답.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InboundRequestSendResDto {

    private UUID emailLogId;
    private LocalDateTime sentAt;
    private String status;          // "sent" | "failed"
    private String errorMessage;    // status=failed 일 때만 채움
}
