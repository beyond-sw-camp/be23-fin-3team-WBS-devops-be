package com.beyond.wbs.supplier.repository;

import com.beyond.wbs.supplier.domain.SupplierProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupplierProductRepository extends JpaRepository<SupplierProduct, UUID> {
    List<SupplierProduct> findBySupplierId(UUID supplierId);
    List<SupplierProduct> findByProductId(UUID productId);
}
