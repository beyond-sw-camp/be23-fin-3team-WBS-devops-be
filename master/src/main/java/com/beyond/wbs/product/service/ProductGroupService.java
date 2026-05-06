package com.beyond.wbs.product.service;

import com.beyond.wbs.product.domain.ProductCategory;
import com.beyond.wbs.product.domain.ProductGroup;
import com.beyond.wbs.product.dtos.ProductGroupCreateDto;
import com.beyond.wbs.product.dtos.ProductGroupResDto;
import com.beyond.wbs.product.dtos.ProductGroupSuggestResDto;
import com.beyond.wbs.product.dtos.ProductGroupUpdateDto;
import com.beyond.wbs.product.repository.ProductCategoryRepository;
import com.beyond.wbs.product.repository.ProductGroupRepository;
import com.beyond.wbs.product.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ProductGroupService {

    private final ProductGroupRepository groupRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Autowired
    public ProductGroupService(ProductGroupRepository groupRepository,
                               ProductCategoryRepository categoryRepository,
                               ProductRepository productRepository) {
        this.groupRepository = groupRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public UUID create(ProductGroupCreateDto dto, UUID clientId) {
        ProductCategory category = resolveActiveCategory(dto.getCategoryId());

        ProductGroup group = ProductGroup.builder()
                .clientId(clientId)
                .category(category)
                .name(dto.getName())
                .code(dto.getCode())
                .brand(dto.getBrand())
                .description(dto.getDescription())
                .build();

        return groupRepository.save(group).getId();
    }

    public ProductGroupResDto update(UUID id, ProductGroupUpdateDto dto, UUID clientId) {
        ProductGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."));
        if (!group.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        ProductCategory newCategory = resolveActiveCategory(dto.getCategoryId());
        group.update(dto.getName(), dto.getBrand(), dto.getDescription(), newCategory);
        return ProductGroupResDto.from(group);
    }

    @Transactional(readOnly = true)
    public Page<ProductGroupResDto> findAll(UUID clientId, UUID categoryId, Pageable pageable) {
        if (categoryId != null) {
            return groupRepository.findByClientIdAndCategoryIdAndIsActiveTrue(clientId, categoryId, pageable)
                    .map(ProductGroupResDto::from);
        }
        return groupRepository.findByClientIdAndIsActiveTrue(clientId, pageable)
                .map(ProductGroupResDto::from);
    }

    @Transactional(readOnly = true)
    public ProductGroupResDto findById(UUID id) {
        return ProductGroupResDto.from(
                groupRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."))
        );
    }

    /**
     * 회사 단위 brand distinct 목록 — 상품 검색 UI 의 브랜드 드롭다운용.
     * 빈/null brand 는 제외, 가나다(알파벳) 정렬.
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctBrands(UUID clientId) {
        return groupRepository.findDistinctBrandsByClientId(clientId);
    }

    /**
     * 비활성화 (soft delete) — RESTRICT 정책.
     * 이 그룹을 참조하는 활성 상품이 남아있으면 거부한다.
     */
    public void deactivate(UUID id, UUID clientId) {
        ProductGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."));
        if (!group.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        if (productRepository.existsByProductGroup_IdAndIsActiveTrue(id)) {
            throw new IllegalStateException("활성 상태의 상품이 이 그룹에 속해 있어 비활성화할 수 없습니다.");
        }
        group.deactivate();
    }

    /**
     * 활성화 — 연결된 카테고리가 비활성이면 활성화할 수 없다 (참조 무결성).
     */
    public void activate(UUID id, UUID clientId) {
        ProductGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."));
        if (!group.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        if (group.getCategory() != null && Boolean.FALSE.equals(group.getCategory().getIsActive())) {
            throw new IllegalStateException("연결된 카테고리가 비활성 상태라 활성화할 수 없습니다.");
        }
        group.activate();
    }

    /**
     * 상품 그룹 자동 제안.
     *
     * 프론트의 [자동 제안] 버튼에서 호출. 선택한 카테고리 기준으로:
     *  - suggestedCode: "{카테고리코드}-GRP-{3자리}" 형식 (기존 그룹 수 + 1)
     *  - existingGroups: 동일 카테고리의 활성 그룹 목록 (네이밍 규칙 참고용)
     *
     * 제안값은 힌트일 뿐 관리자가 의미있는 값(예: FHD_MONITOR_SERIES)으로 수정하는 게 권장됨.
     */
    @Transactional(readOnly = true)
    public ProductGroupSuggestResDto suggestNextGroupInfo(UUID clientId, UUID categoryId) {
        ProductCategory category = resolveActiveCategory(categoryId);
        if (category == null) {
            throw new EntityNotFoundException("카테고리를 찾을 수 없습니다.");
        }

        List<ProductGroup> existing = groupRepository
                .findByClientIdAndCategory_IdAndIsActiveTrue(clientId, categoryId);

        String suggestedCode = String.format("%s-GRP-%03d", category.getCode(), existing.size() + 1);

        List<ProductGroupSuggestResDto.ExistingGroup> existingGroups = existing.stream()
                .map(g -> ProductGroupSuggestResDto.ExistingGroup.builder()
                        .name(g.getName())
                        .code(g.getCode())
                        .build())
                .toList();

        return ProductGroupSuggestResDto.builder()
                .suggestedCode(suggestedCode)
                .existingGroups(existingGroups)
                .build();
    }

    /**
     * categoryId 가 null 이면 null 반환, 존재하지 않으면 404, 비활성이면 거부.
     */
    private ProductCategory resolveActiveCategory(UUID categoryId) {
        if (categoryId == null) return null;
        ProductCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."));
        if (Boolean.FALSE.equals(category.getIsActive())) {
            throw new IllegalStateException("비활성 상태의 카테고리는 사용할 수 없습니다.");
        }
        return category;
    }
}
