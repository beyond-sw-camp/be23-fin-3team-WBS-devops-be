package com.beyond.wbs.zone.service;

import com.beyond.wbs.code.CodeGenerator;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.product.domain.ProductCategory;
import com.beyond.wbs.product.repository.ProductCategoryRepository;
import com.beyond.wbs.warehouse.domain.Warehouse;
import com.beyond.wbs.warehouse.repository.WarehouseRepository;
import com.beyond.wbs.zone.domain.Zone;
import com.beyond.wbs.zone.dtos.ZoneCreateDto;
import com.beyond.wbs.zone.dtos.ZoneListResDto;
import com.beyond.wbs.zone.dtos.ZoneUpdateDto;
import com.beyond.wbs.zone.repository.ZoneRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ZoneService {

    private final ZoneRepository zoneRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductCategoryRepository categoryRepository;
    private final NumberingUtil numberingUtil;

    @Autowired
    public ZoneService(ZoneRepository zoneRepository,
                       WarehouseRepository warehouseRepository,
                       ProductCategoryRepository categoryRepository,
                       NumberingUtil numberingUtil) {
        this.zoneRepository = zoneRepository;
        this.warehouseRepository = warehouseRepository;
        this.categoryRepository = categoryRepository;
        this.numberingUtil = numberingUtil;
    }

    public UUID create(ZoneCreateDto dto, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(dto.getWarehouseId())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));

        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("해당 창고에 대한 접근 권한이 없습니다.");
        }

        // ✅ depth 자동계산 수정 (항상 1이 아닌 부모 depth + 1)
        Zone parent = null;
        int depth = 0;
        if (dto.getParentId() != null) {
            parent = zoneRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("상위 구역을 찾을 수 없습니다."));
            depth = parent.getDepth() + 1;
            if (depth > 2) {
                throw new IllegalArgumentException("구역은 최대 3단계까지만 생성할 수 있습니다.");
            }
        }

        // ✅ category 조회 추가
        ProductCategory category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."));
        }

        // 구역 코드 자동 생성: ZN-{지역코드}-{카테고리코드}-{순번}
        String regionCode = CodeGenerator.parseRegionFromWarehouseCode(warehouse.getCode());
        String categoryCode = category != null ? category.getCode() : "GEN";
        String code = CodeGenerator.generateZoneCode(regionCode, categoryCode, numberingUtil.nextSeq("zone"));

        Zone zone = Zone.builder()
                .warehouse(warehouse)
                .parent(parent)
                .partnerId(dto.getPartnerId())
                .depth(depth)
                .name(dto.getName())
                .code(code)
                .zoneType(dto.getZoneType())
                .category(category) // ✅ 추가
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .isActive(true)
                .build();

        return zoneRepository.save(zone).getId();
    }

    @Transactional(readOnly = true)
    public Page<ZoneListResDto> findAll(UUID warehouseId, Pageable pageable) {
        return zoneRepository.findByWarehouseId(warehouseId, pageable)
                .map(z -> ZoneListResDto.from(z, (int) zoneRepository.countActiveRacksByZoneId(z.getId())));
    }

    @Transactional(readOnly = true)
    public ZoneListResDto findById(UUID id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));
        return ZoneListResDto.from(zone, (int) zoneRepository.countActiveRacksByZoneId(id));
    }

    // 카테고리별 구역 조회 (적치 위치 추천용)
    @Transactional(readOnly = true)
    public java.util.List<ZoneListResDto> findByCategoryId(UUID categoryId) {
        return zoneRepository.findByCategoryIdAndIsActiveTrue(categoryId)
                .stream().map(ZoneListResDto::from).toList();
    }

    // 창고 + (옵션)타입 기준 구역 조회 (불량 임시 보관존 등)
    @Transactional(readOnly = true)
    public java.util.List<ZoneListResDto> findByWarehouseAndType(
            UUID warehouseId, com.beyond.wbs.zone.domain.ZoneType type, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("해당 창고에 대한 접근 권한이 없습니다.");
        }
        java.util.List<Zone> list = (type == null)
                ? zoneRepository.findByWarehouseIdOrderBySortOrderAsc(warehouseId)
                : zoneRepository.findByWarehouseIdAndZoneTypeAndIsActiveTrue(warehouseId, type);
        return list.stream().map(ZoneListResDto::from).toList();
    }

    public ZoneListResDto update(UUID id, ZoneUpdateDto dto, UUID clientId) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));
        if (!zone.getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 구역에 대한 접근 권한이 없습니다.");
        }

        ProductCategory category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new EntityNotFoundException("카테고리를 찾을 수 없습니다."));
        }

        zone.update(dto.getName(), dto.getZoneType(), dto.getSortOrder(), category);

        return ZoneListResDto.from(zone, (int) zoneRepository.countActiveRacksByZoneId(id));
    }

    public void deactivate(UUID id, UUID clientId) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));
        if (!zone.getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 구역에 대한 접근 권한이 없습니다.");
        }
        zone.deactivate();
    }

    public void activate(UUID id, UUID clientId) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));
        if (!zone.getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 구역에 대한 접근 권한이 없습니다.");
        }
        zone.activate();
    }
}