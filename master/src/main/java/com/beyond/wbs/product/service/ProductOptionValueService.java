package com.beyond.wbs.product.service;

import com.beyond.wbs.product.domain.ProductOptionType;
import com.beyond.wbs.product.domain.ProductOptionValue;
import com.beyond.wbs.product.dtos.ProductOptionValueCreateDto;
import com.beyond.wbs.product.dtos.ProductOptionValueResDto;
import com.beyond.wbs.product.repository.ProductOptionTypeRepository;
import com.beyond.wbs.product.repository.ProductOptionValueRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductOptionValueService {

    private final ProductOptionValueRepository valueRepository;
    private final ProductOptionTypeRepository typeRepository;

    public ProductOptionValueService(ProductOptionValueRepository valueRepository,
                                     ProductOptionTypeRepository typeRepository) {
        this.valueRepository = valueRepository;
        this.typeRepository = typeRepository;
    }

    /**
     * 옵션 값 등록 — 옵션 타입 존재 확인 + clientId 일치 검증 + code 중복 방지.
     * code 는 같은 옵션 타입 내에서만 unique (다른 타입에서는 같은 코드 가능).
     */
    public UUID create(ProductOptionValueCreateDto dto, UUID clientId) {
        ProductOptionType type = typeRepository.findById(dto.getOptionTypeId())
                .orElseThrow(() -> new EntityNotFoundException("옵션 타입을 찾을 수 없습니다."));

        if (!type.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("다른 회사의 옵션 타입에는 값을 등록할 수 없습니다.");
        }

        if (valueRepository.existsByOptionType_IdAndCode(type.getId(), dto.getCode())) {
            throw new IllegalArgumentException("이미 등록된 옵션 값 코드입니다: " + dto.getCode());
        }

        ProductOptionValue saved = valueRepository.save(
                ProductOptionValue.builder()
                        .optionType(type)
                        .value(dto.getValue())
                        .code(dto.getCode())
                        .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                        .build()
        );
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<ProductOptionValueResDto> findByOptionTypeId(UUID optionTypeId) {
        return valueRepository.findByOptionTypeId(optionTypeId).stream()
                .map(ProductOptionValueResDto::from)
                .collect(Collectors.toList());
    }
}
