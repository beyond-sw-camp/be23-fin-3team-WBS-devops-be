package com.beyond.wbs.search.inbound;

import com.beyond.wbs.inbounds.domain.InboundOrderItems;
import com.beyond.wbs.inbounds.domain.InboundOrders;
import com.beyond.wbs.inbounds.repository.InboundOrderItemRepository;
import com.beyond.wbs.inbounds.repository.InboundOrderRepository;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.DeleteResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class InboundOrderSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final InboundOrderRepository inboundOrderRepository;
    private final InboundOrderItemRepository inboundOrderItemRepository;

    public InboundOrderSearchService(
            ElasticsearchClient elasticsearchClient,
            InboundOrderRepository inboundOrderRepository,
            InboundOrderItemRepository inboundOrderItemRepository) {
        this.elasticsearchClient = elasticsearchClient;
        this.inboundOrderRepository = inboundOrderRepository;
        this.inboundOrderItemRepository = inboundOrderItemRepository;
    }

    @PostConstruct
    public void ensureIndex() {
        try {
            BooleanResponse exists = elasticsearchClient.indices()
                    .exists(request -> request.index(InboundOrderSearchDocument.INDEX_NAME));

            if (!exists.value()) {
                elasticsearchClient.indices()
                        .create(request -> request.index(InboundOrderSearchDocument.INDEX_NAME));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to ensure inbound Elasticsearch index.", e);
        }
    }

    public InboundOrderSearchDocument index(UUID orderId) {
        InboundOrders order = inboundOrderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("입고 지시서를 찾을 수 없습니다."));
        List<InboundOrderItems> items = inboundOrderItemRepository.findByInboundOrderId(orderId);
        return index(order, items);
    }

    public InboundOrderSearchDocument index(InboundOrders order, List<InboundOrderItems> items) {
        InboundOrderSearchDocument document = InboundOrderSearchDocument.from(order, items);

        try {
            IndexResponse response = elasticsearchClient.index(request -> request
                    .index(InboundOrderSearchDocument.INDEX_NAME)
                    .id(document.getId())
                    .document(document)
            );

            if (response.result() == null) {
                throw new IllegalStateException("Inbound Elasticsearch indexing returned no result.");
            }
            return document;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to index inbound order into Elasticsearch.", e);
        }
    }

    public int reindexByClient(UUID clientId) {
        List<InboundOrders> orders = inboundOrderRepository.findByClientId(clientId);

        for (InboundOrders order : orders) {
            List<InboundOrderItems> items = inboundOrderItemRepository.findByInboundOrderId(order.getId());
            index(order, items);
        }

        return orders.size();
    }

    public void delete(UUID orderId) {
        try {
            DeleteResponse response = elasticsearchClient.delete(request -> request
                    .index(InboundOrderSearchDocument.INDEX_NAME)
                    .id(orderId.toString())
            );

            if (response.result() == null) {
                throw new IllegalStateException("Inbound Elasticsearch delete returned no result.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete inbound order from Elasticsearch.", e);
        }
    }

    public List<InboundOrderSearchDocument> search(InboundSearchQuery searchQuery) {
        int safePage = Math.max(searchQuery.getPage(), 0);
        int safeSize = Math.max(searchQuery.getSize(), 1);
        List<String> safeStatuses = searchQuery.getStatuses() != null ? searchQuery.getStatuses() : Collections.emptyList();

        try {
            SearchResponse<InboundOrderSearchDocument> response = elasticsearchClient.search(search -> search
                            .index(InboundOrderSearchDocument.INDEX_NAME)
                            .from(safePage * safeSize)
                            .size(safeSize)
                            .query(q -> q.bool(bool -> {
                                bool.filter(filter -> filter.term(term -> term
                                        .field("clientId.keyword")
                                        .value(FieldValue.of(searchQuery.getClientId().toString()))
                                ));

                                if (!safeStatuses.isEmpty()) {
                                    bool.filter(filter -> filter.terms(terms -> terms
                                            .field("status.keyword")
                                            .terms(values -> values.value(
                                                    safeStatuses.stream().map(FieldValue::of).toList()
                                            ))
                                    ));
                                }

                                if (StringUtils.hasText(searchQuery.getKeyword())) {
                                    bool.must(must -> must.multiMatch(multiMatch -> multiMatch
                                            .query(searchQuery.getKeyword())
                                            .fields("orderNo", "title", "summary", "note", "itemNames")
                                    ));
                                }

                                return bool;
                            })),
                    InboundOrderSearchDocument.class
            );

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(document -> document != null)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to search inbound orders in Elasticsearch.", e);
        }
    }
}
