package com.beyond.wbs.outbounds.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
@Entity
@Table(name = "outbound_pickinglists")
public class OutboundPickinglist {

    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    // 출고 지시서 ID (Stock Service 내부 참조)
    @Column(name = "outbound_order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID outboundOrderId;

    // 피킹 리스트 ID (Stock Service 내부 참조)
    @Column(name = "picking_list_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID pickingListId;

    @PrePersist
    // DB에 저장(INSERT)하기 직전에 자동으로 실행됨
    public void prePersist() {
        if (this.id == null) {
            // id가 없으면 자동으로 UUID 생성해서 넣어줌
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
    }

}
