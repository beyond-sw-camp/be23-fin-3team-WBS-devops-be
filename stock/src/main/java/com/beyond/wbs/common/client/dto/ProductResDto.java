package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class ProductResDto {
    private UUID id;
    private String name;
    private String sku;
    private String barcode;
    private UUID supplierId;
    private UUID productGroupId;
    private String productGroupName;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal width;
    private BigDecimal depth;
    private BigDecimal height;
}
