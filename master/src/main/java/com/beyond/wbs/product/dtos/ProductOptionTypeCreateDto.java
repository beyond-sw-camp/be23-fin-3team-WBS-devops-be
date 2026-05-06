package com.beyond.wbs.product.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductOptionTypeCreateDto {

    @NotBlank
    private String name;  // 예: 색상

    @NotBlank
    private String code;  // 예: COLOR
}