package com.beyond.wbs.inbounds.dto;

import com.beyond.wbs.inbounds.domain.ErpPurchaseOrders;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class AsnOrderResDto {
    private UUID id;
    private String asnNo;
    private String supplierName;
    private String shipDate;
    private String expectedDate;
    private List<AsnItemResDto> items;

    // 모든 품목의 상품이 Master 에 등록되어 있는지.
    // false 이면 프론트에서 "입고지시서 생성" 버튼 비활성화 + 등록 유도.
    private Boolean allMatched;

    public static AsnOrderResDto fromEntity(ErpPurchaseOrders order, String supplierName, List<AsnItemResDto> items) {
        boolean all = items != null && !items.isEmpty()
                && items.stream().allMatch(i -> Boolean.TRUE.equals(i.getMatched()));
        return AsnOrderResDto.builder()
                .id(order.getId())
                .asnNo(order.getPoNo())
                .supplierName(supplierName)
                .shipDate(order.getOrderDate().toString())
                .expectedDate(order.getScheduledDate().toString())
                .items(items)
                .allMatched(all)
                .build();
    }
}
