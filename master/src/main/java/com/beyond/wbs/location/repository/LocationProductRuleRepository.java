package com.beyond.wbs.location.repository;

import com.beyond.wbs.location.domain.LocationProductRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LocationProductRuleRepository extends JpaRepository<LocationProductRule, UUID> {
    List<LocationProductRule> findByLocationId(UUID locationId);
    void deleteByLocationIdAndProductId(UUID locationId, UUID productId);
    void deleteByLocationIdAndProductGroupId(UUID locationId, UUID productGroupId);
}