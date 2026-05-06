package com.beyond.wbs.search.inbound;

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
public class InboundSearchQuery {

    private UUID clientId;
    private String keyword;
    private List<String> statuses;
    private int page;
    private int size;
}
