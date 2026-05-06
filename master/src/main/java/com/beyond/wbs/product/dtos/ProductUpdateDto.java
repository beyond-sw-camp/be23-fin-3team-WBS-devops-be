package com.beyond.wbs.product.dtos;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 상품 부분 수정 DTO.
 * null 필드는 변경하지 않음 (부분 수정 지원).
 * sku / clientId 는 PK 성격이라 수정 불가.
 */
@Getter
@NoArgsConstructor
public class ProductUpdateDto {

    @Size(max = 150)
    private String name;

    @Size(max = 150)
    private String nameEn;

    private String description;

    @Size(max = 100)
    private String barcode;

    @Size(max = 20)
    private String unit;

    private Integer unitPerBox;

    private BigDecimal standardPrice;

    private BigDecimal weight;

    private BigDecimal width;

    private BigDecimal depth;

    private BigDecimal height;

    // 협력사 변경
    private UUID supplierId;

    // 상품그룹 변경
    private UUID productGroupId;

    // 옵션 값 ID 리스트로 매핑 교체. null 이면 변경하지 않음, 빈 리스트면 모두 제거.
    private List<UUID> optionValueIds;
}
