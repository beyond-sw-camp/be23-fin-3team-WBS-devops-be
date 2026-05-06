package com.beyond.wbs.location.dtos;

import com.beyond.wbs.location.domain.LocationProductRule;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class LocationProductRuleResDto {
    private UUID id;
    private UUID locationId;
    private UUID productId;
    private String productName;
    private UUID productGroupId;
    private String productGroupName;

    public static LocationProductRuleResDto from(LocationProductRule rule) {
        return LocationProductRuleResDto.builder()
                .id(rule.getId())
                .locationId(rule.getLocation().getId())
                .productId(rule.getProduct() != null ? rule.getProduct().getId() : null)
                .productName(rule.getProduct() != null ? rule.getProduct().getName() : null)
                .productGroupId(rule.getProductGroup() != null ? rule.getProductGroup().getId() : null)
                .productGroupName(rule.getProductGroup() != null ? rule.getProductGroup().getName() : null)
                .build();
    }
}