package com.beyond.wbs.store.repository;

import com.beyond.wbs.store.domain.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    Page<Store> findByClientId(UUID clientId, Pageable pageable);
}
