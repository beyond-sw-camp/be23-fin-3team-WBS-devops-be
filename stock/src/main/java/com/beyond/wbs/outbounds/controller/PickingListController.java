package com.beyond.wbs.outbounds.controller;

import com.beyond.wbs.auth.Action;
import com.beyond.wbs.auth.CheckPermission;
import com.beyond.wbs.auth.Resource;
import com.beyond.wbs.outbounds.domain.PickingListStatus;
import com.beyond.wbs.outbounds.dtos.PickItemReqDto;
import com.beyond.wbs.outbounds.dtos.PickingListItemResDto;
import com.beyond.wbs.outbounds.dtos.PickingListSearchReqDto;
import com.beyond.wbs.outbounds.dtos.ProductIdsReqDto;
import com.beyond.wbs.outbounds.dtos.WaveCreateReqDto;
import com.beyond.wbs.outbounds.service.PickingListService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.beyond.wbs.audit.AuditLog;

@RestController
@RequestMapping("/pickingList")
public class PickingListController {

    private final PickingListService pickingListService;

    @Autowired
    public PickingListController(PickingListService pickingListService) {
        this.pickingListService = pickingListService;
    }

    // 웨이브(배치) 생성 (피킹리스트 자동 생성)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.CREATE)
    @AuditLog(action = "웨이브생성")
    @PostMapping("/wave")
    public ResponseEntity<?> createWave(@RequestHeader("X-User-Id") String userId,
                                        @RequestHeader("X-Client-Id") String clientId,
                                        @RequestBody @Valid WaveCreateReqDto dto){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pickingListService.createWave(dto, userUUID, clientUUID));

    }

    // 피킹리스트 목록조회 (페이징 처리)
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/findAll")
    public ResponseEntity<?> getPickingLists(
            @RequestParam(required = false) PickingListStatus status,
            @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(pickingListService.getPickingLists(clientUUID, userUUID, warehouseId, status, null, pageable));
    }

    /**
     * 피킹리스트 멀티필터 검색 — productIds 등 body 로 받는 POST 검색.
     *
     * 흐름:
     *   ① ProductSearchFilterModal → /master-service/product/search-advanced 로 productIds 수집
     *   ② 그 productIds 와 기존 status/warehouseId 를 본 엔드포인트 body 로 전달
     *   ③ EXISTS 서브쿼리로 "해당 상품 라인이 1개 이상 포함된 피킹리스트"만 반환
     *
     * 기존 GET /findAll 은 그대로 유지 (단순 status/warehouse 필터용).
     * Body 의 모든 필드 옵셔널.
     *
     * Body 예) { "status": "in_progress", "productIds": ["uuid1"] }
     */
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/search")
    public ResponseEntity<?> searchPickingLists(
            @RequestBody PickingListSearchReqDto dto,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-Client-Id") String clientId) {
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        PickingListStatus status = dto != null ? dto.getStatus() : null;
        UUID warehouseId = dto != null ? dto.getWarehouseId() : null;
        List<UUID> productIds = dto != null ? dto.getProductIds() : null;
        return ResponseEntity.ok(pickingListService.getPickingLists(
                clientUUID, userUUID, warehouseId, status, null, productIds, pageable));
    }

    // 피킹리스트 상세조회
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> getPickingList(@PathVariable UUID id,
                                            @RequestHeader("X-User-Id") String userId,
                                            @RequestHeader("X-Client-Id") String clientId){
        UUID userUUID = UUID.fromString(userId);
        UUID clientUUID = UUID.fromString(clientId);
        return ResponseEntity.ok(pickingListService.getPickingList(id, userUUID, clientUUID));
    }

    /**
     * 피킹리스트 내 상품 라인 검색 — 상품 ID 필터 적용 (피킹리스트 상세 내 검색).
     *
     * 흐름: 프론트가 ProductSearchFilterModal 의 condition 으로
     *       /master-service/product/search-advanced 호출 → productIds 수집 →
     *       본 엔드포인트에 body 로 전달.
     *
     * Body 예) { "productIds": ["uuid1", "uuid2"] }
     * productIds 가 비어있으면 전체 라인 반환.
     */
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @PostMapping("/{id}/items/search")
    public ResponseEntity<List<PickingListItemResDto>> searchPickingItems(
            @PathVariable UUID id,
            @RequestBody ProductIdsReqDto dto,
            @RequestHeader("X-Client-Id") String clientId) {
        List<PickingListItemResDto> items = pickingListService.getPickingItemsByProductIds(
                id,
                dto != null ? dto.getProductIds() : null,
                UUID.fromString(clientId));
        return ResponseEntity.ok(items);
    }

    // 피킹리스트 QR 생성 - 피킹리스트 ID를 String으로 변환해서 반환하는 조회작업
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.READ)
    @AuditLog
    @GetMapping("/{id}/qr")
    public ResponseEntity<?> getPickingListQr(@PathVariable UUID id){
        return ResponseEntity.ok(pickingListService.getPickingListQr(id));
    }

    // 품목별 피킹 수량 수동입력
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.UPDATE)
    @AuditLog
    @PatchMapping("/{id}/items/{itemId}/pick")
    public ResponseEntity<?> pickItem(@PathVariable UUID id,
                                      @PathVariable UUID itemId,
                                      @RequestBody @Valid PickItemReqDto dto,
                                      @RequestHeader("X-User-Id") String userId){
        UUID userUUID = UUID.fromString(userId);
        pickingListService.pickItem(id, itemId, dto, userUUID);
        return ResponseEntity.ok().build();
    }

    // 피킹리스트 완료 처리
    @CheckPermission(resource = Resource.OUTBOUND, action = Action.UPDATE)
    @AuditLog(action = "완료")
    @PatchMapping("/{id}/complete")
    public ResponseEntity<?> completePickingList(@PathVariable UUID id,
                                                 @RequestHeader("X-User-Id") String userId){
        UUID userUUID = UUID.fromString(userId);
        pickingListService.completePickingList(id, userUUID);
        return ResponseEntity.ok().build();
    }
}
