package com.beyond.wbs.product.service;

import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.domain.ProductOption;
import com.beyond.wbs.product.domain.ProductOptionValue;
import com.beyond.wbs.product.dtos.ProductOptionResDto;
import com.beyond.wbs.product.repository.ProductOptionRepository;
import com.beyond.wbs.product.repository.ProductOptionValueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 상품-옵션 매핑 서비스.
 *
 * Product 등록/수정 시 ProductService 에서 호출.
 * 옵션 값 ID 리스트를 받아 ProductOption row 들을 생성하거나 교체한다.
 */
@Service
@Transactional
public class ProductOptionService {

    private final ProductOptionRepository optionRepository;
    private final ProductOptionValueRepository valueRepository;

    public ProductOptionService(ProductOptionRepository optionRepository,
                                ProductOptionValueRepository valueRepository) {
        this.optionRepository = optionRepository;
        this.valueRepository = valueRepository;
    }

    /**
     * 상품에 옵션 매핑 신규 생성.
     * valueIds 가 null/empty 면 아무 작업도 하지 않는다.
     */
    public void assignToProduct(Product product, List<UUID> valueIds) {
        if (valueIds == null || valueIds.isEmpty()) return;

        List<ProductOptionValue> values = valueRepository.findAllById(valueIds);
        if (values.size() != valueIds.size()) {
            throw new EntityNotFoundException("일부 옵션 값을 찾을 수 없습니다.");
        }

        for (ProductOptionValue value : values) {
            optionRepository.save(
                    ProductOption.builder()
                            .product(product)
                            .optionType(value.getOptionType())
                            .optionValue(value)
                            .build()
            );
        }
    }

    /**
     * 상품의 옵션 매핑을 valueIds 로 교체.
     * 기존 매핑 모두 삭제 후 새로 생성.
     * valueIds 가 null 이면 변경하지 않는다 (부분 수정 의미).
     * valueIds 가 빈 리스트면 모두 제거.
     */
    public void replaceForProduct(Product product, List<UUID> valueIds) {
        if (valueIds == null) return;

        optionRepository.deleteByProductId(product.getId());
        if (!valueIds.isEmpty()) {
            assignToProduct(product, valueIds);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductOptionResDto> findByProductId(UUID productId) {
        return optionRepository.findByProductId(productId).stream()
                .map(ProductOptionResDto::from)
                .collect(Collectors.toList());
    }
}
