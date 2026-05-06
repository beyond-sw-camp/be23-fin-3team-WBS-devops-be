package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, UUID> {
    List<ProductOptionValue> findByOptionTypeId(UUID optionTypeId);

    boolean existsByOptionType_IdAndCode(UUID optionTypeId, String code);
}
