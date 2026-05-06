package com.beyond.wbs.audit;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuditLogSuggestResDto {
    private String type;
    private String label;
    private String value;
}
