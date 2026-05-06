package com.beyond.wbs.inbounds.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ReceiveReqDto {
    @NotNull
    private List<ReceiveRowDto> rows;
}
