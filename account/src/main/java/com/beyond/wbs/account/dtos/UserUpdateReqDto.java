package com.beyond.wbs.account.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserUpdateReqDto {
    private String name;
    private String email;
    private String phone;
    private UUID roleId;
}
