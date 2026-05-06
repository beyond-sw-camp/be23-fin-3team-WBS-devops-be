package com.beyond.wbs.account.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RoleUpdateReqDto {
    private String name;
    private String description;
    private List<UUID> permissionIds;
}
