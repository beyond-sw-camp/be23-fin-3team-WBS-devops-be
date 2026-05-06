package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class AccountUserListResDto {
    private UUID id;
    private String name;
    private String loginId;
    private String email;
    private UUID roleId;
    private String roleCode;
    private String roleName;
}
