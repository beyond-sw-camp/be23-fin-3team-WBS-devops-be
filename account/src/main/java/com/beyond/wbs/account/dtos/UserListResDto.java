package com.beyond.wbs.account.dtos;

import com.beyond.wbs.account.domain.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class UserListResDto {
    private UUID id;
    private String name;
    private String loginId;
    private String email;
    private UUID roleId;
    private String roleCode;
    private String roleName;

    public static UserListResDto fromEntity(User user) {
        return UserListResDto.builder()
                .id(user.getId())
                .name(user.getName())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .roleId(user.getRole() != null ? user.getRole().getId() : null)
                .roleCode(user.getRole() != null ? user.getRole().getCode() : null)
                .roleName(user.getRole() != null ? user.getRole().getName() : null)
                .build();
    }
}
