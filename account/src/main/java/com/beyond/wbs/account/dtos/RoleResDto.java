package com.beyond.wbs.account.dtos;

import com.beyond.wbs.account.domain.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class RoleResDto {
    private UUID id;
    private String name;
    private String code;
    private String description;
    private boolean isSystem;
    private boolean isActive;
    private List<PermissionResDto> permissions;

    public static RoleResDto fromEntity(Role role, List<PermissionResDto> permissions) {
        return RoleResDto.builder()
                .id(role.getId())
                .name(role.getName())
                .code(role.getCode())
                .description(role.getDescription())
                .isSystem(role.isSystem())
                .isActive(role.isActive())
                .permissions(permissions)
                .build();
    }
}
