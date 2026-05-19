# 2D 창고 레이아웃 시스템 기술 명세서

> **목적**: 창고 도면을 캔버스(픽셀) 좌표로 시각화·편집하고, 도메인(상품·재고)과 연결
> **모듈**: master (도메인·API), stock (재고 검증 연동)
> **작성일**: 2026-05-08

---

## 1. 개요

### 1.1 4단계 계층

```
Warehouse (창고)
   ↓ OneToOne
WarehouseLayout         캔버스 크기·배경색만 저장 (좌표 0,0 기준)
   ↓ children: List<Zone>
Zone (구역)
   ↓ OneToOne
ZoneLayout              캔버스 절대좌표 (posX, posY, width, height)
   ↓ children: List<Rack>
Rack (랙)
   ↓ OneToMany (역참조)
RackLayout              부모 ZoneLayout 기준 상대좌표
   ↓ children: List<Location>
Location (위치/층)        좌표 저장 없음 (RackLayout이 대표)
```

### 1.2 핵심 설계 결정

| 결정 | 이유 |
|---|---|
| Zone = **절대좌표**, Rack = **상대좌표** | Zone 이동 시 자식 Rack이 자동 함께 이동 |
| 일괄 저장 (upsert + delete) | 다중 사용자 동시 편집 안전성 |
| 좌표 단위 = **픽셀(px)** | 프론트엔드 캔버스 그대로 매핑 |
| 비활성화·협력사 변경 시 재고 검증 | 데이터 정합성 (관련 재고 있으면 차단) |
| 낙관적 락 미사용 | 명시적 ID 기반 upsert로 충돌 회피 |

---

## 2. 도메인 엔티티

### 2.1 WarehouseLayout

📂 `master/src/main/java/com/beyond/wbs/layout/domain/WarehouseLayout.java`

```sql
CREATE TABLE warehouse_layouts (
  id BINARY(16) PRIMARY KEY,
  warehouse_id BINARY(16) NOT NULL,    -- FK → warehouses.id
  canvas_width INT NOT NULL,            -- 캔버스 가로 (px)
  canvas_height INT NOT NULL,           -- 캔버스 세로 (px)
  bg_color VARCHAR(10),                 -- 배경 HEX (#1a1d23)
  updated_at DATETIME NOT NULL
);
```

- Warehouse와 **OneToOne**
- 캔버스 자체의 크기·배경만 저장

### 2.2 ZoneLayout

📂 `master/src/main/java/com/beyond/wbs/layout/domain/ZoneLayout.java`

```sql
CREATE TABLE zone_layouts (
  id BINARY(16) PRIMARY KEY,
  zone_id BINARY(16) NOT NULL,         -- FK → zones.id
  pos_x INT NOT NULL,                   -- 캔버스 기준 절대 X
  pos_y INT NOT NULL,                   -- 캔버스 기준 절대 Y
  width INT NOT NULL,
  height INT NOT NULL,
  rotation INT NOT NULL DEFAULT 0,      -- 회전각 (도)
  color VARCHAR(10),
  sort_order INT NOT NULL DEFAULT 0,    -- 정렬 순서 (z-index 유사)
  updated_at DATETIME NOT NULL
);
```

**좌표 체계**: 캔버스(0,0) 기준 **절대좌표**.

### 2.3 RackLayout

📂 `master/src/main/java/com/beyond/wbs/layout/domain/RackLayout.java`

```sql
CREATE TABLE rack_layouts (
  id BINARY(16) PRIMARY KEY,
  rack_id BINARY(16) NOT NULL,         -- FK → racks.id
  zone_id BINARY(16) NOT NULL,         -- 어느 ZoneLayout에 속할지
  pos_x INT NOT NULL,                   -- 부모 Zone 기준 상대 X
  pos_y INT NOT NULL,                   -- 부모 Zone 기준 상대 Y
  width INT NOT NULL,
  height INT NOT NULL,
  rotation INT NOT NULL DEFAULT 0,      -- 0/90/180/270
  color VARCHAR(10),
  updated_at DATETIME NOT NULL
);
```

**좌표 체계**: 부모 ZoneLayout의 (posX, posY) 기준 **상대좌표**.

**렌더링 시 절대좌표 변환**:
```
absoluteX = ZoneLayout.posX + RackLayout.posX
absoluteY = ZoneLayout.posY + RackLayout.posY
```

### 2.4 ShapeLayout (계획 중)

❌ **현재 미구현** — 통로(CORRIDOR), 출입구(ENTRANCE) 등 도형 표시.
Postman 컬렉션에 API 명세만 정의되어 있음.

---

## 3. API 엔드포인트

📂 `master/src/main/java/com/beyond/wbs/layout/controller/LayoutController.java`

**Base URL**: `/layout`

### 3.1 Warehouse 레이아웃

| Method | Path | 용도 |
|---|---|---|
| `PUT` | `/layout/warehouse/save` | 저장 (신규 또는 수정) |
| `GET` | `/layout/warehouse/{warehouseId}` | 조회 |
| `POST` | `/layout/warehouse/save` | (Legacy, 호환용) |

### 3.2 Zone 레이아웃 (일괄)

| Method | Path | 용도 |
|---|---|---|
| `PUT` | `/layout/zone/save` | **일괄 저장** (upsert + delete) |
| `GET` | `/layout/zone/{warehouseId}` | 창고의 모든 ZoneLayout 조회 |
| `POST` | `/layout/zone/save` | (Legacy 단건) |

### 3.3 Rack 레이아웃 (일괄)

| Method | Path | 용도 |
|---|---|---|
| `PUT` | `/layout/rack/save` | **일괄 저장** (upsert + delete) |
| `GET` | `/layout/rack/{zoneId}` | 구역의 모든 RackLayout 조회 |
| `GET` | `/layout/rack/warehouse/{warehouseId}` | 창고의 모든 RackLayout 조회 |
| `POST` | `/layout/rack/save` | (Legacy 단건) |

### 3.4 Shape (미구현)

| Method | Path | 용도 |
|---|---|---|
| `GET` | `/layout/shape/{warehouseId}` | 도형 조회 |
| `PUT` | `/layout/shape/save` | 일괄 저장 |
| `DELETE` | `/layout/shape/{shapeId}` | 도형 삭제 |

---

## 4. DTO / 요청 형식

### 4.1 ZoneLayoutSaveDto (일괄 저장)

📂 `master/.../layout/dtos/ZoneLayoutSaveDto.java`

```json
{
  "warehouseId": "<UUID>",
  "items": [
    {
      "zoneId": "<UUID>",
      "posX": 50,
      "posY": 100,
      "width": 400,
      "height": 300,
      "rotation": 0,
      "color": "#2a4d6e",
      "sortOrder": 0
    }
  ],
  "deletedZoneIds": ["<UUID>", "<UUID>"]
}
```

### 4.2 RackLayoutSaveDto (일괄 저장)

📂 `master/.../layout/dtos/RackLayoutSaveDto.java`

```json
{
  "zoneId": "<UUID>",
  "warehouseId": "<UUID>",
  "items": [
    {
      "rackId": "<UUID>",
      "posX": 10,
      "posY": 10,
      "width": 60,
      "height": 40,
      "rotation": 0,
      "color": "#888"
    }
  ],
  "deletedRackIds": ["<UUID>"]
}
```

### 4.3 응답 (Layout ResDto 공통 형태)

```json
{
  "id": "<UUID>",
  "zoneId": "<UUID>",
  "zoneName": "포장존",
  "posX": 50,
  "posY": 100,
  "width": 400,
  "height": 300,
  "rotation": 0,
  "color": "#2a4d6e",
  "sortOrder": 0
}
```

→ `RackLayoutResDto` 도 동일 패턴 (rackId, rackName 포함).

---

## 5. 일괄 저장 로직 (Upsert + Delete)

📂 `master/src/main/java/com/beyond/wbs/layout/service/LayoutService.java`

### 5.1 saveZoneLayouts() 동작

```
1. Warehouse 검증 (clientId 일치)
2. Upsert 단계:
   for item in items:
     existing = repo.findByZoneId(item.zoneId)
     if existing.isPresent():
        existing.update(posX, posY, ...)  // UPDATE
     else:
        repo.save(new ZoneLayout(...))    // INSERT
3. Delete 단계:
   for zoneId in deletedZoneIds:
     existing = repo.findByZoneId(zoneId)
     existing.ifPresent(repo::delete)     // DELETE
```

**안전성**:
- 요청에 없는 다른 ZoneLayout은 **건드리지 않음**
- 시나리오: 사용자 A가 Zone1 수정 중, 사용자 B가 Zone2 추가 → 충돌 없음

### 5.2 saveRackLayouts() 추가 검증

- Rack의 zone이 요청 zoneId와 일치하는지 확인
- 불일치 시 `IllegalArgumentException("랙의 소속 구역이 일치하지 않습니다")`

---

## 6. 방어 로직 (LittleNiddle 기여)

### 6.1 랙 비활성화 시 재고 검증

📂 `master/src/main/java/com/beyond/wbs/rack/service/RackService.java:221-237`

```java
public void deactivate(UUID id, UUID clientId) {
    Rack rack = ...;
    List<Location> locations = locationRepository.findByRackId(rack.getId());

    // stock-service에 "이 위치들에 재고가 있나?" 질문
    boolean hasStock = stockServiceClient.hasStockInLocations(
        locations.stream().map(Location::getId).toList(),
        clientId
    );

    if (hasStock) {
        throw new IllegalStateException(
            "재고가 남아있는 랙은 비활성화할 수 없습니다. 재고를 다른 랙으로 이동 후 시도하세요.");
    }

    rack.deactivate();
}
```

**의도**: 비활성화된 랙에 재고가 남아 있으면 적치/조회 시 데이터 정합성 깨짐 → 사전 차단.

### 6.2 협력사 변경 시 재고 검증

📂 `master/.../rack/service/RackService.java:151-219`

```java
if (!Objects.equals(currentSupplierId, nextSupplierId)) {
    boolean hasStock = stockServiceClient.hasStockInLocations(...);
    if (hasStock) {
        throw new IllegalStateException(
            "재고가 있는 랙은 협력사를 변경할 수 없습니다. 재고를 비운 후 시도하세요.");
    }
}
```

**의도**: 카테고리 혼입 방지 (A협력사 상품 + B협력사 상품 섞이면 안 됨).

### 6.3 통로/출입구 삭제 검증 (계획)

❌ 현재 미구현. 향후 도입 시:
- 도형 영역과 겹치는 Rack/Location이 있으면 삭제 거부
- 작업 진행 중인 랙이 있으면 변경 차단

### 6.4 랙 잠금 / 잠금 해제 (커밋 이력)

LittleNiddle의 커밋 이력에 랙 잠금 관련 작업이 있음:
- `65531e3` 랙별 잠금 수정
- `81f54a8` 랙 잠금 삭제

→ 작업 진행 중인 랙을 다른 작업이 점유하지 못하게 하는 동시성 보호.

---

## 7. 상품 / 랙 물리 치수 (Metadata)

LittleNiddle 커밋 `47a9e87` "상품, 랙에 가로/세로/높이 설정" 으로 추가된 메타데이터.

### 7.1 Rack 물리 치수

📂 `master/src/main/java/com/beyond/wbs/rack/domain/Rack.java:51-59`

| 컬럼 | 단위 | 의미 |
|---|---|---|
| `width_mm` | mm | 가로 |
| `depth_mm` | mm | 세로 |
| `height_mm` | mm | 높이 |

→ 현재는 **참고용**. 적치 알고리즘에서 미사용.

### 7.2 Product 물리 치수

📂 `master/src/main/java/com/beyond/wbs/product/domain/Product.java:70-80`

| 컬럼 | 단위 | 제약 |
|---|---|---|
| `weight` | kg (BigDecimal 10,3) | nullable |
| `width` | cm (BigDecimal 10,2) | NOT NULL |
| `depth` | cm (BigDecimal 10,2) | NOT NULL |
| `height` | cm (BigDecimal 10,2) | NOT NULL |

→ 모든 상품 필수.

### 7.3 향후 확장: 3D 적치 알고리즘

```
현재: Location.maxCapacity (수량 기반) 만으로 추천
미래: Product 부피 vs Rack 공간 비교 → BinPacking 알고리즘
```

---

## 8. 권한 / 보안

📂 `master/.../layout/service/LayoutService.java:54-76`

### 8.1 검증 체계

```java
// 모든 API 호출 시
1. X-Client-Id 헤더 추출
2. Warehouse.clientId와 일치 확인 → 불일치면 SecurityException
3. Entity 존재 검증 → 없으면 EntityNotFoundException
4. 계층 일관성 검증
   - Zone.warehouse == 요청 warehouseId
   - Rack.zone == 요청 zoneId
```

### 8.2 감사 로그

```java
@AuditLog                                // 자동 추출 (PUT → "수정")
@PutMapping("/zone/save")
public ResponseEntity<?> saveZoneLayouts(...) { ... }

@AuditLog(action = "비활성화")           // 명시적 액션
@PatchMapping("/deactivate/{id}")
public ResponseEntity<?> deactivate(...) { ... }
```

---

## 9. 트랜잭션 / 동시성

📂 `master/.../layout/service/LayoutService.java:27-29`

```java
@Service
@Transactional
public class LayoutService { ... }

@Transactional(readOnly = true)
public List<ZoneLayoutResDto> findZoneLayouts(UUID warehouseId) { ... }
```

**낙관적 락 미사용**:
- ID 기반 upsert로 충돌 회피
- 동일 Zone을 두 사용자가 동시 수정 → 마지막 요청이 덮어씀 (의도된 설계, "last writer wins")

---

## 10. 프론트엔드 연동 흐름

### 10.1 좌표 단위

- 단위 = **픽셀 (px)**
- WarehouseLayout, ZoneLayout, RackLayout 모두 동일

### 10.2 캔버스 → JSON → API

```
[프론트엔드 캔버스 (SVG/Canvas)]
   사용자가 Zone 박스 드래그: pos=(50,100), size=(400,300)
        ↓
[JSON 직렬화]
   ZoneLayoutSaveDto.items[]
        ↓
[PUT /layout/zone/save]
        ↓
[서버: upsert + delete]
        ↓
[응답 ResDto]
        ↓
[프론트: 캔버스 재렌더링]
```

### 10.3 Zone 이동 = Rack 자동 이동

```
시나리오: Zone A를 (50,100) → (100,200) 이동

요청: PUT /layout/zone/save
{ items: [{zoneId, posX:100, posY:200, ...}] }

서버:
- ZoneLayout만 UPDATE (Rack은 건드리지 않음)

프론트엔드 렌더링:
- Zone A 새 좌표 (100,200) 적용
- Zone A의 자식 Rack들은 상대좌표 그대로 → 자동으로 함께 이동!
  - 예: Rack(상대 10,10) → 절대좌표 (100+10, 200+10) = (110, 210)
```

→ Rack 위치 데이터 변경 0건. 효율적·일관적.

---

## 11. 통합 시나리오 예시

### 11.1 새 창고 레이아웃 생성

```
1. GET /layout/warehouse/{warehouseId} → null (아직 없음)
2. PUT /layout/warehouse/save
   { warehouseId, canvasWidth: 1200, canvasHeight: 800, bgColor: "#1a1d23" }
3. → 서버: WarehouseLayout INSERT
4. PUT /layout/zone/save
   { warehouseId, items: [Zone A, Zone B] }
5. → 서버: ZoneLayout 2건 INSERT
6. PUT /layout/rack/save (Zone A의 랙들)
   { zoneId, items: [Rack 1, Rack 2] }
7. → 서버: RackLayout 2건 INSERT
```

### 11.2 협력사 변경 차단 시나리오

```
1. PATCH /rack/update/{rackId}
   { supplierId: "vendor-B" }   # 기존: vendor-A
2. RackService.update()
   - currentSupplier = vendor-A → newSupplier = vendor-B (변경 감지)
   - StockServiceClient.hasStockInLocations(...) → true
3. → IllegalStateException: "재고가 있는 랙은 협력사를 변경할 수 없습니다"
4. → 클라이언트에 400 + 에러 메시지 표시
```

---

## 12. Repository 최적화 쿼리

📂 `master/.../layout/repository/RackLayoutRepository.java`

```java
// 활성 랙만 + zone 활성 + zone-warehouse JOIN
List<RackLayout> findByZoneIdAndRack_IsActiveTrueAndZone_IsActiveTrue(UUID zoneId);

// 창고 단위 활성 랙
List<RackLayout> findByZone_WarehouseIdAndRack_IsActiveTrueAndZone_IsActiveTrue(UUID warehouseId);
```

**효과**: 비활성화된 랙·존을 자동 필터링 → 캔버스에 활성 객체만 표시.

---

## 13. 미구현·로드맵

| 기능 | 상태 | 복잡도 |
|---|---|---|
| Shape (도형: 복도/출입구) 엔티티 | ❌ | 중 |
| Shape 일괄 저장 API | ❌ | 중 |
| Shape vs Rack 충돌 감지 | ❌ | 높 |
| 적치 추천 3D 알고리즘 (BinPacking) | ❌ | 높 |
| 레이아웃 다중 버전(롤백) | ❌ | 높 |
| 좌표 회전 변환 활용 (rotation 90·180·270) | 부분 | 중 |
| 실시간 동시 편집 (CRDT/OT) | ❌ | 매우 높 |
| 모바일 캔버스 (작업자 PDA) | ❌ | 중 |

---

## 14. 핵심 파일 위치

### 도메인 엔티티
- `master/.../layout/domain/WarehouseLayout.java`
- `master/.../layout/domain/ZoneLayout.java`
- `master/.../layout/domain/RackLayout.java`

### DTO
- `master/.../layout/dtos/WarehouseLayoutReqDto.java` / `ResDto.java`
- `master/.../layout/dtos/ZoneLayoutReqDto.java` / `SaveDto.java` / `ResDto.java`
- `master/.../layout/dtos/RackLayoutReqDto.java` / `SaveDto.java` / `ResDto.java`

### 서비스 / 컨트롤러
- `master/.../layout/service/LayoutService.java`
- `master/.../layout/controller/LayoutController.java`
- `master/.../layout/repository/*Repository.java`

### 방어 로직
- `master/.../rack/service/RackService.java` (deactivate, update)

### 메타데이터 (물리 치수)
- `master/.../rack/domain/Rack.java` (width_mm, depth_mm, height_mm)
- `master/.../product/domain/Product.java` (weight, width, depth, height)

### 연동 (재고 검증)
- `master/.../client/StockServiceClient.java` (Feign)
- stock 측: `stock/.../inventory/...` (`/inventory/has-stock` 등)

---

## 15. 디자인 결정 근거 요약

| 결정 | 대안 | 선택 이유 |
|---|---|---|
| Zone=절대, Rack=상대 좌표 | 모두 절대 | Zone 이동 시 자식 Rack 자동 이동 → 데이터·UX 일관성 |
| 일괄 저장 (upsert+delete) | 개별 PUT/DELETE | 다중 사용자 동시 편집 안전성 + 트랜잭션 단일화 |
| 좌표 px 단위 | mm/cm | 프론트 캔버스 직접 매핑, 변환 비용 0 |
| 비관적 락 미사용 | 낙관적 / 비관적 | 캔버스 편집은 last-writer-wins가 자연스러움 |
| 비활성화 시 재고 검증 | 단순 비활성화 | 데이터 정합성 (오작동 방지) |
| Shape 별도 도메인 (계획) | Zone에 type 추가 | 책임 분리, 충돌 검사 로직 격리 |

---

**문서 끝**
