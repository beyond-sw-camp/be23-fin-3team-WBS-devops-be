package com.beyond.wbs.product.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter @ToString
@Builder
@Entity
@Table(name = "product_option_values")
public class ProductOptionValue {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "option_type_id", nullable = false)
    private ProductOptionType optionType;

    @Column(length = 100, nullable = false)
    private String value;     // 예: 블랙

    @Column(length = 50, nullable = false)
    private String code;      // 예: BLACK

    @Column(nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }
}