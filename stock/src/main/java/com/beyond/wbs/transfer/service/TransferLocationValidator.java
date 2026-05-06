package com.beyond.wbs.transfer.service;

import com.beyond.wbs.common.client.MasterServiceClient;
import com.beyond.wbs.common.client.dto.WarehouseLocationsResDto;
import com.beyond.wbs.transfer.dto.TransferOrderItemReqDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 이동지시서 품목의 로케이션이 헤더 창고에 속하는지 검증.
 *
 * <p>호출 비용 최소화를 위해 창고별 로케이션 명단을 한 번 받아 메모리에서 멤버십 체크.
 * 창고당 Feign 1회 (출발=도착이면 1회로 단축).
 */
@Component
public class TransferLocationValidator {

    private final MasterServiceClient masterServiceClient;

    public TransferLocationValidator(MasterServiceClient masterServiceClient) {
        this.masterServiceClient = masterServiceClient;
    }

    public void validateAll(List<TransferOrderItemReqDto> items,
                            UUID fromWarehouseId, UUID toWarehouseId, UUID clientId) {
        Set<UUID> fromIds = fetchLocationIds(fromWarehouseId, clientId);
        Set<UUID> toIds = fromWarehouseId.equals(toWarehouseId)
                ? fromIds
                : fetchLocationIds(toWarehouseId, clientId);

        for (TransferOrderItemReqDto item : items) {
            if (!fromIds.contains(item.getFromLocationId())) {
                throw new IllegalArgumentException(
                        "출발 로케이션이 출발 창고에 속하지 않습니다: " + item.getFromLocationId());
            }
            if (!toIds.contains(item.getToLocationId())) {
                throw new IllegalArgumentException(
                        "도착 로케이션이 도착 창고에 속하지 않습니다: " + item.getToLocationId());
            }
        }
    }

    private Set<UUID> fetchLocationIds(UUID warehouseId, UUID clientId) {
        WarehouseLocationsResDto resp =
                masterServiceClient.getLocationsByWarehouseId(warehouseId, clientId);
        if (resp == null || resp.getItems() == null) return Collections.emptySet();
        return resp.getItems().stream()
                .map(WarehouseLocationsResDto.LocationSummaryItem::getLocationId)
                .collect(Collectors.toSet());
    }
}
