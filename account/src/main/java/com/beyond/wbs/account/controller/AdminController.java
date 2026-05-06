package com.beyond.wbs.account.controller;

import com.beyond.wbs.account.dtos.*;
import com.beyond.wbs.account.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

/**
 * 회사 admin 전용 API
 * Gateway에서 ADMIN role 체크로 접근 제어
 */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ========== User 관리 ==========

    @AuditLog
    @PostMapping("/users")
    public ResponseEntity<?> createUser(
            @RequestBody @Valid UserCreateReqDto dto,
            @RequestHeader("X-User-Id") String adminId) {
        UUID userId = adminService.createUser(dto, UUID.fromString(adminId));
        return ResponseEntity.status(HttpStatus.CREATED).body(userId);
    }

    @AuditLog
    @GetMapping("/users")
    public ResponseEntity<?> findUsers(@RequestHeader("X-User-Id") String adminId) {
        List<UserListResDto> users = adminService.findUsersByCompany(UUID.fromString(adminId));
        return ResponseEntity.ok(users);
    }

    @AuditLog
    @GetMapping("/users/{userId}")
    public ResponseEntity<?> findUserDetail(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") String adminId) {
        UserDetailResDto dto = adminService.findUserDetail(userId, UUID.fromString(adminId));
        return ResponseEntity.ok(dto);
    }

    @AuditLog
    @PatchMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(
            @PathVariable UUID userId,
            @RequestBody UserUpdateReqDto dto,
            @RequestHeader("X-User-Id") String adminId) {
        adminService.updateUser(userId, dto, UUID.fromString(adminId));
        return ResponseEntity.ok().build();
    }

    @AuditLog(action = "비활성화")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deactivateUser(
            @PathVariable UUID userId,
            @RequestHeader("X-User-Id") String adminId) {
        adminService.deactivateUser(userId, UUID.fromString(adminId));
        return ResponseEntity.ok().build();
    }

    // ========== Role 관리 ==========

    @AuditLog
    @GetMapping("/roles")
    public ResponseEntity<?> findRoles(@RequestHeader("X-User-Id") String adminId) {
        List<RoleResDto> roles = adminService.findRoles(UUID.fromString(adminId));
        return ResponseEntity.ok(roles);
    }

    @AuditLog
    @GetMapping("/roles/{roleId}")
    public ResponseEntity<?> findRole(
            @PathVariable UUID roleId,
            @RequestHeader("X-User-Id") String adminId) {
        RoleResDto role = adminService.findRole(roleId, UUID.fromString(adminId));
        return ResponseEntity.ok(role);
    }

    @AuditLog
    @PostMapping("/roles")
    public ResponseEntity<?> createRole(
            @RequestBody @Valid RoleCreateReqDto dto,
            @RequestHeader("X-User-Id") String adminId) {
        UUID roleId = adminService.createRole(dto, UUID.fromString(adminId));
        return ResponseEntity.status(HttpStatus.CREATED).body(roleId);
    }

    @AuditLog
    @PatchMapping("/roles/{roleId}")
    public ResponseEntity<?> updateRole(
            @PathVariable UUID roleId,
            @RequestBody RoleUpdateReqDto dto,
            @RequestHeader("X-User-Id") String adminId) {
        adminService.updateRole(roleId, dto, UUID.fromString(adminId));
        return ResponseEntity.ok().build();
    }

    @AuditLog
    @DeleteMapping("/roles/{roleId}")
    public ResponseEntity<?> deleteRole(
            @PathVariable UUID roleId,
            @RequestHeader("X-User-Id") String adminId) {
        adminService.deleteRole(roleId, UUID.fromString(adminId));
        return ResponseEntity.ok().build();
    }

    // ========== Permission 조회 ==========

    @AuditLog
    @GetMapping("/permissions")
    public ResponseEntity<?> findAllPermissions() {
        List<PermissionResDto> permissions = adminService.findAllPermissions();
        return ResponseEntity.ok(permissions);
    }
}
