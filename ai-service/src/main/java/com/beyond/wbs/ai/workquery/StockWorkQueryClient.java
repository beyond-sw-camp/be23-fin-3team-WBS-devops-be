package com.beyond.wbs.ai.workquery;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;
import java.util.Map;

@FeignClient(name = "stock-service", url = "${services.stock.url:}")
public interface StockWorkQueryClient {

    @PostMapping("/ai/work-query/execute")
    WorkQueryApiResponse execute(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody WorkQueryApiRequest request);

    record WorkQueryApiRequest(
            String intent,
            Map<String, String> slots,
            int limit) {
    }

    record WorkQueryApiResponse(
            String intent,
            List<Map<String, Object>> rows) {
    }
}
