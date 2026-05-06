package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.OwnerType;
import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.domain.ProductCategory;
import com.beyond.wbs.product.domain.ProductGroup;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class ProductListResDto {
    private UUID id;
    private OwnerType ownerType;
    private UUID supplierId;
    private UUID productGroupId;
    private String productGroupName;
    // 대분류(root) 카테고리 — Zone.category_id 와 직접 매칭되는 레벨.
    // ProductGroup.category 는 소분류(leaf)를 가리키므로, 여기서 parent 를 타고 올라가 root 로 resolve.
    private UUID categoryId;
    private String categoryName;
    private String sku;
    private String barcode;
    private String name;
    private String nameEn;
    private String unit;
    private Integer unitPerBox;
    private BigDecimal standardPrice;
    private Boolean isActive;
    private BigDecimal weight;
    private BigDecimal width;
    private BigDecimal depth;
    private BigDecimal height;

    public static ProductListResDto from(Product product) {
        ProductGroup group = product.getProductGroup();
        ProductCategory rootCategory = resolveRootCategory(group);

        return ProductListResDto.builder()
                .id(product.getId())
                .ownerType(product.getOwnerType())
                .supplierId(product.getSupplierId())
                .productGroupId(group != null ? group.getId() : null)
                .productGroupName(group != null ? group.getName() : null)
                .categoryId(rootCategory != null ? rootCategory.getId() : null)
                .categoryName(rootCategory != null ? rootCategory.getName() : null)
                .sku(product.getSku())
                .barcode(product.getBarcode())
                .name(product.getName())
                .nameEn(product.getNameEn())
                .unit(product.getUnit())
                .unitPerBox(product.getUnitPerBox())
                .standardPrice(product.getStandardPrice())
                .isActive(product.getIsActive())
                .weight(product.getWeight())
                .width(product.getWidth())
                .depth(product.getDepth())
                .height(product.getHeight())
                .build();
    }

    private static ProductCategory resolveRootCategory(ProductGroup group) {
        if (group == null || group.getCategory() == null) {
            return null;
        }
        ProductCategory node = group.getCategory();
        while (node.getParent() != null) {
            node = node.getParent();
        }
        return node;
    }
}