package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.ProductCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ProductCategoryResDto {
    private UUID id;
    private UUID parentId;
    private String name;
    private String code;
    private Integer depth;
    private Integer sortOrder;
    private Boolean isActive;

    public static ProductCategoryResDto from(ProductCategory category) {
        return ProductCategoryResDto.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .name(category.getName())
                .code(category.getCode())
                .depth(category.getDepth())
                .sortOrder(category.getSortOrder())
                .isActive(category.getIsActive())
                .build();
    }
}