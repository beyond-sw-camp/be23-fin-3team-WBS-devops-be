package com.beyond.wbs.scheduler.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 스케줄러 실행 이력 상세.
 *
 * 한 행 = (OB × SO) 한 쌍. 분할 출고면 같은 OB 에 대해 SO 수만큼 행이 들어감.
 * 검색: outbound_order_id, sales_order_id 모두 인덱스로 빠름.
 * FE 화면에선 OB 단위로 그룹핑해서 표시.
 */
@Entity
@Table(name = "scheduler_history_detail", indexes = {
        @Index(name = "idx_sched_detail_history", columnList = "history_id"),
        @Index(name = "idx_sched_detail_ob", columnList = "outbound_order_id"),
        @Index(name = "idx_sched_detail_so", columnList = "sales_order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SchedulerHistoryDetail {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "history_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID historyId;

    @Column(name = "warehouse_id", columnDefinition = "BINARY(16)")
    private UUID warehouseId;

    @Column(name = "warehouse_name", length = 100)
    private String warehouseName;

    @Column(name = "outbound_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID outboundOrderId;

    @Column(name = "order_no", length = 30)
    private String orderNo;

    @Column(name = "store_id", columnDefinition = "BINARY(16)")
    private UUID storeId;

    @Column(name = "store_name", length = 100)
    private String storeName;

    @Column(name = "sales_order_id", columnDefinition = "BINARY(16)")
    private UUID salesOrderId;

    @Column(name = "so_no", length = 30)
    private String soNo;

    /** 이 OB 가 속한 웨이브 (PickingList) — FE 가 클릭 시 피킹리스트 상세로 이동 */
    @Column(name = "picking_list_id", columnDefinition = "BINARY(16)")
    private UUID pickingListId;

    @Column(name = "picking_no", length = 30)
    private String pickingNo;

    @PrePersist
    private void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

    @Builder
    public SchedulerHistoryDetail(UUID historyId, UUID warehouseId, String warehouseName,
                                   UUID outboundOrderId, String orderNo,
                                   UUID storeId, String storeName,
                                   UUID salesOrderId, String soNo,
                                   UUID pickingListId, String pickingNo) {
        this.historyId = historyId;
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.outboundOrderId = outboundOrderId;
        this.orderNo = orderNo;
        this.storeId = storeId;
        this.storeName = storeName;
        this.salesOrderId = salesOrderId;
        this.soNo = soNo;
        this.pickingListId = pickingListId;
        this.pickingNo = pickingNo;
    }
}
