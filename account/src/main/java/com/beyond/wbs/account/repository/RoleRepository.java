package com.beyond.wbs.account.repository;

import com.beyond.wbs.account.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByClientIdAndCode(UUID clientId, String code);
    List<Role> findByClientId(UUID clientId);
}
