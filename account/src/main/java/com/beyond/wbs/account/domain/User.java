package com.beyond.wbs.account.domain;

import com.beyond.wbs.domain.BaseTimeEntity;
import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "user")
public class User extends BaseTimeEntity {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    // User : Role = N : 1
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    private String name;
    @Column(nullable = false, unique = true)
    private String loginId;
    private String email;
    @Column(nullable = false)
    private String password;
    private String phone;

    // Developer(시스템 관리자) 여부
    @Builder.Default
    private boolean isDeveloper = false;

    @Builder.Default
    private boolean isActive = true;

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void deactivate() {
        this.isActive = false;
    }

    public void changeRole(Role role) {
        this.role = role;
    }

    public void updateInfo(String name, String email, String phone) {
        if (name != null) this.name = name;
        if (email != null) this.email = email;
        if (phone != null) this.phone = phone;
    }

    public void updateMyInfo(String email, String phone) {
        if (email != null) this.email = email;
        if (phone != null) this.phone = phone;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
