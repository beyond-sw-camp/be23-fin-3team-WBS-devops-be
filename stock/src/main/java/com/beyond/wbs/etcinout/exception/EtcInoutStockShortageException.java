package com.beyond.wbs.etcinout.exception;

import com.beyond.wbs.etcinout.dto.StockShortageItemDto;
import lombok.Getter;

import java.util.List;

/**
 * 기타출고 승인 시 가용재고 부족.
 *
 * <p>운영자는 응답 페이로드의 부족 목록을 보고
 * (a) 출고지시서 취소 후보 조회 → 취소 → 재시도 또는
 * (b) 입고 요청 후 재시도 중 선택.
 */
@Getter
public class EtcInoutStockShortageException extends RuntimeException {

    private final List<StockShortageItemDto> shortages;

    public EtcInoutStockShortageException(List<StockShortageItemDto> shortages) {
        super("가용재고가 부족합니다.");
        this.shortages = shortages;
    }
}
