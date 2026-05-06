package com.beyond.wbs.store.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class StoreAddressCreateDto {

    @NotNull
    private UUID storeId;

    @NotBlank
    private String name;

    @NotBlank
    private String receiver;

    @NotBlank
    private String tel;

    @NotBlank
    private String address;

    private String addressDetail;
    private String zipcode;
    private Boolean isDefault;
}