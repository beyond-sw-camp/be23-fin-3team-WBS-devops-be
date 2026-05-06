package com.beyond.wbs.etcinout.dto;

import com.beyond.wbs.etcinout.domain.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtcInoutListResDto {

    private UUID id;
    private String orderNo;
    private IoType ioType;
    private Direction direction;
    private EtcInoutStatus status;
    private LocalDateTime createdAt;

    // 매핑용 (다른 서비스에서 이름 조회)
    private UUID warehouseId;
    private String warehouseName;
    private UUID supplierId;
    private String supplierName;
    private UUID storeId;
    private String storeName;

    // 배정 작업자 (목록에서 한눈에 보기 위해)
    private UUID assignedTo;
    private String assignedToName;

    public static EtcInoutListResDto fromEntity(EtcInoutOrder order,
                                                  String warehouseName,
                                                  String supplierName,
                                                  String storeName,
                                                  String assignedToName) {
        return EtcInoutListResDto.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .ioType(order.getIoType())
                .direction(order.getDirection())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .warehouseId(order.getWarehouseId())
                .warehouseName(warehouseName)
                .supplierId(order.getSupplierId())
                .supplierName(supplierName)
                .storeId(order.getStoreId())
                .storeName(storeName)
                .assignedTo(order.getAssignedTo())
                .assignedToName(assignedToName)
                .build();
    }
}