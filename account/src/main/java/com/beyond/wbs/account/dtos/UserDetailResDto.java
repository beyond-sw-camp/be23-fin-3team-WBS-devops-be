package com.beyond.wbs.account.dtos;

import com.beyond.wbs.account.domain.User;
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
public class UserDetailResDto {
    private UUID id;
    private String name;
    private String loginId;
    private String email;
    private String phone;
    private boolean isActive;
    private UUID roleId;
    private String roleCode;
    private String roleName;
    // "RESOURCE:ACTION" 형식 문자열 리스트 (Redis 에서 가져옴)
    private List<String> permissions;

    public static UserDetailResDto fromEntity(User user) {
        return fromEntity(user, null);
    }

    public static UserDetailResDto fromEntity(User user, List<String> permissions) {
        return UserDetailResDto.builder()
                .id(user.getId())
                .name(user.getName())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .roleCode(user.getRole() != null ? user.getRole().getCode() : null)
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .permissions(permissions)
                .build();
    }
}
