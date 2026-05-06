package com.beyond.wbs.product.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 상품 그룹 자동 제안 응답.
 *
 * 프론트의 [자동 제안] 버튼이 호출할 때 반환되는 정보.
 * - suggestedCode: 제안 코드 (관리자가 수정 가능)
 * - existingGroups: 동일 카테고리의 기존 그룹 목록 — 네이밍 규칙 참고용
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductGroupSuggestResDto {

    private String suggestedCode;

    private List<ExistingGroup> existingGroups;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExistingGroup {
        private String name;
        private String code;
    }
}
