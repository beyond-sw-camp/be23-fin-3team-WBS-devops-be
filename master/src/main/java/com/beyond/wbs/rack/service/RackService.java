package com.beyond.wbs.rack.service;

import com.beyond.wbs.common.client.StockServiceClient;
import com.beyond.wbs.code.CodeGenerator;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.location.domain.Location;
import com.beyond.wbs.location.repository.LocationRepository;
import com.beyond.wbs.rack.domain.Rack;
import com.beyond.wbs.rack.dtos.RackCreateDto;
import com.beyond.wbs.rack.dtos.RackListResDto;
import com.beyond.wbs.rack.dtos.RackUpdateDto;
import com.beyond.wbs.rack.repository.RackRepository;
import com.beyond.wbs.supplier.domain.Supplier;
import com.beyond.wbs.supplier.repository.SupplierRepository;
import com.beyond.wbs.zone.domain.Zone;
import com.beyond.wbs.zone.repository.ZoneRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RackService {
    private final RackRepository rackRepository;
    private final ZoneRepository zoneRepository;
    private final SupplierRepository supplierRepository;
    private final LocationRepository locationRepository;
    private final NumberingUtil numberingUtil;
    private final StockServiceClient stockServiceClient;

    @Autowired
    public RackService(RackRepository rackRepository,
                       ZoneRepository zoneRepository,
                       SupplierRepository supplierRepository,
                       LocationRepository locationRepository,
                       NumberingUtil numberingUtil,
                       StockServiceClient stockServiceClient) {
        this.rackRepository = rackRepository;
        this.zoneRepository = zoneRepository;
        this.supplierRepository = supplierRepository;
        this.locationRepository = locationRepository;
        this.numberingUtil = numberingUtil;
        this.stockServiceClient = stockServiceClient;
    }

    public UUID create(RackCreateDto dto, UUID clientId) {
        Zone zone = zoneRepository.findById(dto.getZoneId())
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));

        if (!zone.getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 구역에 대한 접근 권한이 없습니다.");
        }

        // 클라이언트가 warehouseId 도 보냈다면 zone 의 warehouse 와 일치 검증
        if (dto.getWarehouseId() != null
                && !zone.getWarehouse().getId().equals(dto.getWarehouseId())) {
            throw new IllegalArgumentException("zone 의 소속 창고와 warehouseId 가 일치하지 않습니다.");
        }

        Supplier supplier = null;
        if (dto.getSupplierId() != null) {
            supplier = supplierRepository.findById(dto.getSupplierId())
                    .orElseThrow(() -> new EntityNotFoundException("협력사를 찾을 수 없습니다."));
        }

        // 랙 코드 자동 생성: RK-{구역코드}-{협력사코드}-{순번}
        String vendorCode = supplier != null ? supplier.getCode() : "SELF";
        String code = CodeGenerator.generateRackCode(zone.getCode(), vendorCode, numberingUtil.nextSeq("rack"));

        Rack rack = Rack.builder()
                .zone(zone)
                .name(dto.getName())
                .code(code)
                .supplier(supplier)
                .levelNo(dto.getLevelNo())
                .levelGuideJson(dto.getLevelGuideJson())
                .maxCapacity(dto.getMaxCapacity())
                .widthMm(dto.getWidthMm())
                .depthMm(dto.getDepthMm())
                .heightMm(dto.getHeightMm())
                .build();

        Rack savedRack = rackRepository.save(rack);

        // levelNo 만큼 기본 로케이션 자동 생성 (1층 ~ levelNo 층).
        // Why: 레이아웃 에디터/랙 관리에서 랙만 만들면 "빈 로케이션 없음" 으로
        //      적치 지시서가 막히는 UX 이슈가 있었음. 층수가 명시되면 각 층을
        //      실제 location 레코드로 같이 생성해 위치 배정이 바로 가능하도록 한다.
        Integer levelNo = dto.getLevelNo();
        if (levelNo != null && levelNo > 0) {
            List<Integer> locationCapacities = distributeLocationCapacities(dto.getMaxCapacity(), levelNo);
            for (int floor = 1; floor <= levelNo; floor++) {
                String locationCode = CodeGenerator.generateLocationCode(savedRack.getCode(), floor);
                locationRepository.save(Location.builder()
                        .rack(savedRack)
                        .floorNo(floor)
                        .code(locationCode)
                        .maxCapacity(locationCapacities.get(floor - 1))
                        .build());
            }
        }

        return savedRack.getId();
    }

    @Transactional(readOnly = true)
    public Page<RackListResDto> findAll(Pageable pageable) {
        return rackRepository.findAll(pageable)
                .map(RackListResDto::from);
    }

    /**
     * warehouseId / zoneId 필터 (둘 다 nullable). 둘 다 null 이면 전체.
     */
    @Transactional(readOnly = true)
    public List<RackListResDto> findByFilters(UUID warehouseId, UUID zoneId) {
        return rackRepository.findByFilters(warehouseId, zoneId)
                .stream().map(RackListResDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RackListResDto findById(UUID id) {
        Rack rack = rackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다."));
        return RackListResDto.from(rack);
    }

    // 협력사별 랙 조회 (적치 위치 추천용)
    @Transactional(readOnly = true)
    public List<RackListResDto> findBySupplierId(UUID supplierId) {
        return rackRepository.findBySupplierIdAndIsActiveTrue(supplierId)
                .stream().map(RackListResDto::from).toList();
    }

    // 구역별 랙 조회
    @Transactional(readOnly = true)
    public List<RackListResDto> findByZoneId(UUID zoneId) {
        return rackRepository.findByZoneIdAndIsActiveTrue(zoneId)
                .stream().map(RackListResDto::from).toList();
    }

    public RackListResDto update(UUID id, RackUpdateDto dto, UUID clientId) {
        Rack rack = rackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다."));
        if (!rack.getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 랙에 대한 접근 권한이 없습니다.");
        }

        Supplier supplier = null;
        if (dto.getSupplierId() != null) {
            supplier = supplierRepository.findById(dto.getSupplierId())
                    .orElseThrow(() -> new EntityNotFoundException("협력사를 찾을 수 없습니다."));
        }

        // 협력사 변경 가드 — 카테고리 혼입 방지.
        // 랙의 supplier 가 바뀌는 경우(공통→협력사, 협력사 A→B, 협력사→공통) 잔여 재고 있으면 차단.
        // 코드는 영구 식별자라 그대로 두고 supplier 메타만 갱신하므로, 재고만 비어있으면 안전하게 변경 가능.
        UUID currentSupplierId = rack.getSupplier() != null ? rack.getSupplier().getId() : null;
        UUID nextSupplierId = supplier != null ? supplier.getId() : null;
        boolean supplierChanging = !java.util.Objects.equals(currentSupplierId, nextSupplierId);
        if (supplierChanging) {
            List<UUID> locationIds = locationRepository.findByRackId(rack.getId())
                    .stream().map(Location::getId).toList();
            if (!locationIds.isEmpty()) {
                boolean hasStock = stockServiceClient.hasStockInLocations(locationIds, clientId.toString());
                if (hasStock) {
                    throw new IllegalStateException(
                            "재고가 있는 랙은 협력사를 변경할 수 없습니다. 재고를 비우거나 다른 랙으로 이동한 뒤 다시 시도하세요.");
                }
            }
        }

        rack.update(
                dto.getName(),
                supplier,
                dto.getLevelNo(),
                dto.getLevelGuideJson(),
                dto.getMaxCapacity(),
                dto.getWidthMm(),
                dto.getDepthMm(),
                dto.getHeightMm()
        );

        // levelNo 변경 시 부족한 층만큼 로케이션 자동 추가 (1 ~ newLevelNo 중 없는 층만).
        // Why: 레이아웃 에디터에서 "3층 → 5층" 으로 저장해도 지금까지는 숫자만 업데이트돼
        //      적치 모달에서 "빈 로케이션 없음" 으로 막히는 UX 이슈가 있었음.
        // 주의: levelNo 가 줄어든 경우(5 → 3) 기존 4/5층은 건드리지 않는다 — 재고가 남아
        //      있을 수 있어 삭제는 위험.
        Integer newLevelNo = dto.getLevelNo();
        if (newLevelNo != null && newLevelNo > 0) {
            List<Integer> locationCapacities = distributeLocationCapacities(rack.getMaxCapacity(), newLevelNo);
            Set<Integer> existingFloors = locationRepository.findByRackId(rack.getId())
                    .stream()
                    .map(Location::getFloorNo)
                    .collect(Collectors.toSet());
            for (int floor = 1; floor <= newLevelNo; floor++) {
                if (!existingFloors.contains(floor)) {
                    String locationCode = CodeGenerator.generateLocationCode(rack.getCode(), floor);
                    locationRepository.save(Location.builder()
                            .rack(rack)
                            .floorNo(floor)
                            .code(locationCode)
                            .maxCapacity(locationCapacities.get(floor - 1))
                            .build());
                }
            }
        }

        return RackListResDto.from(rack);
    }

    public void deactivate(UUID id, UUID clientId) {
        Rack rack = rackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다."));
        if (!rack.getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 랙에 대한 접근 권한이 없습니다.");
        }

        List<UUID> locationIds = locationRepository.findByRackId(id).stream()
                .map(Location::getId)
                .collect(Collectors.toList());
        if (!locationIds.isEmpty()
                && stockServiceClient.hasStockInLocations(locationIds, clientId.toString())) {
            throw new IllegalStateException("재고가 남아있는 랙은 비활성화할 수 없습니다. 먼저 재고를 이동/처리해 주세요.");
        }

        rack.deactivate();
    }

    public void activate(UUID id, UUID clientId) {
        Rack rack = rackRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다."));
        if (!rack.getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 랙에 대한 접근 권한이 없습니다.");
        }
        rack.activate();
    }

    /**
     * 입력값을 모든 층(location)에 동일하게 적용.
     * - 의미: rack.maxCapacity = "각 층(location) 의 수용량".
     * - 예) maxCapacity=200, levels=4 → 각 층이 200, 랙 전체 보관 한도는 4×200=800.
     * - null/0 입력은 무제한/미정으로 취급 (모든 층에 null).
     */
    private static List<Integer> distributeLocationCapacities(Integer perLevelCapacity, int levelNo) {
        if (perLevelCapacity == null || perLevelCapacity <= 0 || levelNo <= 0) {
            return Collections.nCopies(Math.max(levelNo, 0), null);
        }
        return Collections.nCopies(levelNo, perLevelCapacity);
    }
}
