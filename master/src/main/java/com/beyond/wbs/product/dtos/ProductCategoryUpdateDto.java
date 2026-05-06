package com.beyond.wbs.product.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductCategoryUpdateDto {
    private String name;
    private Integer sortOrder;
}
