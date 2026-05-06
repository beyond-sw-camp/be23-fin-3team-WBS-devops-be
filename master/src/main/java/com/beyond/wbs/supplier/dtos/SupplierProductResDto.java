package com.beyond.wbs.supplier.dtos;

import com.beyond.wbs.supplier.domain.SupplierProduct;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class SupplierProductResDto {
    private UUID id;
    private UUID supplierId;
    private String supplierName;
    private UUID productId;
    private String productName;
    private String venderSku;
    private BigDecimal unitPrice;
    private Integer leadTimeDays;
    private Integer moq;
    private Boolean isPrimary;
    private Boolean isActive;

    public static SupplierProductResDto from(SupplierProduct sp) {
        return SupplierProductResDto.builder()
                .id(sp.getId())
                .supplierId(sp.getSupplier().getId())
                .supplierName(sp.getSupplier().getName())
                .productId(sp.getProduct().getId())
                .productName(sp.getProduct().getName())
                .venderSku(sp.getVenderSku())
                .unitPrice(sp.getUnitPrice())
                .leadTimeDays(sp.getLeadTimeDays())
                .moq(sp.getMoq())
                .isPrimary(sp.getIsPrimary())
                .isActive(sp.getIsActive())
                .build();
    }
}