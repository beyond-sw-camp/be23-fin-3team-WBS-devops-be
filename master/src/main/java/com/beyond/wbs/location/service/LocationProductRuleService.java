package com.beyond.wbs.location.service;

import com.beyond.wbs.location.domain.Location;
import com.beyond.wbs.location.domain.LocationProductRule;
import com.beyond.wbs.location.dtos.LocationProductRuleCreateDto;
import com.beyond.wbs.location.dtos.LocationProductRuleResDto;
import com.beyond.wbs.location.repository.LocationProductRuleRepository;
import com.beyond.wbs.location.repository.LocationRepository;
import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.domain.ProductGroup;
import com.beyond.wbs.product.repository.ProductGroupRepository;
import com.beyond.wbs.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationProductRuleService {

    private final LocationProductRuleRepository ruleRepository;
    private final LocationRepository locationRepository;
    private final ProductRepository productRepository;
    private final ProductGroupRepository productGroupRepository;

    @Autowired
    public LocationProductRuleService(LocationProductRuleRepository ruleRepository,
                                      LocationRepository locationRepository,
                                      ProductRepository productRepository,
                                      ProductGroupRepository productGroupRepository) {
        this.ruleRepository = ruleRepository;
        this.locationRepository = locationRepository;
        this.productRepository = productRepository;
        this.productGroupRepository = productGroupRepository;
    }

    public UUID create(LocationProductRuleCreateDto dto, UUID clientId) {
        // product_id, product_group_id 둘 다 없으면 예외
        if (dto.getProductId() == null && dto.getProductGroupId() == null) {
            throw new IllegalArgumentException("상품 또는 상품그룹 중 하나는 필수입니다.");
        }

        Location location = locationRepository.findById(dto.getLocationId())
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다."));

        // 소유권 검증
        if (!location.getRack().getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        Product product = null;
        if (dto.getProductId() != null) {
            product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));
        }

        ProductGroup productGroup = null;
        if (dto.getProductGroupId() != null) {
            productGroup = productGroupRepository.findById(dto.getProductGroupId())
                    .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."));
        }

        LocationProductRule rule = LocationProductRule.builder()
                .location(location)
                .product(product)
                .productGroup(productGroup)
                .createdBy(UUID.randomUUID()) // 실제로는 인증된 사용자 ID
                .build();

        return ruleRepository.save(rule).getId();
    }

    @Transactional(readOnly = true)
    public List<LocationProductRuleResDto> findByLocationId(UUID locationId) {
        return ruleRepository.findByLocationId(locationId)
                .stream()
                .map(LocationProductRuleResDto::from)
                .collect(Collectors.toList());
    }

    public void delete(UUID id, UUID clientId) {
        LocationProductRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("규칙을 찾을 수 없습니다."));
        if (!rule.getLocation().getRack().getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        ruleRepository.delete(rule);
    }
}
