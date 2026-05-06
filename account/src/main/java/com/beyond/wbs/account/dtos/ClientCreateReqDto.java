package com.beyond.wbs.account.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ClientCreateReqDto {
    @NotBlank
    private String name;
    @NotBlank
    private String bizNo;
    @NotBlank
    private String masterName;
    @NotBlank
    private String masterLoginId;
    private String masterEmail;
    @NotBlank
    private String masterPassword;
}
