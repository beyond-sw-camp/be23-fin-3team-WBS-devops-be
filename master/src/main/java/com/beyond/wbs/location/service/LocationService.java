package com.beyond.wbs.location.service;

import com.beyond.wbs.code.CodeGenerator;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.common.client.StockServiceClient;
import com.beyond.wbs.location.domain.Location;
import com.beyond.wbs.location.dtos.LocationCreateDto;
import com.beyond.wbs.location.dtos.LocationListResDto;
import com.beyond.wbs.location.dtos.LocationUpdateMaxCapacityDto;
import com.beyond.wbs.location.dtos.WarehouseLocationsResDto;
import com.beyond.wbs.location.repository.LocationRepository;
import com.beyond.wbs.rack.domain.Rack;
import com.beyond.wbs.rack.repository.RackRepository;
import com.beyond.wbs.warehouse.domain.Warehouse;
import com.beyond.wbs.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LocationService {

    private final LocationRepository locationRepository;
    private final RackRepository rackRepository;
    private final WarehouseRepository warehouseRepository;
    private final NumberingUtil numberingUtil;
    private final StockServiceClient stockServiceClient;

    @Autowired
    public LocationService(LocationRepository locationRepository,
                           RackRepository rackRepository,
                           WarehouseRepository warehouseRepository,
                           NumberingUtil numberingUtil,
                           StockServiceClient stockServiceClient) {
        this.locationRepository = locationRepository;
        this.rackRepository = rackRepository;
        this.warehouseRepository = warehouseRepository;
        this.numberingUtil = numberingUtil;
        this.stockServiceClient = stockServiceClient;
    }

    public UUID create(LocationCreateDto dto, UUID clientId) {
        Rack rack = rackRepository.findById(dto.getRackId())
                .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다."));

        // rack → zone → warehouse 타고 소유권 검증
        if (!rack.getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 랙에 대한 접근 권한이 없습니다.");
        }

        // 로케이션 코드 자동 생성: LC-{랙코드}-{층번호}
        String code = CodeGenerator.generateLocationCode(rack.getCode(), dto.getFloorNo());

        Location location = Location.builder()
                .rack(rack)
                .floorNo(dto.getFloorNo())
                .code(code)
                .barcode(dto.getBarcode())
                .maxCapacity(dto.getMaxCapacity())
                .build();

        return locationRepository.save(location).getId();
    }

    @Transactional(readOnly = true)
    public Page<LocationListResDto> findAll(UUID rackId, Pageable pageable) {
        return locationRepository.findByRackId(rackId, pageable)
                .map(LocationListResDto::from);
    }

    @Transactional(readOnly = true)
    public LocationListResDto findById(UUID id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다."));
        return LocationListResDto.from(location);
    }

    /**
     * 배치 로케이션 조회 — 여러 UUID 를 한 번에 받아서 한 번의 쿼리로 일괄 반환.
     *
     * 사용처: Stock 서비스의 재고/트랜잭션 조회에서 N+1 Feign 호출을 방지하기 위함.
     * 요구 사항상 비활성(isActive=false) 로케이션도 포함 — 과거 이력의 이름 해석에 필요.
     *
     * 입력 ids 가 null/empty 이면 빈 리스트 반환.
     * 응답은 입력 순서와 무관하며, 존재하지 않는 id 는 결과에서 빠진다 (부분 성공).
     */
    @Transactional(readOnly = true)
    public List<LocationListResDto> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return locationRepository.findAllById(ids).stream()
                .map(LocationListResDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 로케이션 maxCapacity 변경.
     * - 권한: 해당 로케이션이 속한 창고가 clientId 소유인지 확인.
     * - null: 무제한/미정으로 되돌림.
     * - 0 이하: 비활성화 의미로 오해될 수 있어 거부.
     * - 무결성: 현재 보관 중인 totalQty 합계 > newMax 이면 거부 (stock-service 조회).
     *   null(무제한) 로 변경 시는 검증 생략 (어떤 양이든 허용).
     */
    public void updateMaxCapacity(UUID id, LocationUpdateMaxCapacityDto dto, UUID clientId) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다."));

        if (!location.getRack().getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 로케이션에 대한 접근 권한이 없습니다.");
        }

        Integer newCap = dto.getMaxCapacity();
        if (newCap != null && newCap <= 0) {
            throw new IllegalArgumentException("maxCapacity 는 1 이상이어야 합니다. (비활성화는 deactivate 사용)");
        }
        if (newCap != null) {
            Map<UUID, Integer> usage = stockServiceClient.getTotalQtyByLocations(
                    List.of(id), clientId.toString());
            int currentUsed = usage.getOrDefault(id, 0);
            if (currentUsed > newCap) {
                throw new IllegalStateException(
                        "현재 보관 중인 수량(" + currentUsed + ")이 새 수용량(" + newCap + ")보다 많습니다. "
                                + "재고를 옮긴 후 다시 시도하세요.");
            }
        }
        location.updateMaxCapacity(newCap);
    }

    /**
     * 랙 단위 일괄 maxCapacity 변경.
     * - 권한: 랙 소속 창고가 clientId 소유인지 확인.
     * - null: 무제한/미정으로 되돌림.
     * - 0 이하: 비활성화 의미로 오해될 수 있어 거부.
     * - 비활성 location 도 포함 (마스터 데이터 일관성).
     * - 무결성: 어느 한 location 이라도 현재 보관량 > newCap 이면 전체 거부 (atomic).
     *   초과 location 코드를 메시지에 포함해 어디부터 비워야 하는지 안내.
     * 반환: 변경된 location 개수.
     */
    public int bulkUpdateMaxCapacityByRack(UUID rackId, LocationUpdateMaxCapacityDto dto, UUID clientId) {
        Rack rack = rackRepository.findById(rackId)
                .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다."));
        if (!rack.getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 랙에 대한 접근 권한이 없습니다.");
        }
        Integer newCap = dto.getMaxCapacity();
        if (newCap != null && newCap <= 0) {
            throw new IllegalArgumentException("maxCapacity 는 1 이상이어야 합니다.");
        }
        List<Location> locations = locationRepository.findByRackId(rackId);
        if (newCap != null && !locations.isEmpty()) {
            List<UUID> ids = locations.stream().map(Location::getId).collect(Collectors.toList());
            Map<UUID, Integer> usage = stockServiceClient.getTotalQtyByLocations(
                    ids, clientId.toString());
            List<String> overflow = new ArrayList<>();
            for (Location l : locations) {
                int used = usage.getOrDefault(l.getId(), 0);
                if (used > newCap) {
                    overflow.add(l.getCode() + "(" + used + ")");
                }
            }
            if (!overflow.isEmpty()) {
                throw new IllegalStateException(
                        "다음 로케이션에 새 수용량(" + newCap + ")보다 많은 재고가 있습니다: "
                                + String.join(", ", overflow)
                                + ". 재고를 옮긴 후 다시 시도하세요.");
            }
        }
        for (Location l : locations) {
            l.updateMaxCapacity(newCap);
        }
        return locations.size();
    }

    public void deactivate(UUID id, UUID clientId) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("로케이션을 찾을 수 없습니다."));

        if (!location.getRack().getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("해당 로케이션에 대한 접근 권한이 없습니다.");
        }
        location.deactivate();
    }

    /**
     * 창고 단위 로케이션 벌크 조회 (Stock 서비스 벌크 요약용).
     *
     *  - 권한: warehouseId 가 clientId 소속인지 확인. 아니면 SecurityException (403).
     *  - 대상: Location.isActive = true 만 포함.
     *  - 정렬: zone.sortOrder ASC → zone.code ASC → rack.code ASC → floorNo ASC.
     *  - 빈 창고: 로케이션 0건이면 items=[] 로 반환.
     */
    @Transactional(readOnly = true)
    public WarehouseLocationsResDto findByWarehouseSummary(UUID warehouseId, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("해당 창고에 대한 접근 권한이 없습니다.");
        }

        List<Location> locations = locationRepository.findActiveByWarehouseIdWithDetails(warehouseId);

        List<WarehouseLocationsResDto.LocationSummaryItem> items = locations.isEmpty()
                ? Collections.emptyList()
                : locations.stream()
                    .sorted(locationOrder())
                    .map(LocationService::toItem)
                    .collect(Collectors.toList());

        return WarehouseLocationsResDto.builder()
                .warehouseId(warehouse.getId())
                .warehouseName(warehouse.getName())
                .items(items)
                .build();
    }

    // zone.sortOrder → zone.code → rack.code → floorNo 순 정렬자.
    // null 값은 뒤로 밀어낸다 (nullsLast) — 과거 데이터 안전성.
    private static Comparator<Location> locationOrder() {
        return Comparator
                .comparing((Location l) -> l.getRack().getZone().getSortOrder(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(l -> l.getRack().getZone().getCode(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(l -> l.getRack().getCode(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Location::getFloorNo,
                        Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static WarehouseLocationsResDto.LocationSummaryItem toItem(Location l) {
        Rack rack = l.getRack();
        return WarehouseLocationsResDto.LocationSummaryItem.builder()
                .zoneId(rack.getZone().getId())
                .zoneCode(rack.getZone().getCode())
                .zoneName(rack.getZone().getName())
                .rackId(rack.getId())
                .rackCode(rack.getCode())
                .rackName(rack.getName())
                .locationId(l.getId())
                .locationCode(l.getCode())
                .floorNo(l.getFloorNo())
                .maxCapacity(l.getMaxCapacity())
                .build();
    }
}
