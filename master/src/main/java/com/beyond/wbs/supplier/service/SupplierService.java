package com.beyond.wbs.supplier.service;

import com.beyond.wbs.product.domain.Product;
import com.beyond.wbs.product.repository.ProductRepository;
import com.beyond.wbs.supplier.domain.Supplier;
import com.beyond.wbs.supplier.domain.SupplierProduct;
import com.beyond.wbs.supplier.dtos.*;
import com.beyond.wbs.supplier.repository.*;
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
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierProductRepository supplierProductRepository;
    private final ProductRepository productRepository;

    @Autowired
    public SupplierService(SupplierRepository supplierRepository,
                           SupplierProductRepository supplierProductRepository,
                           ProductRepository productRepository) {
        this.supplierRepository = supplierRepository;
        this.supplierProductRepository = supplierProductRepository;
        this.productRepository = productRepository;
    }

    public UUID create(SupplierCreateDto dto, UUID clientId) {
        Supplier supplier = Supplier.builder()
                .clientId(clientId)
                .name(dto.getName())
                .code(dto.getCode())
                .bizNo(dto.getBizNo())
                .ceoName(dto.getCeoName())
                .tel(dto.getTel())
                .email(dto.getEmail())
                .address(dto.getAddress())
                .esgGrade(dto.getEsgGrade())
                .ecoCertified(dto.getEcoCertified() != null ? dto.getEcoCertified() : false)
                .esgMemo(dto.getEsgMemo())
                .build();
        return supplierRepository.save(supplier).getId();
    }

    public UUID createSupplierProduct(SupplierProductCreateDto dto) {
        Supplier supplier = supplierRepository.findById(dto.getSupplierId())
                .orElseThrow(() -> new EntityNotFoundException("입고처를 찾을 수 없습니다."));
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new EntityNotFoundException("상품을 찾을 수 없습니다."));

        SupplierProduct sp = SupplierProduct.builder()
                .supplier(supplier)
                .product(product)
                .venderSku(dto.getVenderSku())
                .unitPrice(dto.getUnitPrice())
                .leadTimeDays(dto.getLeadTimeDays())
                .moq(dto.getMoq() != null ? dto.getMoq() : 1)
                .isPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : false)
                .build();
        return supplierProductRepository.save(sp).getId();
    }

    @Transactional(readOnly = true)
    public Page<SupplierResDto> findAll(UUID clientId, Pageable pageable) {
        return supplierRepository.findByClientId(clientId, pageable)
                .map(SupplierResDto::from);
    }

    @Transactional(readOnly = true)
    public SupplierResDto findById(UUID id) {
        return SupplierResDto.from(supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("입고처를 찾을 수 없습니다.")));
    }

    @Transactional(readOnly = true)
    public List<SupplierProductResDto> findSupplierProducts(UUID supplierId) {
        return supplierProductRepository.findBySupplierId(supplierId)
                .stream().map(SupplierProductResDto::from)
                .collect(Collectors.toList());
    }

    public void deactivate(UUID id, UUID clientId) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("입고처를 찾을 수 없습니다."));
        if (!supplier.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        supplier.deactivate();
    }

    public void activate(UUID id, UUID clientId) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("입고처를 찾을 수 없습니다."));
        if (!supplier.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        supplier.activate();
    }

    public void update(UUID id, SupplierUpdateDto dto, UUID clientId) {
        Supplier supplier = supplierRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("입고처를 찾을 수 없습니다."));
        if (!supplier.getClientId().equals(clientId)) {
            throw new SecurityException("접근 권한이 없습니다.");
        }
        supplier.update(
                dto.getName(),
                dto.getBizNo(),
                dto.getCeoName(),
                dto.getTel(),
                dto.getEmail(),
                dto.getAddress(),
                dto.getEsgGrade(),
                dto.getEcoCertified(),
                dto.getEsgMemo()
        );
    }
}
