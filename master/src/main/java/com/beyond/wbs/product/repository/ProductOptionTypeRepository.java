package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.ProductOptionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductOptionTypeRepository extends JpaRepository<ProductOptionType, UUID> {
    List<ProductOptionType> findByClientId(UUID clientId);

    boolean existsByClientIdAndCode(UUID clientId, String code);
}
