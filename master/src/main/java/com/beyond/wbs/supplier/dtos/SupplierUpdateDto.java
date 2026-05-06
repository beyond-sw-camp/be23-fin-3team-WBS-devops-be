package com.beyond.wbs.supplier.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SupplierUpdateDto {

    @NotBlank
    private String name;

    private String bizNo;
    private String ceoName;
    private String tel;
    private String email;
    private String address;
    private String esgGrade;
    private Boolean ecoCertified;
    private String esgMemo;
}
