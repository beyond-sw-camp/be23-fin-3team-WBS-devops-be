package com.beyond.wbs.warehouse.service;

import com.beyond.wbs.code.CodeGenerator;
import com.beyond.wbs.code.NumberingUtil;
import com.beyond.wbs.code.enums.RegionCode;
import com.beyond.wbs.warehouse.domain.Warehouse;
import com.beyond.wbs.warehouse.domain.WarehouseType;
import com.beyond.wbs.warehouse.dtos.WarehouseCreateDto;
import com.beyond.wbs.warehouse.dtos.WarehouseDetailResDto;
import com.beyond.wbs.warehouse.dtos.WarehouseListResDto;
import com.beyond.wbs.warehouse.dtos.WarehouseUpdateDto;
import com.beyond.wbs.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final NumberingUtil numberingUtil;

    @Autowired
    public WarehouseService(WarehouseRepository warehouseRepository,
                            NumberingUtil numberingUtil) {
        this.warehouseRepository = warehouseRepository;
        this.numberingUtil = numberingUtil;
    }

    // 창고 생성 — 코드 자동 생성 (WH-SEL-NOR-001 형식)
    public UUID create(WarehouseCreateDto dto, UUID clientId) {
        WarehouseType whType = dto.getWarehouseType() != null ? dto.getWarehouseType() : WarehouseType.NORMAL;

        // 지역코드는 DTO 에서 받거나 기본값 SEL
        RegionCode region = dto.getRegionCode() != null ? dto.getRegionCode() : RegionCode.SEL;

        // 코드 자동 생성 (순번은 DB 시퀀스에서 락 걸고 가져옴)
        String code = CodeGenerator.generateWarehouseCode(region, whType.getCode(), numberingUtil.nextSeq("warehouse"));

        Warehouse warehouse = Warehouse.builder()
                .clientId(clientId)
                .name(dto.getName())
                .code(code)
                .address(dto.getAddress())
                .warehouseType(whType)
                .isActive(true)
                .build();
        return warehouseRepository.save(warehouse).getId();
    }

    // 창고 목록 조회 — zoneCount/rackCount/updatedAt 포함
    // warehouseType 이 null 이면 전체, 지정되면 해당 타입만 반환
    @Transactional(readOnly = true)
    public Page<WarehouseListResDto> findAll(Pageable pageable, UUID clientId, WarehouseType warehouseType) {
        return warehouseRepository.findListByClientId(clientId, warehouseType, pageable);
    }

    // 창고 상세 조회 — 상세 전용 필드 + zone/rack count 포함
    @Transactional(readOnly = true)
    public WarehouseDetailResDto findDetail(UUID id, UUID clientId) {
        WarehouseDetailResDto detail = warehouseRepository.findDetailById(id)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        // 소유권 검증용으로 엔티티도 확인 (상세 DTO 에는 clientId 가 없음)
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("해당 창고에 대한 접근 권한이 없습니다.");
        }
        return detail;
    }

    // 창고 상세 정보 수정 (name/address/managerName/phone/notes 부분 수정)
    public WarehouseDetailResDto updateDetail(UUID id, WarehouseUpdateDto dto, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));

        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("해당 창고에 대한 접근 권한이 없습니다.");
        }

        warehouse.updateDetail(
                dto.getName(),
                dto.getAddress(),
                dto.getManagerName(),
                dto.getPhone(),
                dto.getNotes()
        );

        // 수정 후 상세(집계 포함) 재조회
        return warehouseRepository.findDetailById(id)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));
    }

    // 창고 비활성화 (삭제 대신)
    public void deactivate(UUID id, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));

        // ✅ 소유권 검증
        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("해당 창고에 대한 접근 권한이 없습니다.");
        }
        warehouse.deactivate();
    }

    // 창고 재활성화 (deactivate 의 짝)
    public void activate(UUID id, UUID clientId) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("창고를 찾을 수 없습니다."));

        if (!warehouse.getClientId().equals(clientId)) {
            throw new SecurityException("해당 창고에 대한 접근 권한이 없습니다.");
        }
        warehouse.activate();
    }
}
