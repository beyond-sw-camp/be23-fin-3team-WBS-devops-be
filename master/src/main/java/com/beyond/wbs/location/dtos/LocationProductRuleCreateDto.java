package com.beyond.wbs.location.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class LocationProductRuleCreateDto {

    @NotNull
    private UUID locationId;

    // product_id, product_group_id 둘 중 하나는 필수
    private UUID productId;
    private UUID productGroupId;
}