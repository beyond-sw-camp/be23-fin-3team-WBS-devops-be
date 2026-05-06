package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.ProductOption;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ProductOptionResDto {
    private UUID id;
    private UUID productId;
    private UUID optionTypeId;
    private String optionTypeName;
    private UUID optionValueId;
    private String optionValue;
    private String optionValueCode;

    public static ProductOptionResDto from(ProductOption option) {
        return ProductOptionResDto.builder()
                .id(option.getId())
                .productId(option.getProduct().getId())
                .optionTypeId(option.getOptionType().getId())
                .optionTypeName(option.getOptionType().getName())
                .optionValueId(option.getOptionValue().getId())
                .optionValue(option.getOptionValue().getValue())
                .optionValueCode(option.getOptionValue().getCode())
                .build();
    }
}