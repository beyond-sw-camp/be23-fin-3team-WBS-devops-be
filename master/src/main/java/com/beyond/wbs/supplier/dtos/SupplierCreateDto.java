package com.beyond.wbs.supplier.dtos;

import com.beyond.wbs.code.MasterCodePolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SupplierCreateDto {

    @NotBlank
    private String name;

    @NotBlank
    @Pattern(regexp = MasterCodePolicy.PATTERN, message = MasterCodePolicy.PATTERN_MESSAGE)
    @Size(min = MasterCodePolicy.MIN_LEN, max = MasterCodePolicy.MAX_LEN, message = MasterCodePolicy.SIZE_MESSAGE)
    private String code;

    private String bizNo;
    private String ceoName;
    private String tel;
    private String email;
    private String address;
    private String esgGrade;
    private Boolean ecoCertified;
    private String esgMemo;
}
