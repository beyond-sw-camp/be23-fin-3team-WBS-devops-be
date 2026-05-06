package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.ProductOptionType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ProductOptionTypeResDto {
    private UUID id;
    private String name;
    private String code;

    public static ProductOptionTypeResDto from(ProductOptionType type) {
        return ProductOptionTypeResDto.builder()
                .id(type.getId())
                .name(type.getName())
                .code(type.getCode())
                .build();
    }
}