package com.beyond.wbs.location.controller;

import com.beyond.wbs.location.dtos.LocationCreateDto;
import com.beyond.wbs.location.dtos.LocationListResDto;
import com.beyond.wbs.location.dtos.LocationUpdateMaxCapacityDto;
import com.beyond.wbs.location.dtos.WarehouseLocationsResDto;
import com.beyond.wbs.location.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/location")
public class LocationController {

    private final LocationService locationService;

    @Autowired
    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid LocationCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = locationService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @RequestParam UUID rackId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LocationListResDto> list = locationService.findAll(rackId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        LocationListResDto dto = locationService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /**
     * 배치 로케이션 조회 — 여러 UUID 를 body 로 받아 한 번에 반환.
     *
     * 내부 서비스(Feign) 호출에서 N+1 을 제거하기 위한 엔드포인트.
     * URL 길이 제한 회피를 위해 POST 사용.
     *
     * 요청 예시:
     *   POST /location/batch
     *   Body: ["01935c00-...", "01935c00-..."]
     */
    @AuditLog
    @PostMapping("/batch")
    public ResponseEntity<?> findByIds(@RequestBody List<UUID> ids) {
        List<LocationListResDto> list = locationService.findByIds(ids);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    /**
     * 로케이션 maxCapacity 변경.
     * 적치 화면에서 추천 위치의 수용량이 부족할 때 인라인으로 늘리는 용도.
     */
    @AuditLog(action = "수용량변경")
    @PatchMapping("/max-capacity/{id}")
    public ResponseEntity<?> updateMaxCapacity(@PathVariable UUID id,
                                               @RequestBody LocationUpdateMaxCapacityDto dto,
                                               @RequestHeader("X-Client-Id") UUID clientId) {
        locationService.updateMaxCapacity(id, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    /**
     * 랙 단위 일괄 maxCapacity 변경.
     * 레이아웃 에디터에서 한 랙의 모든 층 수용량을 한 번에 늘릴 때 사용.
     */
    @AuditLog(action = "수용량일괄변경")
    @PatchMapping("/max-capacity-by-rack/{rackId}")
    public ResponseEntity<?> bulkUpdateMaxCapacityByRack(@PathVariable UUID rackId,
                                                        @RequestBody LocationUpdateMaxCapacityDto dto,
                                                        @RequestHeader("X-Client-Id") UUID clientId) {
        int updated = locationService.bulkUpdateMaxCapacityByRack(rackId, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("updated", updated));
    }

    @AuditLog(action = "비활성화")
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        locationService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    /**
     * 창고 단위 로케이션 벌크 조회.
     * Zone/Rack/Location 정보를 한 번에 반환하여 Stock 서비스의 N+1 Feign 호출을 제거한다.
     * 활성 로케이션만 포함하며, 권한 검증은 X-Client-Id 로 수행.
     */
    @AuditLog
    @GetMapping("/by-warehouse/{warehouseId}")
    public ResponseEntity<?> findByWarehouse(@PathVariable UUID warehouseId,
                                             @RequestHeader("X-Client-Id") UUID clientId) {
        WarehouseLocationsResDto res = locationService.findByWarehouseSummary(warehouseId, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

}
