package com.beyond.wbs.store.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 출고처 자동 웨이브 생성 토글 요청.
 * true 면 WaveScheduler 가 이 출고처의 OB 를 자동 처리, false 면 제외.
 */
@Getter
@Setter
@NoArgsConstructor
public class StoreAutoWaveToggleReqDto {

    @NotNull(message = "자동 웨이브 사용 여부(autoWaveEnabled)는 필수입니다.")
    private Boolean autoWaveEnabled;
}
