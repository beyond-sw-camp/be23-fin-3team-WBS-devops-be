# Elastic Search 통합 명세서

> **버전**: Elasticsearch 9.3.1 / Kibana 9.3.1
> **목적**: 3개 발행 서비스(account / master / stock)의 감사 로그와 입고 지시서를 단일 채널(search-service)로 통합 검색
> **아키텍처 패턴**: CQRS — 쓰기는 RDB, 읽기는 ES

---

## 1️⃣ 비동기 검색 아키텍처 (Kafka + Elasticsearch)

### 문제: 원본 서비스의 성능 저하 및 장애 전파

검색 기능을 구현할 때, 3개 서비스(account / master / stock)가 데이터 변경 시마다 Elasticsearch에 직접 동기식으로 색인을 요청하면 2가지 큰 문제가 발생합니다.

- **성능 저하 (응답 속도)**: Elasticsearch의 색인은 비용이 큰 작업입니다. 원본 서비스가 색인 작업이 끝날 때까지 기다려야 하므로, 사용자의 CUD(생성·수정·삭제) 요청 응답 속도가 치명적으로 느려집니다.
- **장애 전파 (결합성)**: 검색 시스템이나 Elasticsearch에 일시적 장애가 발생하면 색인 API 호출이 실패하고, 이 장애가 원본 서비스의 트랜잭션 전체를 실패시킬 수 있습니다.

### 해결: Kafka를 이용한 비동기식 색인 파이프라인

Kafka를 중간 버퍼로 두는 비동기 CQRS 아키텍처를 구축했습니다.

1. **이벤트 발행 (Produce)**: 3개 서비스(account / master / stock)의 `@AuditLog` AOP가 메서드 실행 후 `audit.created` Kafka 토픽으로 이벤트를 발행하고 즉시 응답을 반환합니다.
2. **이벤트 구독 (Consume)**: 별도의 검색 시스템(`search-service`)이 해당 토픽을 `search-group` 컨슈머 그룹으로 구독합니다.
3. **비동기 색인**: 검색 시스템이 이벤트를 받아 비동기적으로 `audit-logs-v2` 인덱스를 갱신합니다.

### 개선 효과

- **성능**: 원본 서비스는 Kafka에 메시지만 전달하면 되므로, Elasticsearch 색인 대기 없이 빠른 응답 속도를 유지합니다.
- **안정성**: 검색 시스템이나 Elasticsearch에 장애가 발생하더라도, Kafka가 이벤트를 안전하게 보관(Retention)하기 때문에 데이터 유실 없는 안정적 동기화가 가능합니다. RDB는 항상 원본(source of truth)으로 남아 재색인만으로 회복 가능합니다.

```
┌──────────────────────────┐
│ account / master / stock  │
│ @AuditLog 어노테이션 메서드 │
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│ AuditLogAspect (AOP)      │
│ ├─ HTTP 요청/응답 캡처     │
│ ├─ MySQL audit_logs 저장   │
│ └─ Kafka "audit.created" 발행 │
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│ search-service             │
│ AuditLogEventConsumer     │
│ (groupId = "search-group") │
└──────────────┬───────────┘
               │
               ▼
┌──────────────────────────┐
│ AuditLogSearchService     │
│ .index(AuditLogEvent)     │
│ → ES audit-logs-v2 색인    │
└──────────────────────────┘
```

---

## 2️⃣ 핵심 검색 기능: 자동완성 vs. 통합 검색

검색 서비스는 사용자의 의도에 따라 **'자동완성'** 과 **'통합 검색'** 두 가지 핵심 기능을 제공합니다.

| 기능 | 설명 | 검색 대상 (필드) | 주요 쿼리 (Elasticsearch) |
|---|---|---|---|
| 🚀 **자동완성** | 키워드 입력 '중간'에 실시간 검색어 제안 | `suggestText` (`search_as_you_type`) | `match_bool_prefix` |
| 🔍 **통합 검색** | 'Enter'로 실행하는 완전한 검색 | `userName`, `requestUri`, `serviceName`, `entityName`, `action`, `ipAddress` | `bool` + `multi_match` + range/term 필터 조합 |

### 상세 구현: 자동완성 (Autocomplete)

- 사용자의 키 입력마다 실시간 호출되므로 **속도가 가장 중요**합니다.
- `search_as_you_type` 타입 필드 + `match_bool_prefix` 쿼리로 매우 빠른 속도로 '입력 중'인 단어를 제안합니다.
- 빠른 조회를 위해 검색 대상을 `suggestText` 한 필드로 한정하고 최소한의 정보(`[{ type, label, value }]`)만 반환합니다.
- 응답의 `type` 으로 `user / service / action / entity / path / ip` 6종을 분류 제공 (기본 size 8, 최대 20).

### 상세 구현: 통합 검색 (Full Search)

- 6개 필드를 동시에 검색하며, **`userName` 필드에 가중치 2배(boost 2x)** 를 적용해 사람 이름 매칭을 강하게 반영합니다.
- **다중 필터 조합**을 지원합니다: keyword(전문) + userId(term) + action(`includeActions` / `excludeActions` 양방향 리스트) + httpMethod + entityName + responseStatus + `statusGroup`(`2xx` / `3xx` / `4xx` / `5xx` 범위 자동 매핑) + `minDurationMs`(느린 요청 추적) + `from`~`to` 시각 범위.
- **멀티테넌시 강제**: 모든 검색에 `X-Client-Id` 헤더 필수 → `clientId.keyword` 필터 자동 주입.

---

## 3️⃣ 기술 결정: 복잡 쿼리·멀티테넌시를 위한 Java Client (Native Query)

WBS의 검색 요구사항은 단순 CRUD가 아니라 **복잡한 Boolean 쿼리 조합과 멀티테넌시 자동 필터 강제**가 필요했기 때문에, Elasticsearch Java Client(Native Query)를 직접 사용했습니다.

### ✔️ 멀티테넌시 자동 필터 강제:

`clientId` 같은 cross-cutting 필터를 모든 쿼리에 일관되게 강제해야 했습니다. 한 쿼리라도 누락되면 즉시 데이터 유출 사고로 직결되기 때문입니다. Java Client는 서비스 레이어에서 `BoolQuery.Builder` 로 `clientId` term 필터를 항상 주입할 수 있어 누락을 원천 차단합니다.

### ✔️ 복잡한 Boolean 쿼리 조합:

다음과 같은 조합이 필요했습니다.
- `userName` 가중치 2배 `multi_match`
- `includeActions`(화이트리스트) / `excludeActions`(블랙리스트) 동시 적용
- `statusGroup`(`2xx` 등) 문자열을 동적으로 range 쿼리로 변환
- `createdAt` + `durationMs` 의 range를 별도 must / filter 절로 분리

Java Client는 이를 코드로 명확하게 구성할 수 있어 유연합니다.

### ✔️ Search-As-You-Type 고성능 쿼리 활용:

자동완성용 `match_bool_prefix` 쿼리는 ES 고유의 고성능 쿼리입니다. Java Client로 직접 호출해 사용자의 키 입력마다 ms 단위 응답을 제공합니다.

### ✔️ 부팅 시 인덱스 자동 생성:

`@PostConstruct ensureIndex()` 로 서비스 부팅 시점에 인덱스 존재 여부를 확인하고, 없으면 매핑을 적용해 자동 생성합니다. 운영자가 별도 수동 작업할 필요가 없습니다.

### ✔️ 운영 단위 재색인 트리거:

`reindexByClient(UUID clientId)` 같은 메서드로 회사 단위 일괄 재색인이 가능합니다. Java Client는 bulk 색인을 세밀하게 제어할 수 있어, 인덱스 매핑 변경 시 무중단 마이그레이션이 용이합니다.

---

## 4️⃣ 트러블슈팅: 시스템 이벤트 색인과 멀티테넌시 사고 방지

### 문제 1: 사용자가 일으키지 않은 시스템 알림(재고부족·출고불가)은 어떻게 감사로그로 색인하는가?

`@AuditLog` AOP는 HTTP 요청을 가로채는 방식이라 **사용자 컨텍스트(`userId`, `userName`)가 있는 요청만** 캡처할 수 있습니다. 그런데 재고부족·출고불가 같은 **시스템 발생 이벤트**는 사용자가 일으킨 것이 아니라 백그라운드 잡에서 발생합니다. 이런 이벤트는 AOP 경로로 잡히지 않아 통합 검색에서 누락되는 문제가 있었습니다.

### 해결 1: AlertAuditLogger 별도 경로

`stock/.../alert/service/AlertAuditLogger.java` 가 시스템 이벤트를 감사로그 형식으로 변환해 직접 ES에 색인하도록 별도 경로를 구축했습니다.

| Action | 발생 시점 |
|---|---|
| `재고부족발생` | 가용재고 < `minStockQty` 진입 |
| `재고부족해소` | 가용재고 ≥ `minStockQty` 회복 |
| `출고불가발생` | reserve 실패 (ATP 부족) |
| `출고불가부분해소` | 일부 품목만 reserve 가능 |
| `출고불가해소` | 완전 해소 |

→ `userId` / `userName` 없이 `requestBody` 필드에 JSON payload를 담아 운반. 검색 시 시스템 이벤트와 사용자 이벤트를 **한 인덱스에서 통합 조회** 가능합니다.

### 문제 2: 멀티테넌시 격리가 한 곳이라도 누락되면 다른 회사 데이터가 노출됨

SaaS 시스템에서 가장 치명적인 사고는 **회사 A 관리자가 회사 B 데이터를 보는 것**입니다. Elasticsearch는 기본적으로 모든 문서를 한 인덱스에 저장하므로, 검색 쿼리에 `clientId` 필터가 한 곳이라도 누락되면 즉시 데이터 유출이 발생합니다.

### 해결 2: 3중 방어선

| 계층 | 격리 방식 |
|---|---|
| **모든 ES 문서** | `clientId.keyword` 필드를 **필수**로 저장 |
| **모든 검색 쿼리** | 컨트롤러에서 `X-Client-Id` 헤더 누락 시 400 차단, 서비스 레이어에서 `clientId` term 필터를 자동 주입 |
| **재색인 작업** | 운영 API는 `reindexByClient(UUID clientId)` 만 노출 — 전체 재색인 API 자체를 제거 |

→ 인덱스 분리(`audit-logs-{clientId}`) 방식은 데이터 양이 커질 때 검토 가능. 현재는 단일 인덱스 + 필터 방식으로 운영합니다.

---

## 5️⃣ 인덱스 매핑 요약

### audit-logs-v2 (감사 로그)

| 필드 | 타입 | 용도 |
|---|---|---|
| `clientId` | keyword | **멀티테넌시 필터 (필수)** |
| `userName` | text (boost 2x) | 전문 검색 |
| `suggestText` | search_as_you_type | 자동완성 |
| `action` / `serviceName` / `entityName` / `httpMethod` / `ipAddress` | keyword | 필터 |
| `responseStatus` | integer | 상태 코드 필터 |
| `durationMs` | long | 처리 시간 범위 |
| `createdAt` | date | 시각 범위 |
| `requestBody` | keyword (`index=false`) | 알림 payload 보관용 (검색 불가) |

### inbound-orders-v1 (입고 지시서)

| 필드 분류 | 필드 |
|---|---|
| **검색 대상** | `orderNo`, `title`, `summary`, `note`, `itemNames` |
| **필터** | `clientId`, `status`, `warehouseId`, `supplierId` |
| **집계** | `totalItems`, `totalOrderedQty`, `totalReceivedQty`, `totalDefectQty` |

---

## 6️⃣ 핵심 파일 위치

| 영역 | 파일 |
|---|---|
| 공통 설정 | `common/.../search/ElasticsearchConfig.java` |
| 감사 로그 도큐먼트 | `search/.../audit/AuditLogSearchDocument.java` |
| 감사 로그 검색 | `search/.../audit/AuditLogSearchService.java`, `AuditLogSearchController.java` |
| 감사 로그 컨슈머 | `search/.../audit/AuditLogEventConsumer.java` |
| 입고 지시서 도큐먼트 | `stock/.../search/inbound/InboundOrderSearchDocument.java` |
| 입고 지시서 색인 | `stock/.../search/inbound/InboundOrderSearchService.java` |
| 알림 → 감사 로그 | `stock/.../alert/service/AlertAuditLogger.java` |

---

**문서 끝**
