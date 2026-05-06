package com.beyond.wbs.supplier.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class SupplierProductCreateDto {

    @NotNull
    private UUID supplierId;

    @NotNull
    private UUID productId;

    private String venderSku;
    private BigDecimal unitPrice;
    private Integer leadTimeDays;
    private Integer moq;
    private Boolean isPrimary;
}