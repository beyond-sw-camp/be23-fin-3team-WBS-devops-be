package com.beyond.wbs.account.repository;

import com.beyond.wbs.account.domain.Permission;
import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByResourceAndAction(Resource resource, Action action);
}
