package com.beyond.wbs.product.service;

import com.beyond.wbs.product.domain.ProductCategory;
import com.beyond.wbs.product.dtos.ProductCategoryCreateDto;
import com.beyond.wbs.product.dtos.ProductCategoryResDto;
import com.beyond.wbs.product.dtos.ProductCategoryUpdateDto;
import com.beyond.wbs.product.repository.ProductCategoryRepository;
import com.beyond.wbs.product.repository.ProductGroupRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductGroupRepository groupRepository;

    @Autowired
    public ProductCategoryService(ProductCategoryRepository categoryRepository,
                                  ProductGroupRepository groupRepository) {
        this.categoryRepository = categoryRepository;
        this.groupRepository = groupRepository;
    }

    public UUID create(ProductCategoryCreateDto dto, UUID clientId) {
        ProductCategory parent = null;
        int depth = 0;

        if (dto.getParentId() != null) {
            parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("상위 카테고리를 찾을 수 없습니다."));
            // 비활성 parent 아래로는 신규 생성 불가
            if (Boolean.FALSE.equals(parent.getIsActive())) {
                throw new IllegalStateException("비활성 상태의 상위 카테고리 아래에는 새 카테고리를 만들 수 없습니다.");
            }
            depth = parent.getDepth() + 1;
            if (depth > 2) {
                throw new IllegalArgumentException("카테고리는 최대 3단계까지만 생성할 수 있습니다.");
            }
        }

        ProductCategory category = ProductCategory.builder()
                .clientId(clientId)
                .parent(parent)
                .name(dto.getName())
                .code(dto.getCode())
                .depth(depth)
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .build();

        return categoryRepository.save(category).getId();
    }

    public ProductCategoryResDto update(UUID id, ProductCategoryUpdateDto dto, UUID clientId) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."));
        if (!category.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        category.update(dto.getName(), dto.getSortOrder());
        return ProductCategoryResDto.from(category);
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryResDto> findRoots(UUID clientId) {
        return categoryRepository.findByClientIdAndParentIsNullAndIsActiveTrue(clientId)
                .stream()
                .map(ProductCategoryResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductCategoryResDto> findChildren(UUID parentId) {
        return categoryRepository.findByParentIdAndIsActiveTrue(parentId)
                .stream()
                .map(ProductCategoryResDto::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductCategoryResDto findById(UUID id) {
        return ProductCategoryResDto.from(
                categoryRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."))
        );
    }

    /**
     * 비활성화 (soft delete) — RESTRICT 정책.
     * 활성 하위 카테고리나 활성 상품 그룹이 남아있으면 거부한다.
     */
    public void deactivate(UUID id, UUID clientId) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."));
        if (!category.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        if (categoryRepository.existsByParentIdAndIsActiveTrue(id)) {
            throw new IllegalStateException("활성 상태의 하위 카테고리가 있어 비활성화할 수 없습니다. 하위 카테고리를 먼저 비활성화해주세요.");
        }
        if (groupRepository.existsByCategoryIdAndIsActiveTrue(id)) {
            throw new IllegalStateException("활성 상태의 상품 그룹이 이 카테고리를 참조하고 있어 비활성화할 수 없습니다.");
        }

        category.deactivate();
    }

    /**
     * 활성화 — 비활성 parent 아래로는 활성화할 수 없다 (트리 단절 방지).
     */
    public void activate(UUID id, UUID clientId) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."));
        if (!category.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        if (category.getParent() != null && Boolean.FALSE.equals(category.getParent().getIsActive())) {
            throw new IllegalStateException("상위 카테고리가 비활성 상태라 활성화할 수 없습니다. 상위를 먼저 활성화해주세요.");
        }
        category.activate();
    }
}
