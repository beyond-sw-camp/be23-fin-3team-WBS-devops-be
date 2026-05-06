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
@Table(
    name = "role",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "code"})
)
public class Role {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    private String description;

    @Builder.Default
    private boolean isActive = true;

    // 시스템 기본 역할 여부 (ADMIN, MANAGER, OPERATOR)
    // 기본 역할은 삭제 불가
    @Builder.Default
    private boolean isSystem = false;

    private LocalDateTime createdAt;

    public void updateInfo(String name, String description) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
