package com.beyond.wbs.inventory.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 재고 (Inventory)
 *
 * 상품 + 창고 + 위치(랙) 단위로 현재 보유 재고 수량을 관리한다.
 * 같은 상품이라도 창고/위치가 다르면 row가 분리된다.
 *
 * 주요 수량:
 * - availableQty : 가용재고 (즉시 출고 가능)
 * - reservedQty  : 예약재고 (출고지시서 승인되어 잠긴 양)
 * - defectQty    : 불량재고 (검수 시 불량 판정)
 * - pendingQty   : 검수중재고 (입고 검수 대기)
 * - totalQty     : 전체 합계 (위 4개의 합)
 *
 * 가용재고 = totalQty - reservedQty - defectQty - pendingQty
 * 단, 빠른 조회를 위해 availableQty를 별도 컬럼으로 관리한다.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "inventories",
        // (warehouse_id, location_id) 조합이 유일해야 함
        // [정책] 한 Location(층)에는 한 가지 SKU만 보관 가능
        //  - 같은 상품은 기존 row에 수량만 누적
        //  - 다른 상품은 같은 Location에 들어갈 수 없음 (DB 레벨 강제)
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inventory_warehouse_location",
                columnNames = {"warehouse_id", "location_id"}
        ))
public class Inventory {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // Account Service 참조 (고객사 / 멀티테넌트)
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // Master Service 참조 - 상품 ID
    @Column(name = "product_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID productId;

    // Master Service 참조 - 창고 ID
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Master Service 참조 - 위치 (랙) ID
    // nullable: 입고예정/검수중처럼 적치 위치가 미정인 단계의 임시 행을 허용한다.
    //           실 위치가 확정되면 별도 행으로 옮긴다.
    @Column(name = "location_id", columnDefinition = "BINARY(16)")
    private UUID locationId;

    // 가용 수량 (즉시 출고 가능)
    @Column(name = "available_qty", nullable = false)
    @Builder.Default
    private Integer availableQty = 0;

    // 예약 수량 (출고지시서 승인 시 할당)
    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private Integer reservedQty = 0;

    // 불량 수량 (검수 불합격, 폐기 대기)
    @Column(name = "defect_qty", nullable = false)
    @Builder.Default
    private Integer defectQty = 0;

    // 입고예정 수량 (입고지시서 승인됨, 아직 안 들어옴. 영업팀 참고용)
    @Column(name = "incoming_qty", nullable = false)
    @Builder.Default
    private Integer incomingQty = 0;

    // 검수중 수량 (입고 검수 대기)
    @Column(name = "pending_qty", nullable = false)
    @Builder.Default
    private Integer pendingQty = 0;

    // 전체 수량 합계 (available + reserved + defect + incoming + pending)
    @Column(name = "total_qty", nullable = false)
    @Builder.Default
    private Integer totalQty = 0;

    // 수정 일시
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
        recalculateTotal();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
        recalculateTotal();
    }

    /**
     * 전체 수량 재계산
     * 각 수량 변경 시 항상 합계가 일치하도록 보장
     */
    private void recalculateTotal() {
        this.totalQty = this.availableQty + this.reservedQty + this.defectQty + this.incomingQty + this.pendingQty;
    }

    // ========================================
    // 도메인 메서드 (재고 변화 로직)
    // ========================================

    /**
     * 입고 (검수/적치 완료)
     * 가용재고 증가
     */
    public void addAvailable(int qty) {
        this.availableQty += qty;
        recalculateTotal();
    }

    /**
     * 불량재고 직접 증가 (입고 시점 즉시 불량 판정 — 기타입고 등)
     */
    public void addDefect(int qty) {
        this.defectQty += qty;
        recalculateTotal();
    }

    /**
     * 출고 확정
     * 예약재고에서 차감 (이미 reserve 단계에서 예약된 상태)
     */
    public void releaseReserved(int qty) {
        if (this.reservedQty < qty) {
            throw new IllegalArgumentException(
                    "예약재고가 부족합니다. 요청: " + qty + ", 보유: " + this.reservedQty);
        }
        this.reservedQty -= qty;
        recalculateTotal();
    }

    /**
     * 출고지시서 승인 시 예약 처리
     * 가용재고 → 예약재고로 이동
     */
    public void reserve(int qty) {
        if (this.availableQty < qty) {
            throw new IllegalArgumentException(
                    "가용재고가 부족합니다. 요청: " + qty + ", 보유: " + this.availableQty);
        }
        this.availableQty -= qty;
        this.reservedQty += qty;
        recalculateTotal();
    }

    /**
     * 출고지시서 취소 시 예약 해제
     * 예약재고 → 가용재고로 원복
     */
    public void unreserve(int qty) {
        if (this.reservedQty < qty) {
            throw new IllegalArgumentException(
                    "예약재고가 부족합니다. 요청: " + qty + ", 보유: " + this.reservedQty);
        }
        this.reservedQty -= qty;
        this.availableQty += qty;
        recalculateTotal();
    }

    /**
     * 입고지시서 승인 시 입고예정 추가
     * incomingQty 증가 (영업팀이 "곧 들어올 재고" 확인 가능)
     */
    public void addIncoming(int qty) {
        this.incomingQty += qty;
        recalculateTotal();
    }

    /**
     * 입고예정 해제 (검수 시작 또는 입고지시서 취소)
     * incomingQty → 다른 상태로 전환될 때 호출
     */
    public void removeIncoming(int qty) {
        if (this.incomingQty < qty) {
            throw new IllegalArgumentException(
                    "입고예정 재고가 부족합니다. 요청: " + qty + ", 보유: " + this.incomingQty);
        }
        this.incomingQty -= qty;
        recalculateTotal();
    }

    /**
     * 검수 대기 (입고 시작)
     * pendingQty 증가
     */
    public void addPending(int qty) {
        this.pendingQty += qty;
        recalculateTotal();
    }

    /**
     * 검수중 재고 해제 (적치 시작 시 pending 을 다른 로케이션 row 로 옮길 때 사용)
     * pendingQty 만 차감한다 (available 은 건드리지 않음).
     */
    public void removePending(int qty) {
        if (this.pendingQty < qty) {
            throw new IllegalArgumentException(
                    "검수중 재고가 부족합니다. 요청: " + qty + ", 보유: " + this.pendingQty);
        }
        this.pendingQty -= qty;
        recalculateTotal();
    }

    /**
     * 검수 합격 (적치 완료)
     * pendingQty → availableQty
     */
    public void confirmPending(int qty) {
        if (this.pendingQty < qty) {
            throw new IllegalArgumentException(
                    "검수중 재고가 부족합니다. 요청: " + qty + ", 보유: " + this.pendingQty);
        }
        this.pendingQty -= qty;
        this.availableQty += qty;
        recalculateTotal();
    }

    /**
     * 불량 처리
     * pendingQty → defectQty (검수 시 불량 판정)
     */
    public void markDefect(int qty) {
        if (this.pendingQty < qty) {
            throw new IllegalArgumentException(
                    "검수중 재고가 부족합니다. 요청: " + qty + ", 보유: " + this.pendingQty);
        }
        this.pendingQty -= qty;
        this.defectQty += qty;
        recalculateTotal();
    }

    /**
     * 재고 조정 (재고실사 결과)
     * 차이를 가용재고에 직접 반영 (양수/음수 모두 가능)
     */
    public void adjust(int diffQty) {
        int newAvailable = this.availableQty + diffQty;
        if (newAvailable < 0) {
            throw new IllegalArgumentException(
                    "조정 후 가용재고가 음수가 될 수 없습니다. 현재: " + this.availableQty + ", 조정: " + diffQty);
        }
        this.availableQty = newAvailable;
        recalculateTotal();
    }

    /**
     * 폐기 처리 (불량재고 → 물리 제거)
     * defectQty 직접 차감
     */
    public void disposeDefect(int qty) {
        if (this.defectQty < qty) {
            throw new IllegalArgumentException(
                    "불량재고가 부족합니다. 요청: " + qty + ", 보유: " + this.defectQty);
        }
        this.defectQty -= qty;
        recalculateTotal();
    }
}
