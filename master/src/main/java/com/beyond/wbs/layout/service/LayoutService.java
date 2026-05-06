package com.beyond.wbs.layout.service;

import com.beyond.wbs.layout.domain.RackLayout;
import com.beyond.wbs.layout.domain.WarehouseLayout;
import com.beyond.wbs.layout.domain.ZoneLayout;
import com.beyond.wbs.layout.dtos.*;
import com.beyond.wbs.layout.dtos.RackLayoutSaveDto;
import com.beyond.wbs.layout.dtos.ZoneLayoutSaveDto;
import com.beyond.wbs.layout.repository.RackLayoutRepository;
import com.beyond.wbs.layout.repository.WarehouseLayoutRepository;
import com.beyond.wbs.layout.repository.ZoneLayoutRepository;
import com.beyond.wbs.rack.domain.Rack;
import com.beyond.wbs.rack.repository.RackRepository;
import com.beyond.wbs.warehouse.domain.Warehouse;
import com.beyond.wbs.warehouse.repository.WarehouseRepository;
import com.beyond.wbs.zone.domain.Zone;
import com.beyond.wbs.zone.repository.ZoneRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class LayoutService {

    private final WarehouseLayoutRepository warehouseLayoutRepository;
    private final ZoneLayoutRepository zoneLayoutRepository;
    private final RackLayoutRepository rackLayoutRepository;
    private final WarehouseRepository warehouseRepository;
    private final ZoneRepository zoneRepository;
    private final RackRepository rackRepository;

    @Autowired
    public LayoutService(WarehouseLayoutRepository warehouseLayoutRepository,
                         ZoneLayoutRepository zoneLayoutRepository,
                         RackLayoutRepository rackLayoutRepository,
                         WarehouseRepository warehouseRepository,
                         ZoneRepository zoneRepository,
                         RackRepository rackRepository) {
        this.warehouseLayoutRepository = warehouseLayoutRepository;
        this.zoneLayoutRepository = zoneLayoutRepository;
        this.rackLayoutRepository = rackLayoutRepository;
        this.warehouseRepository = warehouseRepository;
        this.zoneRepository = zoneRepository;
        this.rackRepository = rackRepository;
    }

    // 창고 레이아웃 저장 (없으면 생성, 있으면 수정)
    public WarehouseLayoutResDto saveWarehouseLayout(WarehouseLayoutReqDto dto, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(dto.getWarehouseId())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        WarehouseLayout layout = warehouseLayoutRepository.findByWarehouseId(dto.getWarehouseId())
                .orElse(null);

        if (layout == null) {
            layout = WarehouseLayout.builder()
                    .warehouse(warehouse)
                    .canvasWidth(dto.getCanvasWidth())
                    .canvasHeight(dto.getCanvasHeight())
                    .bgColor(dto.getBgColor())
                    .build();
        } else {
            layout.update(dto.getCanvasWidth(), dto.getCanvasHeight(), dto.getBgColor());
        }

        return WarehouseLayoutResDto.from(warehouseLayoutRepository.save(layout));
    }

    // 존 레이아웃 저장
    public ZoneLayoutResDto saveZoneLayout(ZoneLayoutReqDto dto, UUID clientId) {
        Zone zone = zoneRepository.findById(dto.getZoneId())
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));
        if (!zone.getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        ZoneLayout layout = zoneLayoutRepository.findByZoneId(dto.getZoneId())
                .orElse(null);

        if (layout == null) {
            layout = ZoneLayout.builder()
                    .zone(zone)
                    .posX(dto.getPosX())
                    .posY(dto.getPosY())
                    .width(dto.getWidth())
                    .height(dto.getHeight())
                    .rotation(dto.getRotation() != null ? dto.getRotation() : 0)
                    .color(dto.getColor())
                    .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                    .build();
        } else {
            layout.update(dto.getPosX(), dto.getPosY(), dto.getWidth(), dto.getHeight(),
                    dto.getRotation() != null ? dto.getRotation() : 0, dto.getColor());
        }

        return ZoneLayoutResDto.from(zoneLayoutRepository.save(layout));
    }

    // 랙 레이아웃 저장
    public RackLayoutResDto saveRackLayout(RackLayoutReqDto dto, UUID clientId) {
        Rack rack = rackRepository.findById(dto.getRackId())
                .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다."));
        if (!rack.getZone().getWarehouse().getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        Zone zone = zoneRepository.findById(dto.getZoneId())
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));

        RackLayout layout = rackLayoutRepository.findByRackId(dto.getRackId())
                .orElse(null);

        if (layout == null) {
            layout = RackLayout.builder()
                    .rack(rack)
                    .zone(zone)
                    .posX(dto.getPosX())
                    .posY(dto.getPosY())
                    .width(dto.getWidth())
                    .height(dto.getHeight())
                    .rotation(dto.getRotation() != null ? dto.getRotation() : 0)
                    .color(dto.getColor())
                    .build();
        } else {
            layout.update(dto.getPosX(), dto.getPosY(), dto.getWidth(), dto.getHeight(),
                    dto.getRotation() != null ? dto.getRotation() : 0, dto.getColor());
        }

        return RackLayoutResDto.from(rackLayoutRepository.save(layout));
    }

    // 창고 레이아웃 조회 (아직 생성되지 않은 경우 null 반환)
    @Transactional(readOnly = true)
    public WarehouseLayoutResDto findWarehouseLayout(UUID warehouseId) {
        return warehouseLayoutRepository.findByWarehouseId(warehouseId)
                .map(WarehouseLayoutResDto::from)
                .orElse(null);
    }

    // 창고 기준 존 레이아웃 전체 조회
    @Transactional(readOnly = true)
    public List<ZoneLayoutResDto> findZoneLayouts(UUID warehouseId) {
        return zoneLayoutRepository.findByZone_WarehouseIdAndZone_IsActiveTrue(warehouseId)
                .stream()
                .map(ZoneLayoutResDto::from)
                .collect(Collectors.toList());
    }

    // 존 기준 랙 레이아웃 전체 조회
    @Transactional(readOnly = true)
    public List<RackLayoutResDto> findRackLayouts(UUID zoneId) {
        return rackLayoutRepository.findByZoneIdAndRack_IsActiveTrueAndZone_IsActiveTrue(zoneId)
                .stream()
                .map(RackLayoutResDto::from)
                .collect(Collectors.toList());
    }

    // 창고 전체의 랙 레이아웃 (zone 경유)
    @Transactional(readOnly = true)
    public List<RackLayoutResDto> findRackLayoutsByWarehouse(UUID warehouseId) {
        return rackLayoutRepository.findByZone_WarehouseIdAndRack_IsActiveTrueAndZone_IsActiveTrue(warehouseId)
                .stream()
                .map(RackLayoutResDto::from)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────
    //  Bulk save (delete-by-explicit-id)
    // ──────────────────────────────────────────────

    /**
     * Zone 레이아웃 일괄 저장.
     * - items: zoneId 단위 upsert
     * - deletedZoneIds: 해당 zoneId 의 ZoneLayout 삭제
     * - 그 외 행은 그대로 유지 (시나리오 B 안전성)
     */
    public List<ZoneLayoutResDto> saveZoneLayouts(ZoneLayoutSaveDto dto, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(dto.getWarehouseId())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }

        // 1) upsert
        List<ZoneLayout> saved = new java.util.ArrayList<>();
        for (ZoneLayoutSaveDto.ZoneLayoutItem item : dto.getItems()) {
            Zone zone = zoneRepository.findById(item.getZoneId())
                    .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다: " + item.getZoneId()));
            if (!zone.getWarehouse().getId().equals(dto.getWarehouseId())) {
                throw new IllegalArgumentException("구역의 소속 창고가 일치하지 않습니다: " + item.getZoneId());
            }

            ZoneLayout layout = zoneLayoutRepository.findByZoneId(item.getZoneId()).orElse(null);
            if (layout == null) {
                layout = ZoneLayout.builder()
                        .zone(zone)
                        .posX(item.getPosX())
                        .posY(item.getPosY())
                        .width(item.getWidth())
                        .height(item.getHeight())
                        .rotation(item.getRotation() != null ? item.getRotation() : 0)
                        .color(item.getColor())
                        .sortOrder(item.getSortOrder() != null ? item.getSortOrder() : 0)
                        .build();
            } else {
                layout.update(item.getPosX(), item.getPosY(), item.getWidth(), item.getHeight(),
                        item.getRotation() != null ? item.getRotation() : 0, item.getColor());
            }
            saved.add(zoneLayoutRepository.save(layout));
        }

        // 2) explicit delete
        if (dto.getDeletedZoneIds() != null) {
            for (UUID zoneId : dto.getDeletedZoneIds()) {
                zoneLayoutRepository.findByZoneId(zoneId)
                        .ifPresent(zoneLayoutRepository::delete);
            }
        }

        return saved.stream().map(ZoneLayoutResDto::from).collect(Collectors.toList());
    }

    /**
     * Rack 레이아웃 일괄 저장.
     */
    public List<RackLayoutResDto> saveRackLayouts(RackLayoutSaveDto dto, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(dto.getWarehouseId())
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        Zone zone = zoneRepository.findById(dto.getZoneId())
                .orElseThrow(() -> new EntityNotFoundException("구역을 찾을 수 없습니다."));
        if (!zone.getWarehouse().getId().equals(dto.getWarehouseId())) {
            throw new IllegalArgumentException("구역의 소속 창고가 일치하지 않습니다.");
        }

        // 1) upsert
        List<RackLayout> saved = new java.util.ArrayList<>();
        for (RackLayoutSaveDto.RackLayoutItem item : dto.getItems()) {
            Rack rack = rackRepository.findById(item.getRackId())
                    .orElseThrow(() -> new EntityNotFoundException("랙을 찾을 수 없습니다: " + item.getRackId()));
            if (!rack.getZone().getId().equals(dto.getZoneId())) {
                throw new IllegalArgumentException("랙의 소속 구역이 일치하지 않습니다: " + item.getRackId());
            }

            int rotation = item.getRotation() != null ? item.getRotation() : 0;
            RackLayout layout = rackLayoutRepository.findByRackId(item.getRackId()).orElse(null);
            if (layout == null) {
                layout = RackLayout.builder()
                        .rack(rack)
                        .zone(zone)
                        .posX(item.getPosX())
                        .posY(item.getPosY())
                        .width(item.getWidth())
                        .height(item.getHeight())
                        .rotation(rotation)
                        .color(item.getColor())
                        .build();
            } else {
                layout.update(item.getPosX(), item.getPosY(), item.getWidth(), item.getHeight(), rotation, item.getColor());
            }
            saved.add(rackLayoutRepository.save(layout));
        }

        // 2) explicit delete
        if (dto.getDeletedRackIds() != null) {
            for (UUID rackId : dto.getDeletedRackIds()) {
                rackLayoutRepository.findByRackId(rackId)
                        .ifPresent(rackLayoutRepository::delete);
            }
        }

        return saved.stream().map(RackLayoutResDto::from).collect(Collectors.toList());
    }

}
