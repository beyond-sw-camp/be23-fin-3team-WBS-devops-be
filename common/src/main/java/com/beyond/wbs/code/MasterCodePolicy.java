package com.beyond.wbs.code;

/**
 * 사용자가 직접 입력하는 마스터 코드의 공통 정책.
 * - 협력사(Supplier), 출고처(Store), 상품 카테고리(ProductCategory)에 적용
 * - 이 코드들은 Rack/Zone 등 자동 생성 코드의 구성요소로 들어가므로
 *   짧고 의미있는 영문 약어로 강제한다.
 *
 * 예: LGX, TECH, FAST, ELC, DISP
 */
public final class MasterCodePolicy {
    public static final String PATTERN = "^[A-Z0-9_-]+$";
    public static final int MIN_LEN = 2;
    public static final int MAX_LEN = 8;
    public static final String PATTERN_MESSAGE = "코드는 영문 대문자/숫자/언더스코어/하이픈만 사용 가능합니다";
    public static final String SIZE_MESSAGE = "코드는 2~8자여야 합니다";

    private MasterCodePolicy() {}
}
