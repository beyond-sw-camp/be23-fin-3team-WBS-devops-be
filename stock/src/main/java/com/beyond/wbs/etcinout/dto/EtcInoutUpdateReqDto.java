package com.beyond.wbs.etcinout.dto;

import com.beyond.wbs.etcinout.domain.IoType;
import com.beyond.wbs.etcinout.domain.RefType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutUpdateReqDto {

    @NotNull(message = "사유를 선택해주세요.")
    private IoType ioType;

    private String note;

    @NotNull(message = "창고를 선택해주세요.")
    private UUID warehouseId;

    private UUID supplierId;
    private UUID storeId;
    private UUID refId;
    private RefType refType;
}