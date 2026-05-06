package com.beyond.wbs.account.service;

import com.beyond.wbs.account.auth.PermissionCacheService;
import com.beyond.wbs.account.domain.*;
import com.beyond.wbs.account.dtos.*;
import com.beyond.wbs.account.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 회사 Admin 전용 서비스
 * - 같은 회사의 사용자 관리
 * - 회사 전용 Role 생성/수정/삭제
 */
@Service
@Transactional
public class AdminService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionCacheService permissionCacheService;

    public AdminService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        PermissionRepository permissionRepository,
                        RolePermissionRepository rolePermissionRepository,
                        PasswordEncoder passwordEncoder,
                        PermissionCacheService permissionCacheService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.permissionCacheService = permissionCacheService;
    }

    // ========== User 관리 ==========

    public UUID createUser(UserCreateReqDto dto, UUID adminId) {
        if (userRepository.findByLoginId(dto.getLoginId()).isPresent()) {
            throw new IllegalArgumentException("이미 사용중인 로그인 ID입니다.");
        }

        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));

        Role role = roleRepository.findById(dto.getRoleId())
                .orElseThrow(() -> new NoSuchElementException("역할을 찾을 수 없습니다."));

        if (!role.getClient().getId().equals(adminUser.getClient().getId())) {
            throw new IllegalArgumentException("같은 회사의 역할만 부여할 수 있습니다.");
        }

        if ("ADMIN".equals(role.getCode())) {
            throw new IllegalArgumentException("ADMIN 역할은 부여할 수 없습니다.");
        }

        User user = User.builder()
                .client(adminUser.getClient())
                .role(role)
                .name(dto.getName())
                .loginId(dto.getLoginId())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .password(passwordEncoder.encode(dto.getPassword()))
                .build();
        userRepository.save(user);

        return user.getId();
    }

    @Transactional(readOnly = true)
    public List<UserListResDto> findUsersByCompany(UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));

        return userRepository.findByClientIdAndIsActiveTrue(adminUser.getClient().getId())
                .stream()
                .map(UserListResDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserDetailResDto findUserDetail(UUID userId, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        if (!user.getClient().getId().equals(adminUser.getClient().getId())) {
            throw new IllegalArgumentException("같은 회사의 사용자만 조회할 수 있습니다.");
        }

        return UserDetailResDto.fromEntity(user);
    }

    public void updateUser(UUID userId, UserUpdateReqDto dto, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        if (!user.getClient().getId().equals(adminUser.getClient().getId())) {
            throw new IllegalArgumentException("같은 회사의 사용자만 수정할 수 있습니다.");
        }

        user.updateInfo(dto.getName(), dto.getEmail(), dto.getPhone());

        if (dto.getRoleId() != null) {
            Role newRole = roleRepository.findById(dto.getRoleId())
                    .orElseThrow(() -> new NoSuchElementException("역할을 찾을 수 없습니다."));

            if (!newRole.getClient().getId().equals(adminUser.getClient().getId())) {
                throw new IllegalArgumentException("같은 회사의 역할만 부여할 수 있습니다.");
            }
            if ("ADMIN".equals(newRole.getCode())) {
                throw new IllegalArgumentException("ADMIN 역할은 부여할 수 없습니다.");
            }

            user.changeRole(newRole);
            permissionCacheService.evictPermissions(userId);
        }
    }

    public void deactivateUser(UUID userId, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("사용자를 찾을 수 없습니다."));

        if (!user.getClient().getId().equals(adminUser.getClient().getId())) {
            throw new IllegalArgumentException("같은 회사의 사용자만 비활성화할 수 있습니다.");
        }

        if (user.getRole() != null && "ADMIN".equals(user.getRole().getCode())) {
            throw new IllegalArgumentException("관리자 계정은 비활성화할 수 없습니다.");
        }

        user.deactivate();
    }

    // ========== Role 관리 ==========

    @Transactional(readOnly = true)
    public List<RoleResDto> findRoles(UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));

        List<Role> roles = roleRepository.findByClientId(adminUser.getClient().getId());
        List<RoleResDto> result = new ArrayList<>();

        for (Role role : roles) {
            List<PermissionResDto> permissions = rolePermissionRepository
                    .findByRoleIdWithPermission(role.getId())
                    .stream()
                    .map(rp -> PermissionResDto.fromEntity(rp.getPermission()))
                    .toList();
            result.add(RoleResDto.fromEntity(role, permissions));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public RoleResDto findRole(UUID roleId, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NoSuchElementException("역할을 찾을 수 없습니다."));

        if (!role.getClient().getId().equals(adminUser.getClient().getId())) {
            throw new SecurityException("해당 역할에 대한 접근 권한이 없습니다.");
        }

        List<PermissionResDto> permissions = rolePermissionRepository
                .findByRoleIdWithPermission(roleId)
                .stream()
                .map(rp -> PermissionResDto.fromEntity(rp.getPermission()))
                .toList();

        return RoleResDto.fromEntity(role, permissions);
    }

    public UUID createRole(RoleCreateReqDto dto, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));

        UUID clientId = adminUser.getClient().getId();

        if (roleRepository.findByClientIdAndCode(clientId, dto.getCode()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 역할 코드입니다: " + dto.getCode());
        }

        if (List.of("ADMIN", "MANAGER", "OPERATOR").contains(dto.getCode())) {
            throw new IllegalArgumentException("예약된 역할 코드입니다: " + dto.getCode());
        }

        Role role = Role.builder()
                .client(adminUser.getClient())
                .code(dto.getCode())
                .name(dto.getName())
                .description(dto.getDescription())
                .isActive(true)
                .isSystem(false)
                .build();
        roleRepository.save(role);

        for (UUID permissionId : dto.getPermissionIds()) {
            Permission permission = permissionRepository.findById(permissionId)
                    .orElseThrow(() -> new NoSuchElementException("권한을 찾을 수 없습니다: " + permissionId));
            rolePermissionRepository.save(RolePermission.builder()
                    .role(role)
                    .permission(permission)
                    .build());
        }

        return role.getId();
    }

    public void updateRole(UUID roleId, RoleUpdateReqDto dto, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NoSuchElementException("역할을 찾을 수 없습니다."));

        if (!role.getClient().getId().equals(adminUser.getClient().getId())) {
            throw new IllegalArgumentException("같은 회사의 역할만 수정할 수 있습니다.");
        }

        role.updateInfo(dto.getName(), dto.getDescription());

        if (dto.getPermissionIds() != null) {
            rolePermissionRepository.deleteByRoleId(roleId);

            for (UUID permissionId : dto.getPermissionIds()) {
                Permission permission = permissionRepository.findById(permissionId)
                        .orElseThrow(() -> new NoSuchElementException("권한을 찾을 수 없습니다: " + permissionId));
                rolePermissionRepository.save(RolePermission.builder()
                        .role(role)
                        .permission(permission)
                        .build());
            }

            // 해당 Role을 쓰는 사용자들의 권한 캐시 무효화
            List<User> usersWithRole = userRepository.findByClientId(adminUser.getClient().getId())
                    .stream()
                    .filter(u -> u.getRole() != null && u.getRole().getId().equals(roleId))
                    .toList();
            for (User u : usersWithRole) {
                permissionCacheService.evictPermissions(u.getId());
            }
        }
    }

    public void deleteRole(UUID roleId, UUID adminId) {
        User adminUser = userRepository.findById(adminId)
                .orElseThrow(() -> new NoSuchElementException("관리자를 찾을 수 없습니다."));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NoSuchElementException("역할을 찾을 수 없습니다."));

        if (!role.getClient().getId().equals(adminUser.getClient().getId())) {
            throw new IllegalArgumentException("같은 회사의 역할만 삭제할 수 있습니다.");
        }

        if (role.isSystem()) {
            throw new IllegalArgumentException("시스템 기본 역할은 삭제할 수 없습니다.");
        }

        if (userRepository.existsByRoleId(roleId)) {
            throw new IllegalArgumentException("이 역할을 사용 중인 사용자가 있어 삭제할 수 없습니다.");
        }

        rolePermissionRepository.deleteByRoleId(roleId);
        roleRepository.delete(role);
    }

    // ========== Permission 조회 ==========

    @Transactional(readOnly = true)
    public List<PermissionResDto> findAllPermissions() {
        return permissionRepository.findAll()
                .stream()
                .map(PermissionResDto::fromEntity)
                .toList();
    }
}
