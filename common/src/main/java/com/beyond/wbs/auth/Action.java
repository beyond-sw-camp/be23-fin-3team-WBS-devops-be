package com.beyond.wbs.auth;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Action {
    CREATE("AC001", "생성"),
    READ("AC002", "조회"),
    UPDATE("AC003", "수정"),
    DELETE("AC004", "삭제"),
    APPROVE("AC005", "승인");

    private final String codeValue;
    private final String codeName;
}
