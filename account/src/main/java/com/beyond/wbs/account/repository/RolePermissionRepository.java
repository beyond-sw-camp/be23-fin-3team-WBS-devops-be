package com.beyond.wbs.account.repository;

import com.beyond.wbs.account.domain.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {
    @Query("SELECT rp FROM RolePermission rp JOIN FETCH rp.permission WHERE rp.role.id = :roleId")
    List<RolePermission> findByRoleIdWithPermission(@Param("roleId") UUID roleId);

    void deleteByRoleId(UUID roleId);
}
