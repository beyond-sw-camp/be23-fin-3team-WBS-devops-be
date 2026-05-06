package com.beyond.wbs.supplier.dtos;

import com.beyond.wbs.supplier.domain.Supplier;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SupplierResDto {
    private UUID id;
    private String name;
    private String bizNo;
    private String code;
    private String ceoName;
    private String tel;
    private String email;
    private String address;
    private String esgGrade;
    private Boolean ecoCertified;
    private String esgMemo;
    private Boolean isActive;

    public static SupplierResDto from(Supplier supplier) {
        return SupplierResDto.builder()
                .id(supplier.getId())
                .name(supplier.getName())
                .bizNo(supplier.getBizNo())
                .code(supplier.getCode())
                .ceoName(supplier.getCeoName())
                .tel(supplier.getTel())
                .email(supplier.getEmail())
                .address(supplier.getAddress())
                .esgGrade(supplier.getEsgGrade())
                .ecoCertified(supplier.getEcoCertified())
                .esgMemo(supplier.getEsgMemo())
                .isActive(supplier.getIsActive())
                .build();
    }
}
