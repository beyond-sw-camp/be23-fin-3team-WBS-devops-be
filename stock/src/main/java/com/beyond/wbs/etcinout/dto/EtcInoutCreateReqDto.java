package com.beyond.wbs.etcinout.dto;

import com.beyond.wbs.etcinout.domain.IoType;
import com.beyond.wbs.etcinout.domain.RefType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutCreateReqDto {

    @NotNull(message = "창고를 선택해주세요.")
    private UUID warehouseId;

    @NotNull(message = "사유를 선택해주세요.")
    private IoType ioType;

    private String note;
    private UUID supplierId;
    private UUID storeId;
    private UUID refId;
    private RefType refType;

    @NotEmpty(message = "품목을 하나 이상 추가해주세요.")
    @Valid
    private List<EtcInoutItemDto> items;

    // 이 기타출고 등록과 함께 취소된 출고지시서 IDs.
    // 프론트가 출고를 cancel 한 후 그 outboundId 들을 여기 넘기면,
    // 백엔드가 cancellation_link 를 자동 저장 → 입고요청 메일 본문이 정확히 채워짐.
    private List<UUID> cancelledOutboundIds;
}