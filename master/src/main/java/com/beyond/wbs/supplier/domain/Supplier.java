package com.beyond.wbs.supplier.domain;

import com.beyond.wbs.code.MasterCodePolicy;
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
@Table(name = "suppliers",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"client_id", "code"})
        }
)
public class Supplier extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "client_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientId;

    @Column(length = 100, nullable = false)
    private String name;        // 협력사명

    @Column(length = 20)
    private String bizNo;       // 사업자번호

    @Column(length = MasterCodePolicy.MAX_LEN, nullable = false)
    private String code;        // 협력사코드

    @Column(length = 50)
    private String ceoName;     // 대표자명

    @Column(length = 20)
    private String tel;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(length = 2)
    private String esgGrade;    // ESG 참고 등급

    @Builder.Default
    @Column(nullable = false)
    private Boolean ecoCertified = false;   // 친환경 인증 여부

    @Column(length = 500)
    private String esgMemo;      // 관리자 참고 메모

    @Builder.Default
    @Column(nullable = false)
    private Boolean isActive = true;

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    public void update(
            String name,
            String bizNo,
            String ceoName,
            String tel,
            String email,
            String address,
            String esgGrade,
            Boolean ecoCertified,
            String esgMemo
    ) {
        this.name = name;
        this.bizNo = bizNo;
        this.ceoName = ceoName;
        this.tel = tel;
        this.email = email;
        this.address = address;
        this.esgGrade = esgGrade;
        this.ecoCertified = ecoCertified != null ? ecoCertified : false;
        this.esgMemo = esgMemo;
    }

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}
