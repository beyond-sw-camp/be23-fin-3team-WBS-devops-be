package com.beyond.wbs.store.dtos;

import com.beyond.wbs.store.domain.Store;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class StoreResDto {
    private UUID id;
    private String name;
    private String bizNo;
    private String code;
    private String ceoName;
    private String tel;
    private String email;
    private String address;
    private Boolean isActive;
    private Boolean autoWaveEnabled;

    public static StoreResDto from(Store store) {
        return StoreResDto.builder()
                .id(store.getId())
                .name(store.getName())
                .bizNo(store.getBizNo())
                .code(store.getCode())
                .ceoName(store.getCeoName())
                .tel(store.getTel())
                .email(store.getEmail())
                .address(store.getAddress())
                .isActive(store.getIsActive())
                .autoWaveEnabled(store.getAutoWaveEnabled())
                .build();
    }
}