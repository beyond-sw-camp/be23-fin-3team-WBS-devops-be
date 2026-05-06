package com.beyond.wbs.inbounds.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.beyond.wbs.inbounds.dto.CreateInboundItemDto;
import com.beyond.wbs.inbounds.dto.CreateInboundReqDto;
import com.beyond.wbs.inbounds.dto.InboundReindexResDto;
import com.beyond.wbs.inbounds.dto.InboundSearchResDto;
import com.beyond.wbs.search.inbound.InboundOrderSearchDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InboundElasticsearchIntegrationTest {

    @Autowired
    private InboundService inboundService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Test
    void createSearchAndReindexInboundOrders() throws IOException {
        UUID clientId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        CreateInboundReqDto request = new CreateInboundReqDto(
                supplierId.toString(),
                warehouseId.toString(),
                "2026-04-20",
                "manual",
                List.of(
                        new CreateInboundItemDto("SKU-ELASTIC-001", "Elasticsearch Guide Book", 15, new BigDecimal("12000")),
                        new CreateInboundItemDto("SKU-ELASTIC-002", "Search Index Notebook", 8, new BigDecimal("7000"))
                )
        );

        inboundService.createManual(request, clientId, userId);
        refreshInboundIndex();

        List<InboundSearchResDto> searchResults = inboundService.searchInboundOrders(
                clientId, null, List.of("draft"), 0, 20);

        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0).getOrderNo()).startsWith("IN-2026-");
        assertThat(searchResults.get(0).getStatus()).isEqualTo("draft");
        assertThat(searchResults.get(0).getSupplierId()).isEqualTo(supplierId);
        assertThat(searchResults.get(0).getWarehouseId()).isEqualTo(warehouseId);
        assertThat(searchResults.get(0).getTotalItems()).isEqualTo(2);
        assertThat(searchResults.get(0).getTotalOrderedQty()).isEqualTo(23);

        InboundReindexResDto reindexResult = inboundService.reindexInboundOrders(clientId);
        refreshInboundIndex();

        assertThat(reindexResult.getClientId()).isEqualTo(clientId);
        assertThat(reindexResult.getIndexedCount()).isEqualTo(1);

        List<InboundSearchResDto> reindexedResults = inboundService.searchInboundOrders(
                clientId, null, List.of("draft"), 0, 20);

        assertThat(reindexedResults).hasSize(1);
        assertThat(reindexedResults.get(0).getOrderNo()).isEqualTo(searchResults.get(0).getOrderNo());
    }

    private void refreshInboundIndex() throws IOException {
        elasticsearchClient.indices().refresh(request -> request.index(InboundOrderSearchDocument.INDEX_NAME));
    }
}
