package com.beyond.wbs.account.service;

import com.beyond.wbs.account.domain.*;
import com.beyond.wbs.account.dtos.*;
import com.beyond.wbs.account.repository.*;
import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
public class DeveloperService {
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    public DeveloperService(CompanyRepository companyRepository,
                            UserRepository userRepository,
                            RoleRepository roleRepository,
                            PermissionRepository permissionRepository,
                            RolePermissionRepository rolePermissionRepository,
                            PasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 회사 + admin 계정 동시 생성
     * 회사 전용 Role(ADMIN, MANAGER, OPERATOR)도 함께 생성 + Permission 매핑
     */
    public UUID createCompanyWithAdmin(ClientCreateReqDto dto) {
        if (userRepository.findByLoginId(dto.getMasterLoginId()).isPresent()) {
            throw new IllegalArgumentException("이미 사용중인 로그인 ID입니다.");
        }

        // 1. Client(회사) 생성
        Client client = Client.builder()
                .name(dto.getName())
                .bizNo(dto.getBizNo())
                .build();
        companyRepository.save(client);

        // 2. 회사 전용 기본 Role 3개 생성
        Role adminRole = createSystemRole(client, "ADMIN", "관리자", "회사 관리자");
        Role managerRole = createSystemRole(client, "MANAGER", "매니저", "지시서 생성/승인/실행");
        Role operatorRole = createSystemRole(client, "OPERATOR", "오퍼레이터", "현장 실행/조회");

        // 3. Role-Permission 매핑
        List<Permission> allPermissions = permissionRepository.findAll();
        assignAllPermissions(adminRole, allPermissions);
        assignAllPermissions(managerRole, allPermissions);
        assignOperatorPermissions(operatorRole, allPermissions);

        // 4. Admin User 생성 + ADMIN Role 부여
        User admin = User.builder()
                .client(client)
                .role(adminRole)
                .name(dto.getMasterName())
                .loginId(dto.getMasterLoginId())
                .email(dto.getMasterEmail())
                .password(passwordEncoder.encode(dto.getMasterPassword()))
                .build();
        userRepository.save(admin);

        return client.getId();
    }

    private Role createSystemRole(Client client, String code, String name, String description) {
        Role role = Role.builder()
                .client(client)
                .code(code)
                .name(name)
                .description(description)
                .isActive(true)
                .isSystem(true)
                .build();
        return roleRepository.save(role);
    }

    private void assignAllPermissions(Role role, List<Permission> permissions) {
        for (Permission permission : permissions) {
            rolePermissionRepository.save(RolePermission.builder()
                    .role(role)
                    .permission(permission)
                    .build());
        }
    }

    /**
     * OPERATOR용 Permission 부여
     * - 모든 리소스의 READ 허용
     * - MASTER 제외한 리소스의 UPDATE 허용 (현장 실행)
     */
    private void assignOperatorPermissions(Role role, List<Permission> allPermissions) {
        for (Permission permission : allPermissions) {
            Action action = permission.getAction();
            Resource resource = permission.getResource();

            if (action == Action.READ) {
                saveRolePermission(role, permission);
                continue;
            }
            if (action == Action.UPDATE && resource != Resource.MASTER) {
                saveRolePermission(role, permission);
            }
        }
    }

    private void saveRolePermission(Role role, Permission permission) {
        rolePermissionRepository.save(RolePermission.builder()
                .role(role)
                .permission(permission)
                .build());
    }

    /**
     * 회사 목록 조회 (활성 회사만)
     */
    @Transactional(readOnly = true)
    public List<ClientResDto> getClients() {
        List<Client> clients = companyRepository.findByIsActiveTrue();
        List<ClientResDto> result = new ArrayList<>();
        for (Client client : clients) {
            result.add(ClientResDto.fromEntity(client));
        }
        return result;
    }

    /**
     * 회사 상세 조회 (소속 사용자 포함)
     */
    @Transactional(readOnly = true)
    public ClientDetailResDto getClientDetail(UUID clientId) {
        Client client = companyRepository.findById(clientId)
                .orElseThrow(() -> new NoSuchElementException("회사를 찾을 수 없습니다."));

        List<User> users = userRepository.findByClientId(clientId);
        List<UserListResDto> userDtos = new ArrayList<>();
        for (User user : users) {
            userDtos.add(UserListResDto.fromEntity(user));
        }

        return ClientDetailResDto.fromEntity(client, userDtos);
    }

    /**
     * 회사 비활성화 (소속 사용자 전체 비활성화)
     */
    public void deactivateClient(UUID clientId) {
        Client client = companyRepository.findById(clientId)
                .orElseThrow(() -> new NoSuchElementException("회사를 찾을 수 없습니다."));

        client.deactivate();

        List<User> users = userRepository.findByClientId(clientId);
        for (User user : users) {
            user.deactivate();
        }
    }
}
