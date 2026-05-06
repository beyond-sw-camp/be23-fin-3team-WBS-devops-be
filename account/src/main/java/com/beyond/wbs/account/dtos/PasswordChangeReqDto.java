package com.beyond.wbs.account.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PasswordChangeReqDto {
    @NotBlank
    private String currentPassword;
    @NotBlank
    private String newPassword;
}
