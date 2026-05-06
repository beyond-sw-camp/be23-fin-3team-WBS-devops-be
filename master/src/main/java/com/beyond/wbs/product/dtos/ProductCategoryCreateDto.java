package com.beyond.wbs.product.dtos;

import com.beyond.wbs.code.MasterCodePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ProductCategoryCreateDto {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = MasterCodePolicy.PATTERN, message = MasterCodePolicy.PATTERN_MESSAGE)
    @Size(min = MasterCodePolicy.MIN_LEN, max = MasterCodePolicy.MAX_LEN, message = MasterCodePolicy.SIZE_MESSAGE)
    private String code;

    private UUID parentId;
    private Integer sortOrder;
}