package com.beyond.wbs.store.service;

import com.beyond.wbs.store.domain.Store;
import com.beyond.wbs.store.domain.StoreAddress;
import com.beyond.wbs.store.dtos.*;
import com.beyond.wbs.store.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreAddressRepository addressRepository;

    @Autowired
    public StoreService(StoreRepository storeRepository,
                        StoreAddressRepository addressRepository) {
        this.storeRepository = storeRepository;
        this.addressRepository = addressRepository;
    }

    public UUID create(StoreCreateDto dto, UUID clientId) {
        Store store = Store.builder()
                .clientId(clientId)
                .name(dto.getName())
                .code(dto.getCode())
                .bizNo(dto.getBizNo())
                .ceoName(dto.getCeoName())
                .tel(dto.getTel())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .build();
        return storeRepository.save(store).getId();
    }

    public UUID createAddress(StoreAddressCreateDto dto) {
        Store store = storeRepository.findById(dto.getStoreId())
                .orElseThrow(() -> new EntityNotFoundException("출고처를 찾을 수 없습니다."));
        StoreAddress address = StoreAddress.builder()
                .store(store)
                .name(dto.getName())
                .receiver(dto.getReceiver())
                .tel(dto.getTel())
                .address(dto.getAddress())
                .addressDetail(dto.getAddressDetail())
                .zipcode(dto.getZipcode())
                .isDefault(dto.getIsDefault() != null ? dto.getIsDefault() : false)
                .build();
        return addressRepository.save(address).getId();
    }

    @Transactional(readOnly = true)
    public Page<StoreResDto> findAll(UUID clientId, Pageable pageable) {
        return storeRepository.findByClientId(clientId, pageable)
                .map(StoreResDto::from);
    }

    @Transactional(readOnly = true)
    public StoreResDto findById(UUID id) {
        return StoreResDto.from(storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("출고처를 찾을 수 없습니다.")));
    }

    public void deactivate(UUID id, UUID clientId) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("출고처를 찾을 수 없습니다."));
        if (!store.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        store.deactivate();
    }

    /**
     * 자동 웨이브 생성 토글 — WaveScheduler 의 처리 대상에 이 출고처를 포함/제외.
     */
    public StoreResDto toggleAutoWave(UUID id, Boolean enabled, UUID clientId) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("출고처를 찾을 수 없습니다."));
        if (!store.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        store.updateAutoWaveEnabled(enabled);
        return StoreResDto.from(store);
    }
}