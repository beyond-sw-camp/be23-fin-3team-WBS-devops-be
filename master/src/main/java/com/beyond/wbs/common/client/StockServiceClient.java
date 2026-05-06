package com.beyond.wbs.common.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "stock-service")
public interface StockServiceClient {

    @PostMapping("/inventory/has-stock")
    boolean hasStockInLocations(@RequestBody List<UUID> locationIds,
                                 @RequestHeader("X-Client-Id") String clientId);

    /**
     * Location 별 totalQty 합계 조회 — maxCapacity 축소 시 무결성 검증용.
     */
    @PostMapping("/inventory/total-qty-by-locations")
    Map<UUID, Integer> getTotalQtyByLocations(@RequestBody List<UUID> locationIds,
                                              @RequestHeader("X-Client-Id") String clientId);
}
