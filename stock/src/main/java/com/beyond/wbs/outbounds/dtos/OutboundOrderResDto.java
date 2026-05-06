package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
public class OutboundOrderResDto {
    // 출고 지시서 ID
    private UUID id;

    // 출고지시서 번호 (SO-00001)
    private String orderNo;

    // 창고명 (TODO: Feign Client로 Master Service 조회)
    private String warehouseName;

    // 출고처명 (TODO: Feign Client로 Master Service 조회)
    private String storeName;

    // 출고 예정일
    private LocalDate scheduledDate;

    // 출고 지시서 상태
    private OutboundOrderStatus status;

    // 총 수량
    private Integer totalQty;

    // 생성일시
    private LocalDateTime createdAt;

    // 출처 유형 — 'sales_order' / 'manual' / 'return'
    private String originType;

    // 반품 출고 — 원본 입고지시서 ID (originType = "return" 일 때만)
    private UUID originId;

    // 반품 출고 — 원본 입고지시서 번호 (IB-XXXX, originType = "return" 일 때만)
    private String returnFromOrderNo;

    // 출고 목적지 종류 — 'store' / 'supplier'
    private String destinationType;

    // 반품 대상 입고처 (originType = "return" 일 때만)
    private UUID supplierId;

    // 반품 대상 입고처명 (UI 표시용)
    private String supplierName;

    // 반품 사유 (originType = "return" 일 때만)
    private String returnReason;
}
