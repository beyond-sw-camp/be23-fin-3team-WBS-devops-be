package com.beyond.wbs.common.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserResDto {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private List<String> roles;
    private UUID warehouseId;
}
