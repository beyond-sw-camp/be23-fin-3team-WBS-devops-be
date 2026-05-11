package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.OutboundOrderStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class OutboundOrderDetailResDto {
    // 출고 지시서 ID
    private UUID id;

    // 출고지시서 번호 (SO-00001)
    private String orderNo;

    // 창고 ID
    private UUID warehouseId;

    // 출고처 ID
    private UUID storeId;

    // 창고명 (TODO: Feign Client로 Master Service 조회)
    private String warehouseName;

    // 출고처명 (TODO: Feign Client로 Master Service 조회)
    private String storeName;

    // 생성자명 (TODO: Feign Client로 Account Service 조회)
    private String createdByName;

    // 승인자명 (TODO: Feign Client로 Account Service 조회)
    private String approvedByName;

    // 작업자 자동 배정 대상자
    private UUID assignedTo;
    private String assignedToName;

    // 출고 예정일
    private LocalDate scheduledDate;

    // 배송지
    private String shippingAddress;

    // 출고 지시서 상태
    private OutboundOrderStatus status;

    // 비고
    private String note;

    // 승인일시
    private LocalDateTime approvedAt;

    // 생성일시
    private LocalDateTime createdAt;

    // 수정일시
    private LocalDateTime updatedAt;

    // 품목 목록
    private List<OutboundOrderItemResDto> items;

    // 이 출고지시서에 연결된 피킹리스트 ID 목록
    // (웨이브 생성 시 한 지시서가 여러 SKU 로 나뉘면 피킹리스트도 여러 개가 됨)
    private List<UUID> pickingListIds;

    // 이 출고지시서가 만들어진 원본 ERP 수주서 ID 목록 (활성 링크만, 중복 제거)
    // — 출고지시서 상세 화면에서 "원본 수주서 진행률"로 역참조할 때 사용
    private List<UUID> sourceSalesOrderIds;

    // 위 ID 들의 수주서 번호 (SO-XXX). 같은 순서로 매핑.
    // 상세 화면/인쇄에서 "어떤 수주서로 만들어진 출고지시서" 인지 표시용.
    private List<String> sourceSalesOrderNos;

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
