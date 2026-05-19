# 📊 Kafka Streams 스펙

**버전**: Apache Kafka 3.5.x / Spring Boot 3.4.8 (Spring Kafka 3.3.x)  
**목적**: 운영 이벤트 토픽을 실시간으로 집계해 대시보드 KPI 제공  
**작성일**: 2026-05-09

---

## 1. 개요

Kafka 토픽으로 흘러가는 운영 이벤트(입고/출고/이동/기타)를 실시간으로 집계해 대시보드 KPI를 만든다. 별도 분석 클러스터 없이 stock 서비스 안의 라이브러리(`@EnableKafkaStreams`)로 동작.

| 토폴로지 | 역할 | State Store | 윈도우 |
|---------|------|-------------|--------|
| `outboundQtyByProductTable` | 상품별 누적 출고량 | KeyValueStore | 없음 |
| `hourlyThroughputTable` | 시간별 처리량 (24h) | WindowStore | 1h tumbling, 25h retention |
| `activeOrdersCountTable` | 진행 중 작업 수 (라이브 카운터) | KeyValueStore | 없음 |
| `returnRatioDailyTable` | 오늘 반품/일반 비율 | WindowStore | 1d tumbling, 2d retention |

---

## 2. 토폴로지

### 2.1 상품별 누적 출고량 (`outboundQtyByProductTable`)

| 항목 | 값 |
|------|-----|
| 입력 토픽 | `outbound.completed` |
| 처리 | flatMap items → groupByKey(productId) → aggregate(누적합) |
| State Store | `outbound-qty-by-product` (KeyValueStore) |
| 용도 | 상품별 출고량 검증 + 대시보드 |

### 2.2 시간별 처리량 (`hourlyThroughputTable`)

| 항목 | 값 |
|------|-----|
| 입력 토픽 | `inbound.placed`, `outbound.completed`, `transfer.completed`, `etcinout.completed` (4개 merge) |
| 처리 | groupByKey(`{clientId}\|{module}`) → windowedBy(1h tumbling) → count() |
| State Store | `hourly-throughput` (WindowStore, 25h retention) |
| 용도 | 24시간 처리량 라인 차트 |

### 2.3 진행 중 작업 수 (`activeOrdersCountTable`) ⭐

| 항목 | 값 |
|------|-----|
| 입력 토픽 | 12개 (`*.approved` +1 / `*.completed`(또는 `order-completed`) -1 / `*.cancelled` -1) |
| 처리 | groupByKey(`{clientId}\|{module}`) → aggregate(`Math.max(0, total+delta)`) |
| State Store | `active-orders-count` (KeyValueStore) |
| 용도 | 대시보드 "실시간 진행 상황" 4박스 (2초 폴링) |

→ **중요**: 라이프사이클 시작(`approved`)에서 +1 하고 종료(`completed`/`cancelled`)에서 -1 하므로, 그 사이의 모든 중간 상태(검수, 적치 등)는 자동으로 "진행중"에 포함됨. 중간 상태마다 별도 +/- 안 함.

### 2.4 반품/일반 비율 (`returnRatioDailyTable`)

| 항목 | 값 |
|------|-----|
| 입력 토픽 | `inbound.order-completed`, `outbound.completed` |
| 처리 | originType으로 normal/return 구분 → groupByKey(`{clientId}\|{module}\|{kind}`) → windowedBy(1d) → count() |
| State Store | `return-ratio-daily` (WindowStore, 2d retention) |
| 용도 | 오늘 반품/일반 비율 패널 |

---

## 3. State Store

| Store | 종류 | 키 | 값 |
|-------|------|-----|-----|
| `outbound-qty-by-product` | KeyValueStore | `productId` | Long (누적 qty) |
| `hourly-throughput` | WindowStore | `Windowed<{clientId}\|{module}>` | Long (count) |
| `active-orders-count` | KeyValueStore | `{clientId}\|{module}` | Long (현재 카운트) |
| `return-ratio-daily` | WindowStore | `Windowed<{clientId}\|{module}\|{kind}>` | Long (count) |

**저장 매체**: RocksDB (로컬 디스크) + changelog 토픽 자동 백업  
**복구**: Streams 인스턴스 재시작 시 changelog 토픽에서 자동 복원

---

## 4. Interactive Query (조회 API)

`StreamsQueryController.java` — state store를 RDB 안 거치고 직접 메모리/RocksDB 조회 → JSON 응답.

| 엔드포인트 | State Store |
|-----------|-------------|
| `GET /streams/active-orders?clientId=...` | `active-orders-count` |
| `GET /streams/hourly-throughput?clientId=...&module=inbound` | `hourly-throughput` |
| `GET /streams/return-ratio?clientId=...` | `return-ratio-daily` |

→ 응답에 `status` 필드 포함 (`OK` / `NOT_READY`). NOT_READY 시 빈 응답으로 화면은 "Streams 준비 중…" empty state.

---

## 5. 멀티테넌시

State store key가 모두 `{clientId}|...` 로 시작 → 회사별 격리 자동. 다른 회사 데이터 노출 사고 차단.

---

## 6. 부팅 설정

| 항목 | 값 |
|------|-----|
| Application ID | `stock-streams` |
| State Directory | `/tmp/kafka-streams/stock` |
| Key/Value Serdes | String key, JsonSerde value |
| Commit Interval | 1000ms (dev) |
| Replication Factor (changelog) | 1 (dev) / 3 (prod) |

---

## 7. 토픽 자동 생성

`StatsTopicConfig`의 21개 토픽이 부팅 시 `KafkaAdmin`에 의해 생성됨. 토폴로지 시작 시 입력 토픽이 모두 존재해야 정상 구동(없으면 `MissingSourceTopicException` → SHUTDOWN_CLIENT).

---

## 8. AB(정상/불량) 단일 토픽 처리 패턴

- **활용**: 입고 검수처럼 정상/불량 동시 발생 → 단일 토픽(`inbound.inspected`) 페이로드에 `defectItems`로 묶어 발행
- **이유**: 토픽 분리 시 토폴로지 처리 순서 꼬임 위험 차단

---

## 9. Kafka Consumer vs Kafka Streams 역할 분리

같은 토픽을 Kafka Consumer와 Kafka Streams가 모두 소비하지만, 역할이 다름. 한 이벤트가 운영 처리(Consumer) + 분석 집계(Streams) 양쪽으로 동시에 흐름. Consumer Group이 분리돼 있어 서로 영향 없음.

| 토픽 | Consumer 처리 (운영) | Streams 처리 (분석) |
|------|---------------------|---------------------|
| `outbound.approved` | 재고 예약 (`reserve`) | active-orders +1 |
| `outbound.cancelled` | 재고 복원 (`unreserve`) | active-orders -1 |
| `outbound.completed` | 재고 차감 (`releaseOnDispatch`) | 시간별 처리량 + 반품 비율 + 상품별 누적 + active-orders -1 |
| `inbound.approved` | 입고예정 +1 (`addIncoming`) | active-orders +1 |
| `inbound.inspected` | 입고예정 ↓ / 검수중 ↑ | (직접 구독 X — 검수중인 입고는 +1 상태 유지로 진행중에 자동 포함) |
| `inbound.placed` | 검수중 ↓ / 가용 ↑ (`confirmPlacement`) | 시간별 처리량 +1 (active-orders는 +1 유지로 여전히 진행중) |
| `inbound.order-completed` | (없음) | active-orders -1 + 반품 비율 분류 |
| `inbound.cancelled` | 입고예정 ↓ (`removeIncoming`) | active-orders -1 |
| `transfer.approved` | (없음, 동기 처리) | active-orders +1 |
| `transfer.completed` | (없음, 동기 처리) | 시간별 처리량 + active-orders -1 |
| `etcinout.approved` | (없음, 동기 처리) | active-orders +1 |
| `etcinout.completed` | (없음, 동기 처리) | 시간별 처리량 + active-orders -1 |

### 중요한 라이프사이클 규칙

진행 중 작업 카운터(`active-orders`)는 **라이프사이클 시작(approved)과 종료(completed/cancelled)만 보고 +/-한다.** 그 사이의 모든 중간 단계는 카운터를 건드리지 않지만, 이미 +1된 상태가 유지되므로 결과적으로 "진행중"에 포함된다.

```
입고 라이프사이클:
승인 → 검수 → 적치 → 종결
 │     │     │      │
 +1    유지   유지    -1   ← active-orders 카운터 변화

→ 검수중인 입고도, 적치 중인 입고도 모두 진행중에 카운트됨
```

---

## 10. Kafka Streams 적용 영역 분리

모든 통계를 Streams로 처리하지 않음. 정확성/실시간성 우선순위에 따라 분리.

| 영역 | 처리 방식 | 이유 |
|------|----------|------|
| 시간별 처리량 | Kafka Streams | 시간 윈도우 native, 응답 속도 우선 |
| 진행 중 작업 수 | Kafka Streams | 라이브 카운터, 응답 속도 우선 |
| 반품 비율 | Kafka Streams | 시간 윈도우 native |
| 상품별 누적 출고 | Kafka Streams | KeyValue 누적 빠름 |
| 일/월별 입출고 집계 | RDB 직접 쿼리 | 정확성 우선, 과거 데이터 백필 가능 |
| 재고 회전율 / 정산 | RDB 직접 쿼리 (스냅샷) | 절대적 정확성 필요 |
| 품번별 출고 순위 | RDB 직접 쿼리 | 정확성 + 과거 기간 조회 가능 |

→ "실시간성 vs 정확성" 트레이드오프에 따라 라우팅이 다르다.

---

## 11. 핵심 파일 위치

| 역할 | 위치 |
|------|------|
| 토폴로지 | `stock/src/main/java/com/beyond/wbs/streams/StreamsConfig.java` |
| 조회 API | `stock/src/main/java/com/beyond/wbs/streams/StreamsQueryController.java` |
| 토픽 자동 생성 | `stock/src/main/java/com/beyond/wbs/streams/StatsTopicConfig.java` |

---

## 12. 알려진 한계

### 12.1 부팅 전 데이터 미반영

Kafka Streams는 보관 기간 안의 이벤트만 처리. 처음 부팅 시 RDB의 기존 진행 중 데이터는 카운트에 포함되지 않음.

**시나리오**
```
서비스 재시작
   ↓
active-orders-count = 0
   ↓
기존 OB-001이 완료되면 -1 이벤트만 들어옴
   ↓
카운트 -1 위험 → Math.max(0) 가드로 방어
```

**현재 대응**: 시연 전 시드 이벤트 흘려서 띄움  
**운영 권장**: 부팅 시 DB → State Store 초기값 동기화 로직 추가

### 12.2 State Store 디스크 관리

RocksDB 기반이므로 디스크 사용량 모니터링 필요. WindowStore는 retention으로 자동 정리되지만 KeyValueStore(`active-orders-count`)는 영속 보관.

---

## 13. 운영 체크리스트

| 항목 | 현재 | 운영 권장 |
|------|------|-----------|
| Streams 인스턴스 수 | 1 | 2+ (HA + 부하 분산) |
| State Store 백업 | changelog 토픽 자동 | + 정기 스냅샷 |
| 모니터링 | - | Streams metrics + Grafana |
| 부팅 시 동기화 | 미구현 | DB → State Store 초기값 동기화 |
| State Store 디스크 알림 | - | 사용량 임계치 알림 |

---

## 14. Kafka 명세서와의 관계

| 구분 | Kafka | Kafka Streams |
|------|-------|---------------|
| 역할 | 이벤트 통신 수단 | 실시간 분석/집계 엔진 |
| 컴포넌트 | Producer, Consumer, 토픽 | 토폴로지, State Store, 윈도우 |
| 우리 use-case | 재고 상태 변화, 감사 로그, PDF 발행 | 시간별 처리량, 진행 카운터, 반품 비율 |
| 저장소 | 토픽 (디스크) | State Store (RocksDB + changelog 백업) |
| 인프라 | Kafka 브로커 | 추가 인프라 없음 (라이브러리) |

---

## 15. 향후 확장 후보

| 영역 | 후보 |
|------|------|
| 토폴로지 추가 | 창고별 회전율, 시간대별 피킹 효율 |
| 외부 저장 | Streams 결과를 ES/InfluxDB로 흘려서 장기 보관 |
| 시작 시 동기화 | 부팅 시 DB → State Store 초기값 동기화 자동화 |
| 인스턴스 분산 | Streams 인스턴스 다중화 (state store 자동 분산) |

문서 끝
