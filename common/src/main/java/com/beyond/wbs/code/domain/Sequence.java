package com.beyond.wbs.code.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 채번 시퀀스 테이블
 *
 * type 별로 현재 번호(current_val)를 관리한다.
 * NumberingUtil 에서 비관적 락으로 조회 후 +1 하여 중복 없는 순번을 보장.
 */
@NoArgsConstructor
@AllArgsConstructor
@Data
@Entity
@Table(name = "sequences")
public class Sequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 채번 타입 (order, picking, dispatch, inbound, placement, receipt, etc_inout, stock_count, transfer, transfer_cluster)
    @Column(name = "type", nullable = false, unique = true)
    private String type;

    // 현재 번호
    @Column(name = "current_val", nullable = false)
    private Long currentVal;
}
