package com.beyond.wbs.product.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ProductGroupCreateDto {

    @NotBlank
    private String name;

    @NotBlank
    private String code;

    private UUID categoryId;
    private String brand;
    private String description;
}