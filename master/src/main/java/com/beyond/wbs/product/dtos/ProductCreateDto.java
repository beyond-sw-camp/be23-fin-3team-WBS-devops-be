package com.beyond.wbs.product.dtos;

import com.beyond.wbs.product.domain.OwnerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCreateDto {

    @NotNull
    private OwnerType ownerType;

    private UUID supplierId;
    private UUID productGroupId;

    @NotBlank
    private String sku;

    private String barcode;

    @NotBlank
    private String name;

    private String nameEn;
    private String description;

    @NotBlank
    private String unit;

    private Integer unitPerBox;

    private BigDecimal standardPrice;

    private BigDecimal weight;  // 선택 — 현재 미사용

    @NotNull
    private BigDecimal width;

    @NotNull
    private BigDecimal depth;

    @NotNull
    private BigDecimal height;

    // 등록 시 함께 매핑할 옵션 값 ID 리스트 (선택). 비어있으면 옵션 매핑 없이 등록.
    private List<UUID> optionValueIds;
}