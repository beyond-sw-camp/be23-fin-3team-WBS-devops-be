package com.beyond.wbs.product.repository;

import com.beyond.wbs.product.domain.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductOptionRepository extends JpaRepository<ProductOption, UUID> {
    List<ProductOption> findByProductId(UUID productId);

    void deleteByProductId(UUID productId);
}
