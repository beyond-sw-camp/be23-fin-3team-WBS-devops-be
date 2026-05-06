package com.beyond.wbs.account.dtos;

import com.beyond.wbs.account.domain.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class PermissionResDto {
    private UUID id;
    private String resource;
    private String action;
    private String description;

    public static PermissionResDto fromEntity(Permission permission) {
        return PermissionResDto.builder()
                .id(permission.getId())
                .resource(permission.getResource().name())
                .action(permission.getAction().name())
                .description(permission.getDescription())
                .build();
    }
}
