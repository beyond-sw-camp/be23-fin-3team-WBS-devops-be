package com.beyond.wbs.account.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class UserCreateReqDto {
    @NotBlank
    private String name;
    @NotBlank
    private String loginId;
    private String email;
    private String phone;
    @NotBlank
    private String password;
    @NotNull
    private UUID roleId;
}
