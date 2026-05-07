package com.beyond.wbs.outbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Table(
        name = "outbound_orders",
        indexes = {
                // 미리보기 가용재고 계산: client + warehouse + scheduled_date 범위 + status 필터
                @Index(name = "idx_ob_client_wh_date_status", columnList = "client_id, warehouse_id, scheduled_date, status"),
                // 대시보드 KPI / 처리 필요 조회 최적화
                @Index(name = "idx_ob_client_status_date", columnList = "client_id, status, scheduled_date")
        }
)
@Entity
public class OutboundOrders {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 출고지시서 번호 (자동채번: SO-00001)
    @Column(name = "order_no", nullable = false, length = 20)
    private String orderNo;

    // Account Service 참조
    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    // Master Service 참조 (창고)
    @Column(name = "warehouse_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    // Master Service 참조 (출고처)
    // 반품 출고(originType = "return")는 store 가 아닌 supplier 로 가므로 nullable.
    // 일반 출고는 NOT NULL 처럼 사용 — Service 단에서 destinationType 에 따라 분기 검증.
    @Column(name = "store_id", columnDefinition = "BINARY(16)")
    private UUID storeId;

    // 반품 출고 대상 입고처 (originType = "return" 일 때만 채움)
    @Column(name = "supplier_id", columnDefinition = "BINARY(16)")
    private UUID supplierId;

    // 출고 목적지 종류 — 'store'(일반) | 'supplier'(반품)
    // String 으로 두어 originType 처리와 일관성 유지.
    @Column(name = "destination_type", length = 20)
    private String destinationType;

    // 반품 사유 (originType = "return" 일 때만 채움)
    @Column(name = "return_reason", columnDefinition = "TEXT")
    private String returnReason;

    // Account Service 참조 (생성자)
    @Column(name = "created_by", nullable = false, columnDefinition = "BINARY(16)")
    private UUID createdBy;

    // Account Service 참조 (승인자)
    @Column(name = "approved_by", columnDefinition = "BINARY(16)")
    private UUID approvedBy;

    // Account Service 참조 (출고 담당자)
    @Column(name = "assigned_to", columnDefinition = "BINARY(16)")
    private UUID assignedTo;

    // 출고 지시서 상태
    // draft(초안) → approved(승인) → in_progress(처리중)
    // → completed(완료) / partial(부분완료) / cancelled(취소)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboundOrderStatus status;

    // ERP 수주서 출처 유형 (항상 sales_order - ERP 더미데이터 연동)
    @Column(name = "origin_type")
    private String originType;

    // ERP 수주서 ID (ERP 더미 테이블 참조, FK 없이 ID만 저장)
    @Column(name = "origin_id", columnDefinition = "BINARY(16)")
    private UUID originId;

    // 출고 예정일
    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    // 배송지 주소 (길어질 수 있어서 TEXT 타입)
    @Column(name = "shipping_address", columnDefinition = "TEXT")
    private String shippingAddress;

    // 비고 (길어질 수 있어서 TEXT 타입)
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    // 생성 일시
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 승인 일시
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    // 수정 일시 - 엔티티가 수정될 때마다 자동으로 현재 시간 업데이트
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 낙관락 — 동시 취소 경합 방지 (기타출고 후보 취소 시)
    // @Builder.Default 금지: Spring Data JPA의 isNew() 가 version 으로 판별해서
    // 0L 박혀있으면 신규를 merge() 로 오판 → @PrePersist 미발동 → id 누락 사고.
    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    // DB에 저장(INSERT)하기 직전에 자동으로 실행됨
    public void prePersist() {
        if (this.id == null) {
            // id가 없으면 자동으로 UUID 생성해서 넣어줌
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    // 출고지시서 승인
    public void approve(UUID approvedBy, UUID assignedTo) {
        this.approvedBy = approvedBy;
        this.assignedTo = assignedTo;
        this.status = OutboundOrderStatus.approved;
        this.approvedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // 출고지시서 취소
    public void cancel() {
        this.status = OutboundOrderStatus.cancelled;
        this.updatedAt = LocalDateTime.now();
    }

    // 출고지시서 상태 변경 (피킹 시작 등)
    public void changeStatus(OutboundOrderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    // 출고 담당자 배당 (피킹 완료 시 자동 호출)
    public void assignDispatcher(UUID userId) {
        this.assignedTo = userId;
        this.updatedAt = LocalDateTime.now();
    }

}
