package com.beyond.wbs.account.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class RoleCreateReqDto {
    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "역할 코드는 영문 대문자, 숫자, 언더스코어만 허용됩니다.")
    private String code;

    private String description;

    @NotEmpty
    private List<UUID> permissionIds;
}
