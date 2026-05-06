package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.ProductGroup;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ProductGroupResDto {
    private UUID id;
    private UUID clientId;
    private UUID categoryId;
    private String categoryName;
    private String name;
    private String code;
    private String brand;
    private String description;
    private Boolean isActive;

    public static ProductGroupResDto from(ProductGroup group) {
        return ProductGroupResDto.builder()
                .id(group.getId())
                .clientId(group.getClientId())
                .categoryId(group.getCategory() != null ? group.getCategory().getId() : null)
                .categoryName(group.getCategory() != null ? group.getCategory().getName() : null)
                .name(group.getName())
                .code(group.getCode())
                .brand(group.getBrand())
                .description(group.getDescription())
                .isActive(group.getIsActive())
                .build();
    }
}