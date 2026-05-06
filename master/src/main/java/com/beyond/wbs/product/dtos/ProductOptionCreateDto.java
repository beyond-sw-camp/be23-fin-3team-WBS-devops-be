package com.beyond.wbs.product.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ProductOptionCreateDto {

    @NotNull
    private UUID productId;

    @NotNull
    private UUID optionTypeId;

    @NotNull
    private UUID optionValueId;
}