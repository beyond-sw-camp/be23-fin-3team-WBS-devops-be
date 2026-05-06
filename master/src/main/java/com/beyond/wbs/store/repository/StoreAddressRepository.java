package com.beyond.wbs.store.repository;

import com.beyond.wbs.store.domain.StoreAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoreAddressRepository extends JpaRepository<StoreAddress, UUID> {
    List<StoreAddress> findByStoreId(UUID storeId);
}
