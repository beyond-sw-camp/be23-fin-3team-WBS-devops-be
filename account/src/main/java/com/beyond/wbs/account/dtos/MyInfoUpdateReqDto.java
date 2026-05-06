package com.beyond.wbs.account.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class MyInfoUpdateReqDto {
    private String email;
    private String phone;
}
