package com.beyond.wbs.account.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@ToString
@Entity
public class Client {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    private String name;
    private String bizNo;
    @Builder.Default
    private boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void deactivate() {
        this.isActive = false;
    }

    public void updateInfo(String name, String bizNo) {
        if (name != null) this.name = name;
        if (bizNo != null) this.bizNo = bizNo;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
