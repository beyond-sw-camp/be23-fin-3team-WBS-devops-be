package com.beyond.wbs.account.repository;

import com.beyond.wbs.account.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Client, UUID> {
    List<Client> findByIsActiveTrue();
}
