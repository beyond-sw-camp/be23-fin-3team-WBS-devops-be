package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.ProductOptionValue;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ProductOptionValueResDto {
    private UUID id;
    private UUID optionTypeId;
    private String optionTypeName;
    private String value;
    private String code;
    private Integer sortOrder;

    public static ProductOptionValueResDto from(ProductOptionValue v) {
        return ProductOptionValueResDto.builder()
                .id(v.getId())
                .optionTypeId(v.getOptionType().getId())
                .optionTypeName(v.getOptionType().getName())
                .value(v.getValue())
                .code(v.getCode())
                .sortOrder(v.getSortOrder())
                .build();
    }
}