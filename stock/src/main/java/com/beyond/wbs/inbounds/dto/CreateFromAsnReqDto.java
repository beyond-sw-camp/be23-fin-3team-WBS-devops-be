package com.beyond.wbs.inbounds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CreateFromAsnReqDto {
    @NotNull
    private UUID asnId;
    @NotBlank
    private String warehouse;
}
