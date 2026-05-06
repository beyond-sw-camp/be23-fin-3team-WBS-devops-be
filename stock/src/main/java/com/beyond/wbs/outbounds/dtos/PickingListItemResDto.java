package com.beyond.wbs.outbounds.dtos;

import com.beyond.wbs.outbounds.domain.PickingListItemStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
@Builder
public class PickingListItemResDto {
    // 피킹 품목 ID
    private UUID id;

    // 상품 ID
    private UUID productId;

    // SKU (Master Service Feign 조회 결과)
    private String sku;

    // 상품명 (Master Service Feign 조회 결과)
    private String productName;

    // 구역명 (TODO: Feign Client로 Master Service 조회)
    private String zoneName;

    // 랙 ID (모바일 QR 스캔 시 UUID 매칭용)
    private UUID rackId;

    // 랙코드 (TODO: Feign Client로 Master Service 조회)
    private String rackCode;

    // 로케이션 ID (특정 위치 식별용)
    private UUID locationId;

    // 표준 로케이션 코드 (예: LC-A-RK-XXX-045-04 — 행/층까지 포함된 전체 코드)
    private String locationCode;

    // 층(floor) 번호 — 같은 랙 내 다른 층 구분용
    private Integer floorNo;

    // 목표 피킹 수량
    private Integer qty;

    // 실제 피킹 수량
    private Integer pickedQty;

    // LOT 번호
    private String lotNo;

    // 피킹 상태
    private PickingListItemStatus status;

    // 피킹 완료 일시
    private LocalDateTime pickedAt;

}
