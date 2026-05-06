package com.beyond.wbs.product.controller;

import com.beyond.wbs.product.dtos.ProductGroupCreateDto;
import com.beyond.wbs.product.dtos.ProductGroupResDto;
import com.beyond.wbs.product.dtos.ProductGroupSuggestResDto;
import com.beyond.wbs.product.dtos.ProductGroupUpdateDto;
import com.beyond.wbs.product.service.ProductGroupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
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
@RequestMapping("/product/group")
public class ProductGroupController {

    private final ProductGroupService groupService;

    @Autowired
    public ProductGroupController(ProductGroupService groupService) {
        this.groupService = groupService;
    }

    @AuditLog
    @PostMapping("/create")
    public ResponseEntity<?> create(@RequestBody @Valid ProductGroupCreateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        UUID id = groupService.create(dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(id);
    }

    @AuditLog
    @GetMapping("/list")
    public ResponseEntity<?> findAll(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam(required = false) UUID categoryId,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ProductGroupResDto> list = groupService.findAll(clientId, categoryId, pageable);
        return ResponseEntity.status(HttpStatus.OK).body(list);
    }

    @AuditLog
    @GetMapping("/detail/{id}")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        ProductGroupResDto dto = groupService.findById(id);
        return ResponseEntity.status(HttpStatus.OK).body(dto);
    }

    /**
     * 회사 단위 brand distinct 목록 — 상품 검색 UI 드롭다운용.
     * 빈 brand 제외, 알파벳/가나다 정렬된 String 리스트.
     *
     * 예) GET /product/group/brands  → ["Anker", "GH Electronics", "삼성"]
     */
    @AuditLog
    @GetMapping("/brands")
    public ResponseEntity<List<String>> findBrands(
            @RequestHeader("X-Client-Id") UUID clientId) {
        return ResponseEntity.ok(groupService.findDistinctBrands(clientId));
    }

    @AuditLog
    @PatchMapping("/update/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id,
                                    @RequestBody @Valid ProductGroupUpdateDto dto,
                                    @RequestHeader("X-Client-Id") UUID clientId) {
        ProductGroupResDto res = groupService.update(id, dto, clientId);
        return ResponseEntity.status(HttpStatus.OK).body(res);
    }

    @AuditLog(action = "비활성화")
    @PutMapping("/deactivate/{id}")
    public ResponseEntity<?> deactivate(@PathVariable UUID id,
                                        @RequestHeader("X-Client-Id") UUID clientId) {
        groupService.deactivate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    @AuditLog(action = "활성화")
    @PutMapping("/activate/{id}")
    public ResponseEntity<?> activate(@PathVariable UUID id,
                                      @RequestHeader("X-Client-Id") UUID clientId) {
        groupService.activate(id, clientId);
        return ResponseEntity.status(HttpStatus.OK).body("");
    }

    /**
     * 상품 그룹 자동 제안 — 등록 폼의 [자동 제안] 버튼에서 호출.
     *
     * 선택한 카테고리 기준으로 제안 코드 + 기존 동일 카테고리의 그룹 목록(네이밍 참고용) 반환.
     * 제안값은 힌트일 뿐, 관리자가 의미있는 이름으로 덮어쓰는 걸 권장.
     */
    @AuditLog
    @GetMapping("/suggest")
    public ResponseEntity<ProductGroupSuggestResDto> suggest(
            @RequestHeader("X-Client-Id") UUID clientId,
            @RequestParam UUID categoryId) {
        ProductGroupSuggestResDto res = groupService.suggestNextGroupInfo(clientId, categoryId);
        return ResponseEntity.ok(res);
    }
}