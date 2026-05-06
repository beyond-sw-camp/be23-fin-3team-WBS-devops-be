package com.beyond.wbs.account.dtos;

import com.beyond.wbs.account.domain.Client;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ClientResDto {
    private UUID id;
    private String name;
    private String bizNo;
    private boolean isActive;
    private String createdAt;

    public static ClientResDto fromEntity(Client client) {
        return ClientResDto.builder()
                .id(client.getId())
                .name(client.getName())
                .bizNo(client.getBizNo())
                .isActive(client.isActive())
                .createdAt(client.getCreatedAt() != null ? client.getCreatedAt().toLocalDate().toString() : "")
                .build();
    }
}
