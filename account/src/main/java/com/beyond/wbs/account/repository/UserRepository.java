package com.beyond.wbs.account.repository;

import com.beyond.wbs.account.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByLoginId(String loginId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.client WHERE u.loginId = :loginId")
    Optional<User> findByLoginIdWithRole(@Param("loginId") String loginId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.client WHERE u.loginId = :loginId")
    Optional<User> findByLoginIdWithClient(@Param("loginId") String loginId);

    List<User> findByClientId(UUID clientId);
    List<User> findByClientIdAndIsActiveTrue(UUID clientId);

    boolean existsByRoleId(UUID roleId);
}
