package com.beyond.wbs.product.service;

import com.beyond.wbs.product.domain.ProductOptionType;
import com.beyond.wbs.product.dtos.ProductOptionTypeCreateDto;
import com.beyond.wbs.product.dtos.ProductOptionTypeResDto;
import com.beyond.wbs.product.repository.ProductOptionTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductOptionTypeService {

    private final ProductOptionTypeRepository repository;

    public ProductOptionTypeService(ProductOptionTypeRepository repository) {
        this.repository = repository;
    }

    /**
     * 옵션 타입 등록 — 회사(client) 단위로 격리.
     * code 는 회사 내 중복 불가.
     */
    public UUID create(ProductOptionTypeCreateDto dto, UUID clientId) {
        if (repository.existsByClientIdAndCode(clientId, dto.getCode())) {
            throw new IllegalArgumentException("이미 등록된 옵션 타입 코드입니다: " + dto.getCode());
        }
        ProductOptionType saved = repository.save(
                ProductOptionType.builder()
                        .clientId(clientId)
                        .name(dto.getName())
                        .code(dto.getCode())
                        .build()
        );
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<ProductOptionTypeResDto> findAll(UUID clientId) {
        return repository.findByClientId(clientId).stream()
                .map(ProductOptionTypeResDto::from)
                .collect(Collectors.toList());
    }
}
