package com.beyond.wbs.product.service;

import com.beyond.wbs.code.CodeGenerator;
import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.domain.ProductCategory;
import com.beyond.wbs.product.domain.ProductGroup;
import com.beyond.wbs.product.dtos.ProductCreateDto;
import com.beyond.wbs.product.dtos.ProductListResDto;
import com.beyond.wbs.product.dtos.ProductSearchCondition;
import com.beyond.wbs.product.dtos.ProductUpdateDto;
import com.beyond.wbs.product.domain.ProductOptionValue;
import com.beyond.wbs.product.repository.ProductCategoryRepository;
import com.beyond.wbs.product.repository.ProductGroupRepository;
import com.beyond.wbs.product.repository.ProductOptionValueRepository;
import com.beyond.wbs.product.repository.ProductRepository;
import com.beyond.wbs.product.repository.ProductSpecifications;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductGroupRepository productGroupRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductOptionValueRepository productOptionValueRepository;
    private final ProductOptionService productOptionService;

    @Autowired
    public ProductService(ProductRepository productRepository,
                          ProductGroupRepository productGroupRepository,
                          ProductCategoryRepository productCategoryRepository,
                          ProductOptionValueRepository productOptionValueRepository,
                          ProductOptionService productOptionService) {
        this.productRepository = productRepository;
        this.productGroupRepository = productGroupRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.productOptionValueRepository = productOptionValueRepository;
        this.productOptionService = productOptionService;
    }

    /**
     * 상품 SKU 제안값 계산 — 상품그룹의 카테고리 기준.
     *
     * 관리자가 상품그룹 드롭다운 선택했을 때 프론트에서 호출.
     * 그룹 → 카테고리 → 카테고리코드를 가져와서 "{카테고리}-{순번}" 포맷으로 제안.
     * 같은 카테고리로 이미 등록된 SKU 개수 + 1 을 순번으로 사용.
     *
     * 결과는 "제안값"일 뿐이며 실제 저장 시점에 관리자가 수정 가능.
     * 동시성 충돌은 sku 유니크 제약으로 방지됨 (충돌 시 재입력 유도).
     */
    @Transactional(readOnly = true)
    public String suggestNextSku(UUID clientId, UUID productGroupId) {
        ProductGroup group = productGroupRepository.findById(productGroupId)
                .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."));
        ProductCategory category = group.getCategory();
        if (category == null) {
            throw new IllegalStateException("상품그룹에 카테고리가 지정되어 있지 않아 SKU 를 제안할 수 없습니다.");
        }
        String prefix = category.getCode() + "-";
        long count = productRepository.countByClientIdAndSkuStartingWith(clientId, prefix);
        return CodeGenerator.generateProductSku(category.getCode(), (int) (count + 1));
    }

    public UUID create(ProductCreateDto dto, UUID clientId) {
        ProductGroup productGroup = null;
        if (dto.getProductGroupId() != null) {
            productGroup = productGroupRepository.findById(dto.getProductGroupId())
                    .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."));
        }

        Product product = Product.builder()
                .clientId(clientId)
                .ownerType(dto.getOwnerType())
                .supplierId(dto.getSupplierId())
                .productGroup(productGroup)
                .sku(dto.getSku())
                .barcode(dto.getBarcode())
                .name(dto.getName())
                .nameEn(dto.getNameEn())
                .description(dto.getDescription())
                .unit(dto.getUnit())
                .unitPerBox(dto.getUnitPerBox())
                .standardPrice(dto.getStandardPrice())
                .weight(dto.getWeight())
                .width(dto.getWidth())
                .depth(dto.getDepth())
                .height(dto.getHeight())
                .build();

        Product saved = productRepository.save(product);

        // 옵션 매핑 — DTO 에 optionValueIds 가 있으면 같은 트랜잭션에서 ProductOption 생성
        productOptionService.assignToProduct(saved, dto.getOptionValueIds());

        return saved.getId();
    }

    @Transactional(readOnly = true)
    public Page<ProductListResDto> findAll(UUID clientId, Pageable pageable) {
        return productRepository.findByClientId(clientId, pageable)
                .map(ProductListResDto::from);
    }

    /**
     * client 단위 전체 상품 조회 (페이징 없음).
     * 사용처: 재고 부족 알림 등 "이 회사의 모든 상품을 검사" 해야 하는 내부 서비스.
     */
    @Transactional(readOnly = true)
    public List<ProductListResDto> findAllByClient(UUID clientId) {
        return productRepository.findAllByClientId(clientId).stream()
                .map(ProductListResDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 상품 키워드 검색 — 상품명 / SKU / 바코드에서 LIKE 매칭.
     * keyword 가 없으면 전체 조회와 동일.
     */
    @Transactional(readOnly = true)
    public Page<ProductListResDto> search(UUID clientId, String keyword, Pageable pageable) {
        return productRepository.searchByKeyword(clientId, keyword, pageable)
                .map(ProductListResDto::from);
    }

    /**
     * 멀티필터 동적 검색 — Specification 기반.
     * cond 의 모든 필드는 옵셔널. 비어있는 필드는 무시되고 제공된 항목만 AND 결합.
     */
    @Transactional(readOnly = true)
    public Page<ProductListResDto> searchAdvanced(UUID clientId,
                                                  ProductSearchCondition cond,
                                                  Pageable pageable) {
        // 카테고리 자손 펼침 — 루트 + 모든 자손 ID 수집 후 IN 매칭
        List<UUID> categoryIdsExpanded = null;
        if (cond != null && cond.getCategoryId() != null) {
            categoryIdsExpanded = collectCategoryAndDescendants(cond.getCategoryId());
        }

        // 옵션 값 ID 리스트 → 옵션 타입ID 별 그룹화 (패싯 시멘틱: 같은 타입 OR + 타입 간 AND)
        Map<UUID, List<UUID>> optionGroups = (cond == null) ? null
                : groupValuesByType(cond.getOptionValueIds());

        return productRepository.findAll(
                        ProductSpecifications.forCondition(clientId, cond, categoryIdsExpanded, optionGroups),
                        pageable)
                .map(ProductListResDto::from);
    }

    /**
     * 옵션 값 ID 들을 옵션 타입 ID 단위로 그룹화.
     * 패싯 검색 시멘틱(같은 타입 OR + 타입 간 AND) 구현을 위한 사전처리.
     */
    private Map<UUID, List<UUID>> groupValuesByType(List<UUID> valueIds) {
        if (valueIds == null || valueIds.isEmpty()) return Collections.emptyMap();
        List<ProductOptionValue> values = productOptionValueRepository.findAllById(valueIds);
        return values.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getOptionType().getId(),
                        Collectors.mapping(ProductOptionValue::getId, Collectors.toList())
                ));
    }

    /**
     * 카테고리 트리 BFS — 루트 본인 + 모든 자손 ID 수집.
     * 트리 깊이가 얕아 N+1 영향 미미. 깊이 증가 시 findByParentIdIn 으로 최적화 여지.
     */
    private List<UUID> collectCategoryAndDescendants(UUID rootId) {
        List<UUID> result = new ArrayList<>();
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            result.add(current);
            productCategoryRepository.findByParentId(current)
                    .forEach(child -> queue.add(child.getId()));
        }
        return result;
    }

    /**
     * 바코드 정확매칭 조회 — 스캐너 입력 등 단일 상품을 즉시 식별할 때 사용.
     * 결과 없으면 Optional.empty() 반환 (컨트롤러에서 404 처리).
     */
    @Transactional(readOnly = true)
    public Optional<ProductListResDto> findByBarcode(UUID clientId, String barcode) {
        return productRepository.findByClientIdAndBarcode(clientId, barcode)
                .map(ProductListResDto::from);
    }

    @Transactional(readOnly = true)
    public ProductListResDto findById(UUID id) {
        return ProductListResDto.from(
                productRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."))
        );
    }

    /**
     * 배치 상품 조회 — 여러 UUID 를 한 번에 받아서 한 번의 쿼리로 일괄 반환.
     *
     * 사용처: Stock 서비스의 층별 재고 요약 등 N+1 Feign 호출을 방지하기 위함.
     * 요구 사항상 비활성(isActive=false) 상품도 포함 — 과거 이력/재고 표시용 이름 해석에 필요.
     *
     * 입력 ids 가 null/empty 이면 빈 리스트 반환.
     * 응답은 입력 순서와 무관하며, 존재하지 않는 id 는 결과에서 빠진다 (부분 성공).
     */
    @Transactional(readOnly = true)
    public List<ProductListResDto> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return productRepository.findAllById(ids).stream()
                .map(ProductListResDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 상품 부분 수정 — null 필드는 변경하지 않음.
     */
    public ProductListResDto update(UUID id, ProductUpdateDto dto, UUID clientId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));
        if (!product.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        ProductGroup productGroup = null;
        if (dto.getProductGroupId() != null) {
            productGroup = productGroupRepository.findById(dto.getProductGroupId())
                    .orElseThrow(() -> new EntityNotFoundException("상품그룹을 찾을 수 없습니다."));
        }

        product.update(
                dto.getName(), dto.getNameEn(), dto.getDescription(), dto.getBarcode(),
                dto.getUnit(), dto.getUnitPerBox(), dto.getStandardPrice(),
                dto.getWeight(), dto.getWidth(), dto.getDepth(), dto.getHeight(),
                dto.getSupplierId(), productGroup
        );

        // 옵션 매핑 교체 — null 이면 미변경, 빈 리스트면 모두 제거
        productOptionService.replaceForProduct(product, dto.getOptionValueIds());

        return ProductListResDto.from(product);
    }

    public void deactivate(UUID id, UUID clientId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));
        if (!product.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        product.deactivate();
    }
}