package com.beyond.wbs.product.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ProductOptionValueCreateDto {

    @NotNull
    private UUID optionTypeId;

    @NotBlank
    private String value;  // 예: 블랙

    @NotBlank
    private String code;   // 예: BLACK

    private Integer sortOrder;
}