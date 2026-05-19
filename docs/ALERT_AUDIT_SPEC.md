# 🔔 알림·감사 로그 스펙

**버전**: 1.0  
**목적**: 비슷해 보이는 두 시스템(알림 / 감사 로그)의 책임 분리, 흐름, 기술 차이 명확화  
**작성일**: 2026-05-09

---

## 1. 개요 — 왜 따로 다뤄야 하나

겉보기엔 둘 다 `audit_logs` 테이블을 공유하지만, **목적과 수명이 다른 두 시스템**이다.

| 항목 | 알림 시스템 (재고부족 / 출고불가) | 감사 로그 시스템 |
|------|--------------------------------|----------------|
| 목적 | 운영자에게 "지금 대응해야 한다" 알림 | "누가 언제 무엇을 했는지" 영구 기록 |
| 트리거 | 재고 변동 / SO 평가 | API 호출 (`@AuditLog`) |
| 수명 | 부족 → 해소까지의 짧은 라이프사이클 | 영구 보관 |
| 현재 상태 추적 | 필요 (active 알림이 무엇인지) | 불필요 |
| 실시간성 | 즉시 (WebSocket 토스트) | 비실시간 (이력 조회용) |
| 스팸 차단 | 필수 (같은 부족 반복 push 방지) | 불필요 |
| 저장/전달 | Redis + RDB + Kafka + ES (4단) | RDB + Kafka + ES (3단) |

→ **핵심 차이**: 알림은 "지금 진행 중인 상태"를 추적하고 즉시 푸시해야 하므로 Redis가 추가로 필요하다. 다만 알림 이력의 검색 색인은 Kafka를 통해 ES로 전달된다. 감사 로그는 실시간 상태 추적이 필요 없고, RDB 저장 후 Kafka로 ES 색인만 수행한다.

---

## 2. 알림 시스템

알림 시스템은 **Redis와 Kafka를 함께 사용**한다.

- **Redis + WebSocket**: 운영자 브라우저로 실시간 알림 전파
- **Redis Hash**: 현재 active 알림 상태 저장 및 diff 비교(스팸 차단)
- **Kafka**: 알림 발생/해소 이력을 search-service로 전달하여 Elasticsearch 색인

### 2.1 흐름

```
[재고 변동 또는 SO 평가 트리거]
   ↓
AlertService.notifyLowStockIfNeeded()
SalesOrderShortageService.refresh()
   ↓
① Redis Hash 조회 — 직전 상태 확인
   key: low_stock:{clientId} / so_shortage:{clientId}
   ↓
② Diff 판정
   ├─ 정상 → 부족      = added
   ├─ 부족 → 해소      = resolved
   └─ 변화 없음        = (스킵, push 안 함)
   ↓
③ WebSocketPublisher.send(destination, alert)
   └─ Redis Pub/Sub "ws-broadcast" 발행
   └─ 모든 stock 인스턴스 구독 → 자기 STOMP broker 로 forward
   └─ 클라이언트(브라우저)에 실시간 토스트
   ↓
④ AlertAuditLogger.log(action, clientId, alert)
   └─ audit_logs RDB 저장 + Kafka audit.created 발행
   └─ search-service 가 ES 색인 → 알림 페이지 조회 가능
   ↓
⑤ Redis Hash 업데이트
   ├─ added   → 현재 알림 페이로드 저장
   └─ resolved → 키 삭제
```

### 2.2 기능

| 기능 | 구현 |
|------|------|
| 실시간 푸시 | WebSocket (STOMP) over Redis Pub/Sub broadcast |
| 스팸 차단 | Redis Hash에 직전 상태 저장 후 diff 판정 |
| 이력 보관 | AuditLogger → audit_logs RDB + Kafka → ES |
| 멀티 인스턴스 지원 | Redis Pub/Sub 채널 `ws-broadcast`로 전 인스턴스 broadcast |
| 재시작 복원 | Redis 데이터 유실 시에도 다음 트리거에서 RDB를 기준으로 active 상태 재평가 |

### 2.3 기술

| 계층 | 기술 | 역할 |
|------|------|------|
| 트리거 | Spring `@Transactional` 메서드 내부 호출 | 재고 변경 후 즉시 평가 |
| 상태 저장 | Redis Hash | 현재 active 알림 스냅샷 (스팸 차단용) |
| 실시간 전파 | Redis Pub/Sub + WebSocket(STOMP) | 멀티 인스턴스 broadcast → 브라우저 토스트 |
| 이력 저장 | MySQL `audit_logs` | 영구 보관 source-of-truth |
| 이벤트 발행 | Kafka `audit.created` | search-service ES 색인 트리거 |
| 검색 인덱스 | Elasticsearch | 알림 페이지 빠른 조회/필터 |

### 2.4 알림 종류

| 알림 | action 코드 | Redis Hash key | WS destination |
|------|-------------|----------------|----------------|
| 안전재고 부족 발생 | `재고부족발생` | `low_stock:{clientId}` (field: `productId\|warehouseId`) | `/topic/admin/alerts/{clientId}` |
| 안전재고 부족 해소 | `재고부족해소` | (해소 시 삭제) | `/topic/admin/alerts/{clientId}` |
| 출고불가 발생 | `출고불가발생` | `so_shortage:{clientId}` (field: `soId`) | `/topic/admin/alerts/{clientId}` |
| 출고불가 부분해소 | `출고불가부분해소` | (값 갱신) | `/topic/admin/alerts/{clientId}` |
| 출고불가 해소 | `출고불가해소` | (해소 시 삭제) | `/topic/admin/alerts/{clientId}` |

---

## 3. 감사 로그 시스템

### 3.1 흐름

```
[모든 서비스의 @AuditLog 어노테이션 메서드 호출]
   ↓
AuditLogAspect (AOP, Spring @Around)
   ├─ HTTP 요청/응답 캡처
   ├─ 사용자/IP/duration 메타 추출
   └─ saveLog()
   ↓
① auditLogRepository.save(AuditLogEntity)
   → MySQL audit_logs 저장
   ↓
② auditLogEventPublisher.publish(savedLog)
   → Kafka audit.created 발행
   ↓
search-service의 AuditLogEventConsumer
(groupId="search-group")
   ↓
③ AuditLogSearchService.index(event)
   → Elasticsearch audit-logs-v2 색인
```

### 3.2 기능

| 기능 | 구현 |
|------|------|
| 자동 캡처 | AOP로 메서드 호출 시 자동 (코드 침투 X) |
| 영구 보관 | MySQL `audit_logs` (truncate 안 함) |
| 빠른 검색 | ES 색인 (감사 로그 페이지 필터/검색) |
| 장애 복원력 | ES 장애 시에도 RDB는 살아있음 → Kafka offset 되감아서 재색인 가능 |
| 필터링 | 알림 페이지는 5종 action 화이트리스트로 필터해서 별도 화면 구성 |

### 3.3 기술

| 계층 | 기술 | 역할 |
|------|------|------|
| 가로채기 | Spring AOP `@Around` | API 호출 자동 캡처 |
| 영구 저장 | MySQL `audit_logs` | source-of-truth |
| 이벤트 발행 | Kafka `audit.created` | search-service에 인덱싱 의뢰 |
| 검색 인덱스 | Elasticsearch `audit-logs-v2` | 시간/사용자/액션 빠른 조회 |
| 비동기 분리 | Kafka 통한 CQRS 패턴 | RDB write ↔ ES read 분리 |

---

## 4. 같은 audit_logs 테이블, 두 가지 화면

| 화면 | URL | 사용 action | 의미 |
|------|-----|-------------|------|
| 감사 로그 | `/common/audit-logs` (예시) | 전체 (생성/승인/취소/조회 등) | "누가 언제 뭘 했는지" 운영 이력 |
| 알림 이력 | `/common/notifications` | 5종 화이트리스트만 (출고불가발생/부분해소/해소, 재고부족발생/해소) | "어떤 알림이 떴고 해소됐는지" |

→ **저장은 같은 테이블, 조회는 화이트리스트로 분리.** 같은 데이터를 두 가지 관점으로 쓴다.

---

## 5. 두 시스템 비교

| 구분 | 알림 시스템 | 감사 로그 시스템 |
|------|------------|----------------|
| 목적 | 즉각 대응이 필요한 이벤트 통지 | 영구 운영 이력 기록 |
| 주요 컴포넌트 | `AlertService`, `SalesOrderShortageService`, `WebSocketPublisher`, Redis, Kafka | `AuditLogAspect`, `audit_logs`, Kafka, ES |
| 트리거 | 재고 변동 / SO 평가 (코드 직접 호출) | API 호출 (AOP 자동) |
| 저장/전달 | Redis Hash + RDB + Kafka + ES (4중) | RDB + Kafka + ES (3중) |
| 실시간 채널 | Redis Pub/Sub + WebSocket | 없음 |
| 스팸 차단 | Redis Hash diff 판정 | 불필요 |
| 수명 | 부족 → 해소 (라이프사이클 있음) | 영구 |
| 장애 복원력 | Redis 유실 시 재평가, RDB 유실 시 ES 재구성 | RDB가 source-of-truth, ES만 재색인 |
| 운영 의미 | "지금 무엇을 해야 하나" | "지금까지 무엇이 있었나" |

### 핵심 한 줄

> 알림은 **"현재형 + 즉시 푸시"**, 감사 로그는 **"과거형 + 비실시간 보관"**.

두 시스템이 `audit_logs` 테이블을 공유하는 건 이력 보관 측면에서만이고, 알림 시스템은 그 위에 Redis(상태/실시간 전파) + Kafka(검색 인덱싱 전달) 레이어를 추가로 얹은 구조다.

---

## 6. 통합 흐름도 (한 알림이 발생하면)

```
재고 변동 트리거
   │
   ├──→ ① AlertService → Redis Hash 직전 상태 조회
   │       ↓
   │       ② Diff 판정 (added/resolved/none)
   │       ↓
   │       ┌─────────────────┬─────────────────┐
   │       ↓                 ↓                 ↓
   │   ③ WS 푸시        ④ 이력 기록      ⑤ Redis Hash 갱신
   │   (Redis Pub/Sub)   (AuditLogger)     (다음 비교용)
   │       ↓                 ↓
   │   브라우저 토스트   audit_logs RDB
   │                         ↓
   │                    Kafka audit.created
   │                         ↓
   │                    ES 색인
   │                         ↓
   │                    알림 페이지 조회
```

**한 알림에 4가지 인프라가 협력**

- **Redis**: 현재 상태 + 실시간 전파
- **RDB**: 영구 이력
- **Kafka**: search-service 로 이력 전달
- **ES**: 검색 가능한 인덱스

---

## 7. 전체 명세서 간 관계

```
┌──────────────────────────────────────────────────┐
│ Kafka 스펙 (KAFKA_SPEC.md)                        │
│   토픽 27개, Producer/Consumer, AFTER_COMMIT, DLQ │
└──────────────────────────────────────────────────┘
            │                          │
            │ 같은 토픽을 같이 소비      │ audit.created 토픽
            ↓                          ↓
┌────────────────────────────┐  ┌────────────────────────────┐
│ Kafka Streams 스펙           │  │ 알림·감사 로그 스펙           │
│ (KAFKA_STREAMS_SPEC.md)     │  │ (ALERT_AUDIT_SPEC.md)       │
│ 토폴로지 4개, State Store    │  │ Redis Hash + WebSocket      │
│ 대시보드 KPI 실시간 집계      │  │ audit_logs (RDB+Kafka+ES)   │
└────────────────────────────┘  └────────────────────────────┘
```

세 명세서는 같은 Kafka 인프라 위에서 각각 다른 책임을 가짐.

| 명세서 | 역할 |
|--------|------|
| Kafka 스펙 | 인프라 토대 |
| Kafka Streams 스펙 | 분석 레이어 |
| 알림·감사 로그 스펙 | 운영자 가시성 레이어 |

문서 끝
