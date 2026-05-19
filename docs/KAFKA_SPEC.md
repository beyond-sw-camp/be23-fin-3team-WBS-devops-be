# ⚡ Kafka 스펙

**버전**: Apache Kafka 3.5.x (Confluent Platform 7.5.0) / Spring Boot 3.4.8 (Spring Kafka 3.3.x)  
**목적**: 입고·출고·이동·재고 도메인 간 비동기 이벤트 처리, 멀티테넌시 격리, 실시간 통계/검색 파이프라인  
**작성일**: 2026-05-09

---

## 1. 개요

본 시스템은 Kafka를 도메인 간 이벤트 통신 수단으로 사용한다. 재고가 움직이는 주요 구간(입고·출고·이동·기타입출고)의 상태 변화는 이벤트로 발행되며, 운영 처리와 통계 집계가 이를 각각 소비한다.

다만 **실시간 알림 브라우저 푸시 자체는 Kafka가 아니라 Redis Pub/Sub + WebSocket으로 처리**한다. Kafka는 알림 이력을 search-service로 전달해 Elasticsearch에 색인하는 용도로 함께 사용된다.

| 영역 | 처리 방식 | 비고 |
|------|-----------|------|
| 라이프사이클 + 통계 | Kafka Producer + Consumer + Streams | 운영 이벤트/대시보드 실시간 집계 |
| 감사 로그 / 알림 이력 색인 | Kafka 발행 → search-service 소비 | `audit.created` 기반 ES 색인 |
| 지시서 PDF | Kafka 자동 재시도 + 실패 큐(DLQ) | 비동기 렌더링 |
| 실시간 알림 전파 | Redis Pub/Sub + WebSocket | 운영자 브라우저 즉시 반영 |
| 알림 상태 스냅샷 | Redis Hash | active 알림 diff 비교, 스팸 차단 |

→ **핵심 정리**: Kafka는 이벤트 통신과 검색/통계 파이프라인의 중심이고, Redis는 실시간 알림 브로드캐스트와 현재 알림 상태 추적에 사용된다.

---

## 2. Kafka 도입 전 / 후 비교

### 2.1 도입 전 — 직접 호출 방식

```text
[출고 지시서 승인 API]
   ↓
재고 서비스 직접 호출 → 재고 차감
   ↓
SO 부족 알림 갱신
   ↓
감사 로그 저장
   ↓
응답 반환
```

단점:

- 응답 시간이 후속 처리에 묶임
- 재고 처리 실패가 승인 API까지 전파됨
- 부가 로직(알림/로그)이 핵심 트랜잭션을 무겁게 만듦
- 재처리/복구가 어려움

### 2.2 도입 후 — 이벤트 기반 방식

```text
[출고 지시서 승인 API]
   ↓
이벤트 발행
   ↓
[Kafka 토픽]
   ↓
stock 서비스
  ├─ InventoryEventConsumer (재고 반영)
  └─ Kafka Streams (실시간 통계)
search 서비스
  └─ AuditLogEventConsumer (ES 색인)
```

장점:

- API는 이벤트 발행 후 빠르게 응답
- 장애가 도메인 전체로 전파되지 않음
- 같은 이벤트를 운영 처리와 분석 집계가 동시에 소비 가능
- Kafka offset 기반 재처리 가능

---

## 3. 의존성 / 버전

### 3.1 Docker 인프라

| 서비스 | 이미지 | 포트 |
|--------|--------|------|
| kafka | confluentinc/cp-kafka:7.5.0 | 9092 |
| zookeeper | confluentinc/cp-zookeeper:7.5.0 | 2181 |
| redis | 알림 브로드캐스트/상태 추적 용도 | 6379 |

### 3.2 모듈별 의존성

| 모듈 | 라이브러리 |
|------|-----------|
| stock | spring-kafka, kafka-streams, spring-data-redis, spring-websocket |
| search | spring-kafka, Elasticsearch Java Client |
| common | spring-kafka, spring-data-redis |

---

## 4. 토픽 구성

### 4.1 라이프사이클 + 통계 토픽

운영 이벤트 토픽은 입고/출고/이동/기타입출고 흐름에서 발행되며, 일부는 재고 반영 Consumer가, 일부는 Kafka Streams가 소비한다.

대표 예시:

| 도메인 | 토픽 | 발행 시점 | 주요 소비 |
|--------|------|-----------|-----------|
| 입고 | `inbound.approved` | 지시서 승인 | 재고 반영 + Streams |
| 입고 | `inbound.inspected` | 검수 완료 | 재고 반영 |
| 입고 | `inbound.placed` | 적치 완료 | 재고 반영 + Streams |
| 출고 | `outbound.approved` | 지시서 승인 | 재고 예약 + Streams |
| 출고 | `outbound.cancelled` | 지시서 취소 | 예약 복원 + Streams |
| 출고 | `outbound.completed` | 출고 확정 | 최종 차감 + Streams |
| 이동 | `transfer.approved` | 지시서 승인 | Streams |
| 이동 | `transfer.completed` | 이동 완료 | Streams |
| 기타 | `etcinout.approved` | 지시서 승인 | Streams |
| 기타 | `etcinout.completed` | 처리 완료 | Streams |

### 4.2 감사 로그 / 알림 이력 토픽

| 토픽 | Producer | Consumer | 용도 |
|------|----------|----------|------|
| `audit.created` | `@AuditLog` AOP, `AlertAuditLogger` | search-service | 감사 로그 및 알림 이력 ES 색인 |

즉 알림은 **실시간 푸시 경로는 Redis**, **이력 검색 경로는 Kafka**를 사용한다.

### 4.3 지시서 PDF 토픽

| 토픽 | 용도 |
|------|------|
| `instruction.issued` | 메인 발행 |
| `instruction.issued-retry-0` | 1차 재시도 |
| `instruction.issued-retry-1` | 2차 재시도 |
| `instruction.issued.dlq` | 최종 실패 큐 |

---

## 5. 파티션 키 설계

같은 지시서에 대한 이벤트 순서를 보장하기 위해 메시지 키를 `refId`(지시서 ID)로 사용한다.

```text
파티션 0: OB-001.approved → OB-001.cancelled
파티션 1: OB-002.approved → OB-002.completed
파티션 2: OB-003.approved → OB-003.completed
```

또한 Streams state store key는 `{clientId}|{module}` 형식을 사용해 회사별 멀티테넌시를 자동으로 분리한다.

---

## 6. Producer 구성

| 클래스 | 역할 |
|--------|------|
| `InboundEventPublisher` | 입고 이벤트 발행 |
| `OutboundEventPublisher` | 출고 이벤트 발행 |
| `TransferEventPublisher` | 이동 이벤트 발행 |
| `EtcInoutEventPublisher` | 기타입출고 이벤트 발행 |
| `AuditLogEventPublisher` | `audit.created` 발행 |
| `InstructionIssueEventBridge` | PDF 발행 이벤트 브리지 |

### 6.1 트랜잭션 commit 후 발행

일부 이벤트는 `@TransactionalEventListener(phase = AFTER_COMMIT)`을 사용해 DB commit 성공 후 발행한다.  
DB 롤백 시 잘못된 후속 작업(PDF 생성, 검색 색인 등)이 발생하지 않도록 한다.

---

## 7. Consumer 구성

### 7.1 Consumer Group

| 그룹 | 소속 모듈 | 처리 |
|------|-----------|------|
| `stock-group` | stock | 운영 이벤트 재고 처리 |
| `search-group` | search | `audit.created` ES 색인 |
| `instruction-doc-group` | stock | PDF 렌더링 / 재시도 / DLQ |

### 7.2 InventoryEventConsumer 대표 매핑

| 토픽 | 처리 메서드 | 효과 |
|------|------------|------|
| `outbound.approved` | `reserve()` | 가용재고 감소 / 예약재고 증가 |
| `outbound.cancelled` | `unreserve()` | 예약재고 복원 |
| `outbound.completed` | `releaseOnDispatch()` | 예약재고 최종 차감 |
| `inbound.approved` | `addIncoming()` | 입고예정재고 증가 |
| `inbound.cancelled` | `removeIncoming()` | 입고예정재고 복원 |
| `inbound.inspected` | `addPending()` + `markDefect()` | 검수중/불량 반영 |
| `inbound.placed` | `confirmPlacement()` | 가용재고 반영 |

---

## 8. Kafka Streams와의 역할 분리

Kafka Consumer와 Kafka Streams는 같은 토픽을 읽을 수 있지만 역할이 다르다.

| 토픽 | Consumer 처리 (운영) | Streams 처리 (분석) |
|------|---------------------|---------------------|
| `outbound.approved` | 재고 예약 | active-orders +1 |
| `outbound.cancelled` | 재고 복원 | active-orders -1 |
| `outbound.completed` | 재고 차감 | 시간별 처리량 / 반품 비율 / 누적 출고량 / active-orders -1 |
| `inbound.approved` | 입고예정 증가 | active-orders +1 |
| `inbound.placed` | 가용재고 반영 | 시간별 처리량 +1 |
| `inbound.order-completed` | 없음 | active-orders -1 + 반품 비율 분류 |
| `transfer.completed` | 없음 | 시간별 처리량 + active-orders -1 |
| `etcinout.completed` | 없음 | 시간별 처리량 + active-orders -1 |

즉 Kafka는 단순 메시지 브로커가 아니라,

- 운영 재고 상태 반영
- 실시간 통계 집계
- 검색 색인 파이프라인

을 동시에 받쳐주는 핵심 인프라다.

---

## 9. 실시간 알림 처리 (Redis + WebSocket + Kafka)

알림은 역할에 따라 **Redis와 Kafka를 함께 사용**한다.

| 용도 | 자료구조/채널 | 키/토픽 | 책임 클래스 |
|------|---------------|---------|-------------|
| WebSocket 멀티 인스턴스 broadcast | Redis Pub/Sub | `ws-broadcast` | `WebSocketPublisher`, `WebSocketRedisSubscriber` |
| 알림 상태 스냅샷 (diff) | Redis Hash | `low_stock:{clientId}`, `so_shortage:{clientId}` | `AlertService`, `SalesOrderShortageService` |
| 알림 이력 검색 파이프라인 | Kafka | `audit.created` | `AlertAuditLogger`, search-service consumer |

### 9.1 Redis Pub/Sub — 멀티 인스턴스 broadcast

여러 stock 인스턴스가 떠 있는 환경에서는 각 인스턴스가 자기 STOMP 세션만 알고 있다.  
이때 인스턴스 A에서 발생한 알림을 인스턴스 B에 연결된 사용자도 받아야 하므로 `ws-broadcast` 채널을 모든 인스턴스가 함께 구독한다.

```text
[Service code]
   ↓ webSocketPublisher.send("/topic/...", payload)
[Redis Pub/Sub 채널 "ws-broadcast"]
   ↓
인스턴스 1 → 자기 SimpleBroker → 연결된 클라이언트
인스턴스 2 → 자기 SimpleBroker → 연결된 클라이언트
인스턴스 N → 자기 SimpleBroker → 연결된 클라이언트
```

장애 처리: WebSocket 발행 실패는 비즈니스 트랜잭션을 롤백시키지 않도록 예외를 내부에서 흡수한다.

### 9.2 Redis Hash — 상태 스냅샷 / 스팸 차단

재고 변동이 여러 번 일어나도 같은 부족 알림이 반복 push 되지 않도록 Redis Hash에 현재 active 알림 상태를 저장하고 diff만 계산한다.

| 직전 상태 | 현재 상태 | 동작 |
|-----------|-----------|------|
| 정상 | 부족 | `added` push + Hash 저장 |
| 부족 | 해소 | `resolved` push + Hash 삭제 |
| 변화 없음 | 동일 | push 안 함 |

### 9.3 Kafka — 알림 이력 색인

실시간 푸시는 Redis 경로를 사용하지만, 알림 발생/해소 이력은 `AlertAuditLogger`가 `audit_logs`에 기록한 뒤 `audit.created`를 Kafka로 발행한다. search-service는 이를 받아 Elasticsearch에 색인하고, 알림 페이지 조회에 사용한다.

즉 알림 시스템은:

- **Redis**: 지금 브라우저에 바로 보여줄 것
- **Kafka**: 나중에 검색/조회할 이력 파이프라인

으로 역할이 분리되어 있다.

---

## 10. 운영 체크포인트

| 항목 | 현재 | 운영 권장 |
|------|------|-----------|
| Kafka 토픽 파티션 | 개발 편의 기준 | 운영 환경에서 토픽별 재검토 |
| `audit.created` 토픽 생성 | broker 기본값 의존 가능 | NewTopic 명시 선언 권장 |
| Streams 인스턴스 수 | 1 | 2+ (HA) |
| Redis Pub/Sub 모니터링 | 별도 없음 | 채널 소비/지연 관찰 |
| DLQ 운영 | 존재 | 운영자 수동 재처리 절차 문서화 |

---

## 11. 관련 문서

- [Kafka Streams 스펙](./KAFKA_STREAMS_SPEC.md)
- [알림·감사 로그 스펙](./ALERT_AUDIT_SPEC.md)

문서 끝
