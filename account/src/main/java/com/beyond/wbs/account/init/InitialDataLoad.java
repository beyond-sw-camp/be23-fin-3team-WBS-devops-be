package com.beyond.wbs.account.init;

import com.beyond.wbs.account.domain.*;
import com.beyond.wbs.account.repository.*;
import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 시스템 초기 데이터 로드
 * - Permission 시드 (전역 고정)
 * - Developer 계정 생성
 * - 더미 Client(테스트회사) + 표준 Role 3종(ADMIN/MANAGER/OPERATOR) + 사용자 21명
 *
 * client_id / 사용자 UUID는 master·stock 의 data.sql 시드와 정합.
 */
@Component
@ConditionalOnProperty(name = "app.seed.initial-data-load.enabled", havingValue = "true", matchIfMissing = true)
@Transactional
public class InitialDataLoad implements CommandLineRunner {

    // master / stock data.sql 과 동일한 client_id
    private static final UUID DUMMY_CLIENT_ID =
            UUID.fromString("01935c00-0000-7000-8000-000000000001");
    private static final UUID SY_CLIENT_ID =
            UUID.fromString("01935c00-0000-7000-8000-000000000201");

    // 사용자 고정 UUID — stock data.sql 의 created_by/approved_by/assigned_to 에서 참조
    private static final UUID USER_ADMIN1    = UUID.fromString("01935c00-0000-8000-8000-000000000001");
    private static final UUID USER_ADMIN2    = UUID.fromString("01935c00-0000-8000-8000-000000000021");
    private static final UUID USER_MANAGER6  = UUID.fromString("01935c00-0000-8000-8000-000000000022");
    private static final UUID USER_OPERATOR16 = UUID.fromString("01935c00-0000-8000-8000-000000000023");
    private static final UUID USER_MANAGER1  = UUID.fromString("01935c00-0000-8000-8000-000000000002");
    private static final UUID USER_MANAGER2  = UUID.fromString("01935c00-0000-8000-8000-000000000003");
    private static final UUID USER_MANAGER3  = UUID.fromString("01935c00-0000-8000-8000-000000000004");
    private static final UUID USER_OPERATOR2 = UUID.fromString("01935c00-0000-8000-8000-000000000011");

    // 자동 배당 임시 구현체가 참조하는 고정 사용자 UUID (operator1)
    public static final UUID FIXED_OPERATOR1_ID =
            UUID.fromString("01935c00-0000-7200-8000-000000000001");

    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PasswordEncoder passwordEncoder;

    public InitialDataLoad(PermissionRepository permissionRepository,
                           UserRepository userRepository,
                           CompanyRepository companyRepository,
                           RoleRepository roleRepository,
                           RolePermissionRepository rolePermissionRepository,
                           PasswordEncoder passwordEncoder) {
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 1. Permission 시드 데이터
        seedPermissions();

        // 2. Developer 계정 생성
        if (userRepository.findByLoginId("developer").isEmpty()) {
            userRepository.save(User.builder()
                    .name("Developer")
                    .loginId("developer")
                    .password(passwordEncoder.encode("12341234"))
                    .isDeveloper(true)
                    .build());
        }

        // 3. 더미 Client + Role 3종 + 사용자 21명 생성
        seedDummyClient();

        // 4. 발표용 Client + admin2 계정 생성
        seedPresentationClient();
    }

    private void seedPermissions() {
        // INBOUND
        savePermission(Resource.INBOUND, Action.CREATE, "입고 지시서 생성");
        savePermission(Resource.INBOUND, Action.READ, "입고 조회");
        savePermission(Resource.INBOUND, Action.UPDATE, "입고 수정/실행");
        savePermission(Resource.INBOUND, Action.DELETE, "입고 삭제");
        savePermission(Resource.INBOUND, Action.APPROVE, "입고 지시서 승인");

        // OUTBOUND
        savePermission(Resource.OUTBOUND, Action.CREATE, "출고 지시서 생성");
        savePermission(Resource.OUTBOUND, Action.READ, "출고 조회");
        savePermission(Resource.OUTBOUND, Action.UPDATE, "출고 수정/실행");
        savePermission(Resource.OUTBOUND, Action.DELETE, "출고 삭제");
        savePermission(Resource.OUTBOUND, Action.APPROVE, "출고 지시서 승인");

        // TRANSFER
        savePermission(Resource.TRANSFER, Action.CREATE, "이동 지시서 생성");
        savePermission(Resource.TRANSFER, Action.READ, "이동 조회");
        savePermission(Resource.TRANSFER, Action.UPDATE, "이동 수정/실행");
        savePermission(Resource.TRANSFER, Action.DELETE, "이동 삭제");
        savePermission(Resource.TRANSFER, Action.APPROVE, "이동 지시서 승인");

        // INVENTORY
        savePermission(Resource.INVENTORY, Action.READ, "재고 조회");
        savePermission(Resource.INVENTORY, Action.UPDATE, "재고 조정");

        // STOCK_COUNT
        savePermission(Resource.STOCK_COUNT, Action.CREATE, "실사 지시서 생성");
        savePermission(Resource.STOCK_COUNT, Action.READ, "실사 조회");
        savePermission(Resource.STOCK_COUNT, Action.UPDATE, "실사 수정/실행");
        savePermission(Resource.STOCK_COUNT, Action.DELETE, "실사 삭제");
        savePermission(Resource.STOCK_COUNT, Action.APPROVE, "실사 지시서 승인");

        // ETC_INOUT
        savePermission(Resource.ETC_INOUT, Action.CREATE, "기타입출고 생성");
        savePermission(Resource.ETC_INOUT, Action.READ, "기타입출고 조회");
        savePermission(Resource.ETC_INOUT, Action.UPDATE, "기타입출고 수정/실행");
        savePermission(Resource.ETC_INOUT, Action.DELETE, "기타입출고 삭제");

        // MASTER
        savePermission(Resource.MASTER, Action.CREATE, "마스터 생성");
        savePermission(Resource.MASTER, Action.READ, "마스터 조회");
        savePermission(Resource.MASTER, Action.UPDATE, "마스터 수정");
        savePermission(Resource.MASTER, Action.DELETE, "마스터 삭제");

        // STATISTICS
        savePermission(Resource.STATISTICS, Action.READ, "통계 조회");
    }

    /**
     * 더미 회사(테스트회사) + 표준 Role 3종(ADMIN/MANAGER/OPERATOR) + 사용자 21명 생성.
     * client_id 및 주요 사용자 UUID는 master / stock 의 data.sql 과 동일.
     */
    private void seedDummyClient() {
        // 1. Client(회사) 생성 또는 재사용
        Client client = companyRepository.findById(DUMMY_CLIENT_ID)
                .orElseGet(() -> companyRepository.saveAndFlush(Client.builder()
                        .id(DUMMY_CLIENT_ID)
                        .name("테스트회사")
                        .bizNo("123-45-67890")
                        .build()));

        List<Permission> allPermissions = permissionRepository.findAll();

        // 2. 표준 Role 3종 — DeveloperService.createCompanyWithAdmin과 동일한 코드/구조
        Role adminRole    = ensureSystemRole(client, "ADMIN",    "관리자",     "회사 관리자");
        Role managerRole  = ensureSystemRole(client, "MANAGER",  "매니저",     "지시서 생성/승인/실행");
        Role operatorRole = ensureSystemRole(client, "OPERATOR", "오퍼레이터", "현장 실행/조회");

        // 3. Permission 매핑 — 비어있을 때만 채움 (idempotent)
        if (rolePermissionRepository.findByRoleIdWithPermission(adminRole.getId()).isEmpty()) {
            assignAllPermissions(adminRole, allPermissions);
        }
        if (rolePermissionRepository.findByRoleIdWithPermission(managerRole.getId()).isEmpty()) {
            assignAllPermissions(managerRole, allPermissions);
        }
        if (rolePermissionRepository.findByRoleIdWithPermission(operatorRole.getId()).isEmpty()) {
            assignOperatorPermissions(operatorRole, allPermissions);
        }

        // 4. 사용자 21명 생성 (주요 사용자는 고정 UUID — stock data.sql 참조용)
        createDummyUser(client, adminRole,    "관리자",       "admin1",    "admin@test.com",    USER_ADMIN1);

        createDummyUser(client, managerRole,  "김매니저",     "manager1",  "manager1@test.com", USER_MANAGER1);
        createDummyUser(client, managerRole,  "박입고",       "manager2",  "manager2@test.com", USER_MANAGER2);
        createDummyUser(client, managerRole,  "최출고",       "manager3",  "manager3@test.com", USER_MANAGER3);
        createDummyUser(client, managerRole,  "정재고",       "manager4",  "manager4@test.com");
        createDummyUser(client, managerRole,  "한검수",       "manager5",  "manager5@test.com");

        createDummyUser(client, operatorRole, "이오퍼레이터", "operator1", "op1@test.com",      FIXED_OPERATOR1_ID);
        createDummyUser(client, operatorRole, "강현장",       "operator2", "op2@test.com",      USER_OPERATOR2);
        createDummyUser(client, operatorRole, "윤피킹",       "operator3", "op3@test.com");
        createDummyUser(client, operatorRole, "임적치",       "operator4", "op4@test.com");
        createDummyUser(client, operatorRole, "조입고",       "operator5", "op5@test.com");
        createDummyUser(client, operatorRole, "배출고",       "operator6", "op6@test.com");
        createDummyUser(client, operatorRole, "신포장",       "operator7", "op7@test.com");
        createDummyUser(client, operatorRole, "류검수",       "operator8", "op8@test.com");

        createDummyUser(client, operatorRole, "한현장",       "operator9", "op9@test.com");
        createDummyUser(client, operatorRole, "오피킹",       "operator10", "op10@test.com");
        createDummyUser(client, operatorRole, "문적치",       "operator11", "op11@test.com");
        createDummyUser(client, operatorRole, "장입고",       "operator12", "op12@test.com");
        createDummyUser(client, operatorRole, "권출고",       "operator13", "op13@test.com");

        createDummyUser(client, operatorRole, "송검수",       "operator14", "op14@test.com");
        createDummyUser(client, operatorRole, "노반품",       "operator15", "op15@test.com");
    }

    /**
     * 발표용 회사 + 관리자 계정(admin2).
     *
     * 발표 시 기존 테스트회사(admin1)와 분리해서 계정/데이터를 전달하기 위한 전용 시드.
     * master / stock 의 data_SY.sql 과 동일한 client_id 를 사용한다.
     */
    private void seedPresentationClient() {
        Client client = companyRepository.findById(SY_CLIENT_ID)
                .orElseGet(() -> companyRepository.saveAndFlush(Client.builder()
                        .id(SY_CLIENT_ID)
                        .name("SY 발표회사")
                        .bizNo("987-65-43210")
                        .build()));

        List<Permission> allPermissions = permissionRepository.findAll();

        Role adminRole = ensureSystemRole(client, "ADMIN", "관리자", "회사 관리자");
        Role managerRole = ensureSystemRole(client, "MANAGER", "매니저", "지시서 생성/승인/실행");
        Role operatorRole = ensureSystemRole(client, "OPERATOR", "오퍼레이터", "현장 실행/조회");

        if (rolePermissionRepository.findByRoleIdWithPermission(adminRole.getId()).isEmpty()) {
            assignAllPermissions(adminRole, allPermissions);
        }
        if (rolePermissionRepository.findByRoleIdWithPermission(managerRole.getId()).isEmpty()) {
            assignAllPermissions(managerRole, allPermissions);
        }
        if (rolePermissionRepository.findByRoleIdWithPermission(operatorRole.getId()).isEmpty()) {
            assignOperatorPermissions(operatorRole, allPermissions);
        }

        createDummyUser(client, adminRole, "발표관리자", "admin2", "admin2@sy-demo.com", USER_ADMIN2);
        createDummyUser(client, managerRole, "발표매니저", "manager6", "manager6@sy-demo.com", USER_MANAGER6);
        createDummyUser(client, operatorRole, "발표오퍼레이터", "operator16", "operator16@sy-demo.com", USER_OPERATOR16);
    }

    /**
     * (client, code) 기준으로 Role 조회, 없으면 신규 생성.
     */
    private Role ensureSystemRole(Client client, String code, String name, String description) {
        return roleRepository.findByClientIdAndCode(client.getId(), code)
                .orElseGet(() -> createSystemRole(client, code, name, description));
    }

    private Role createSystemRole(Client client, String code, String name, String description) {
        return roleRepository.save(Role.builder()
                .client(client)
                .code(code)
                .name(name)
                .description(description)
                .isActive(true)
                .isSystem(true)
                .build());
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
     * OPERATOR용 Permission 부여 — DeveloperService.assignOperatorPermissions와 동일 로직.
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

    private void createDummyUser(Client client, Role role, String name, String loginId, String email) {
        createDummyUser(client, role, name, loginId, email, null);
    }

    private void createDummyUser(Client client, Role role, String name, String loginId, String email, UUID fixedId) {
        if (userRepository.findByLoginId(loginId).isPresent()) {
            return;
        }
        userRepository.save(User.builder()
                .id(fixedId)
                .client(client)
                .role(role)
                .name(name)
                .loginId(loginId)
                .email(email)
                .password(passwordEncoder.encode("12341234"))
                .build());
    }

    private void savePermission(Resource resource, Action action, String description) {
        permissionRepository.findByResourceAndAction(resource, action)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder()
                                .resource(resource)
                                .action(action)
                                .description(description)
                                .build()
                ));
    }
}
