package com.beyond.wbs.inventory.service;

import com.beyond.wbs.inventory.domain.Inventory;
import com.beyond.wbs.inventory.domain.InventoryTransaction;
import com.beyond.wbs.inventory.repository.InventoryRepository;
import com.beyond.wbs.inventory.repository.InventoryTransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Inventory Rebuild — inventory_transactions 합계로 inventory 테이블을 재계산하는 서비스.
 *
 * 목적:
 *   inventory_transactions 가 source of truth 이고 inventory 테이블은 그 합계의 캐시다.
 *   코드 버그/비정상 종료로 inventory 가 트랜잭션 합계와 어긋나면 이 서비스로 다시 맞춘다.
 *
 * 한계:
 *   inventory_transactions 자체에 누락이 있는 케이스 (예: dispatch 가 절반만 발행되어
 *   release 트랜잭션이 누락된 경우) 는 이 서비스로 못 잡는다. 그런 케이스는
 *   "잔여 출고 처리(forceReleaseResidual)" 같은 도메인 보상 액션으로 별도 처리해야 한다.
 *
 * 호출 경로:
 *   - admin endpoint POST /admin/inventory/rebuild  (수동 트리거)
 *   - InventoryRebuildScheduler                     (매일 새벽 자동 실행)
 */
@Slf4j
@Service
@Transactional
public class InventoryRebuildService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    public InventoryRebuildService(InventoryRepository inventoryRepository,
                                   InventoryTransactionRepository inventoryTransactionRepository) {
        this.inventoryRepository = inventoryRepository;
        this.inventoryTransactionRepository = inventoryTransactionRepository;
    }

    /**
     * 창고 단위 재계산. warehouseId == null 이면 전체.
     */
    public RebuildResult rebuild(UUID warehouseId) {
        List<Inventory> inventories = warehouseId == null
                ? inventoryRepository.findAll()
                : inventoryRepository.findByWarehouseId(warehouseId);
        return doRebuild(inventories);
    }

    private RebuildResult doRebuild(List<Inventory> inventories) {
        int total = inventories.size();
        int corrected = 0;

        for (Inventory inv : inventories) {
            List<InventoryTransaction> txs = inventoryTransactionRepository
                    .findByInventoryIdOrderByCreatedAtAsc(inv.getId());

            // statusTo 별 qty 합계 = 그 status 의 현재 수량
            int available = 0, reserved = 0, defect = 0, incoming = 0, pending = 0;
            for (InventoryTransaction tx : txs) {
                if (tx.getStatusTo() == null) continue;
                switch (tx.getStatusTo()) {
                    case available -> available += tx.getQty();
                    case reserved  -> reserved  += tx.getQty();
                    case defect    -> defect    += tx.getQty();
                    case incoming  -> incoming  += tx.getQty();
                    case pending   -> pending   += tx.getQty();
                }
            }

            boolean changed = !Objects.equals(inv.getAvailableQty(), available)
                    || !Objects.equals(inv.getReservedQty(), reserved)
                    || !Objects.equals(inv.getDefectQty(), defect)
                    || !Objects.equals(inv.getIncomingQty(), incoming)
                    || !Objects.equals(inv.getPendingQty(), pending);

            if (changed) {
                log.info("[rebuild] inventoryId={} corrected — available {}->{}, reserved {}->{}, defect {}->{}, incoming {}->{}, pending {}->{}",
                        inv.getId(),
                        inv.getAvailableQty(), available,
                        inv.getReservedQty(), reserved,
                        inv.getDefectQty(), defect,
                        inv.getIncomingQty(), incoming,
                        inv.getPendingQty(), pending);

                inv.setAvailableQty(available);
                inv.setReservedQty(reserved);
                inv.setDefectQty(defect);
                inv.setIncomingQty(incoming);
                inv.setPendingQty(pending);
                // totalQty 는 @PreUpdate 에서 자동 재계산됨
                inventoryRepository.save(inv);
                corrected++;
            }
        }

        log.info("[rebuild] complete — totalChecked={}, corrected={}", total, corrected);
        return new RebuildResult(total, corrected);
    }

    public record RebuildResult(int totalChecked, int corrected) {}
}
