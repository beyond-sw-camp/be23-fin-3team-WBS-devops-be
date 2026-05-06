package com.beyond.wbs.product.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ProductGroupUpdateDto {
    private String name;
    private String brand;
    private String description;
    // 이동 대상 카테고리 (null 이면 변경 없음). 대상 카테고리가 비활성 상태면 서비스에서 거부.
    private UUID categoryId;
}
