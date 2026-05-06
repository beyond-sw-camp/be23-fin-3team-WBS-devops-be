package com.beyond.wbs.etcinout.dto;

import com.beyond.wbs.etcinout.domain.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutDetailResDto {

    // 기록 정보
    private UUID id;
    private String orderNo;
    private IoType ioType;
    private Direction direction;
    private EtcInoutStatus status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 참조 정보
    private UUID createdBy;
    private UUID clientId;
    private UUID warehouseId;
    private String warehouseName;
    private UUID supplierId;
    private String supplierName;
    private UUID storeId;
    private String storeName;
    private UUID refId;
    private RefType refType;

    // 흐름 (1:1)
    private UUID assignedTo;
    private String assignedToName;
    private UUID approvedBy;
    private LocalDateTime approvedAt;
    private UUID completedBy;
    private LocalDateTime completedAt;

    public static EtcInoutDetailResDto fromEntity(EtcInoutOrder order,
                                                    String warehouseName,
                                                    String supplierName,
                                                    String storeName,
                                                    String assignedToName) {
        return EtcInoutDetailResDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .ioType(order.getIoType())
                .direction(order.getDirection())
                .status(order.getStatus())
                .note(order.getNote())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .createdBy(order.getCreatedBy())
                .clientId(order.getClientId())
                .warehouseId(order.getWarehouseId())
                .warehouseName(warehouseName)
                .supplierId(order.getSupplierId())
                .supplierName(supplierName)
                .storeId(order.getStoreId())
                .storeName(storeName)
                .refId(order.getRefId())
                .refType(order.getRefType())
                .assignedTo(order.getAssignedTo())
                .assignedToName(assignedToName)
                .approvedBy(order.getApprovedBy())
                .approvedAt(order.getApprovedAt())
                .completedBy(order.getCompletedBy())
                .completedAt(order.getCompletedAt())
                .build();
    }
}