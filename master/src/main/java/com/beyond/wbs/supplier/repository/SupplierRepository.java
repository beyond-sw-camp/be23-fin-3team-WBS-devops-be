package com.beyond.wbs.supplier.repository;

import com.beyond.wbs.supplier.domain.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    Page<Supplier> findByClientId(UUID clientId, Pageable pageable);
}
