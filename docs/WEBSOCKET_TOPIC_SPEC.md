# 📡 WebSocket·Redis Pub/Sub 스펙

**버전**: Spring Boot 3.4.8 (spring-boot-starter-websocket / spring-data-redis) / STOMP over SockJS-호환 ws  
**목적**: 운영자 실시간 업데이트와 모바일 작업자 액션 알림을 위한 WebSocket(STOMP) 채널 + Redis Pub/Sub 멀티 인스턴스 broadcast 인프라 명세  
**작성일**: 2026-05-10

---

## 1. 개요

운영자 화면(지시서 목록·상세, 알림 토스트)과 모바일 작업자 액션은 **요청 없이도 서버가 즉시 화면을 갱신**해야 한다. 폴링으로는 즉시성과 부하 모두 만족시키기 어렵기 때문에 WebSocket(STOMP) 채널을 사용한다.

다만 stock 서비스는 **여러 인스턴스로 수평 확장**될 수 있고, 각 인스턴스는 자기 STOMP 세션만 알고 있다. 그래서 한 인스턴스에서 발행한 메시지가 다른 인스턴스의 클라이언트에게 도달하려면 **Redis Pub/Sub 채널 `ws-broadcast`** 를 모든 인스턴스가 함께 구독하는 broadcast 패턴이 필요하다.

| 영역 | 처리 방식 | 비고 |
|------|-----------|------|
| 운영자 실시간 업데이트 | WebSocket(STOMP) `/topic/admin/{module}/{clientId}` | 지시서 목록/상세 자동 갱신 |
| 작업자 알림 | WebSocket(STOMP) `/topic/worker/{userId}/{module}` | 모바일 앱에 배정/지시 푸시 |
| 알림 토스트 | WebSocket(STOMP) `/topic/admin/alerts/{clientId}` | 재고부족·출고불가 |
| 멀티 인스턴스 broadcast | Redis Pub/Sub 채널 `ws-broadcast` | 발행 인스턴스와 무관하게 전 클라이언트에 도달 |
| 핸드셰이크 인증 | JWT 5분 단기 ticket (query string `?ticket=`) | 브라우저 WebSocket API 가 Authorization 헤더 못 박는 문제 우회 |

→ **핵심 정리**: 발행 측은 `WebSocketPublisher.send()` 한 줄만 호출하고, Redis Pub/Sub 와 SimpleBroker 가 멀티 인스턴스 broadcast 와 클라이언트 분배를 자동 처리한다.

---

## 2. 도입 전 / 후 비교

### 2.1 도입 전 — 단일 인스턴스 SimpleBroker 직접 발행

```text
[Service code]
   ↓ messagingTemplate.convertAndSend("/topic/...", payload)
[자기 인스턴스의 SimpleBroker]
   ↓
자기 인스턴스에 연결된 클라이언트만 받음
```

단점:

- 다른 인스턴스에 붙은 사용자에게는 메시지가 안 도달
- 인스턴스 수평 확장 시 알림 누락 발생
- 발행 측이 어느 인스턴스에서 도는지 알 수 없으므로 보장 불가

### 2.2 도입 후 — Redis Pub/Sub 거쳐 broadcast

```text
[Service code]
   ↓ webSocketPublisher.send("/topic/...", payload)
[Redis Pub/Sub 채널 "ws-broadcast"]
   ↓
인스턴스 1 → WebSocketRedisSubscriber → 자기 SimpleBroker → 연결된 클라이언트
인스턴스 2 → WebSocketRedisSubscriber → 자기 SimpleBroker → 연결된 클라이언트
인스턴스 N → WebSocketRedisSubscriber → 자기 SimpleBroker → 연결된 클라이언트
```

장점:

- 어느 인스턴스에서 발행해도 모든 클라이언트가 받음
- 발행 측은 인스턴스 토폴로지를 알 필요 없음
- 알림 발행 실패가 비즈니스 트랜잭션을 롤백시키지 않게 흡수 처리

---

## 3. 의존성 / 버전

| 모듈 | 라이브러리 | 역할 |
|------|-----------|------|
| stock | spring-boot-starter-websocket | STOMP 브로커, `/ws` 엔드포인트 |
| stock | spring-data-redis | Redis Pub/Sub publisher / subscriber |
| stock | jackson-datatype-jsr310 | `LocalDateTime` 직렬화 |
| account | jjwt | 5분 ws ticket 발급 |
| apigateway | jjwt | ws 핸드셰이크 단계 ticket 검증 |

---

## 4. 컴포넌트 구성

### 4.1 발행 측 — `WebSocketPublisher`

| 항목 | 내용 |
|------|------|
| 위치 | `stock/src/main/java/com/beyond/wbs/websocket/WebSocketPublisher.java` |
| 진입점 | `send(String destination, Object payload)` |
| 내부 | `WebSocketBroadcastEnvelope(destination, payload)` 로 감싸 `webSocketRedisTemplate.convertAndSend("ws-broadcast", envelope)` |
| 실패 처리 | try-catch 로 예외 흡수 — 비즈니스 트랜잭션 롤백되지 않게 |
| 호출 시점 | `@Transactional` 트랜잭션 내부에서 직접 호출 (commit 후 보장은 별도) |

### 4.2 수신 측 — `WebSocketRedisSubscriber`

| 항목 | 내용 |
|------|------|
| 위치 | `stock/src/main/java/com/beyond/wbs/websocket/WebSocketRedisSubscriber.java` |
| 인터페이스 | `MessageListener.onMessage(Message, byte[] pattern)` |
| 역할 | Redis 채널에서 envelope 수신 → 역직렬화 → `messagingTemplate.convertAndSend(destination, payload)` 로 자기 SimpleBroker 에 forward |
| 등록 | `RedisWebSocketConfig` 에서 `RedisMessageListenerContainer` 에 `ChannelTopic("ws-broadcast")` 으로 추가 |

### 4.3 STOMP 브로커 — `WebSocketConfig`

| 설정 | 값 |
|------|-----|
| `@EnableWebSocketMessageBroker` | STOMP 활성화 |
| SimpleBroker prefix | `/topic` |
| Application destination prefix | `/app` (현재 client → server 메시지 미사용) |
| Endpoint | `/ws` (`setAllowedOriginPatterns("*")`) |
| 게이트웨이 매핑 | `/stock-service/ws` → stock 의 `/ws` |

### 4.4 봉투 — `WebSocketBroadcastEnvelope`

| 필드 | 타입 | 의미 |
|------|------|------|
| `destination` | `String` | 받는 SimpleBroker 가 라우팅할 STOMP 경로 (예: `/topic/admin/inbound/...`) |
| `payload` | `Object` | 실제 클라이언트에게 전달될 메시지 (예: `WorkEventMessage`) |

→ Redis 채널은 destination 정보를 모르므로 봉투에 함께 실어 보낸다.

### 4.5 직렬화 — `RedisWebSocketConfig`

| 항목 | 설정 |
|------|------|
| Serializer | `GenericJackson2JsonRedisSerializer` (전용 빈) |
| ObjectMapper | `JavaTimeModule` 등록 + `activateDefaultTyping(NON_FINAL)` 으로 다형성 허용 |
| 빈 이름 | `webSocketRedisTemplate`, `webSocketRedisMessageListenerContainer`, `webSocketRedisSerializer` |
| 충돌 회피 | common 모듈의 기본 `redisTemplate` 빈과 이름 분리 |

→ 발행 측 `RedisTemplate` 과 수신 측 `WebSocketRedisSubscriber` 가 **같은 직렬화기 빈**을 주입받아 직렬화/역직렬화 설정을 공유한다.

---

## 5. 채널 계층

운영자 채널과 작업자 채널, 그리고 알림 채널의 3계층으로 분리된다.

### 5.1 운영자 채널 — `/topic/admin/{module}/{clientId}[/{orderId}]`

| 모듈 | 목록 채널 | 상세 채널 |
|------|-----------|-----------|
| 입고 | `/topic/admin/inbound/{clientId}` | `/topic/admin/inbound/{clientId}/{orderId}` |
| 출고 | `/topic/admin/outbound/{clientId}` | `/topic/admin/outbound/{clientId}/{orderId}` |
| 피킹 | `/topic/admin/picking/{clientId}` | `/topic/admin/picking/{clientId}/{orderId}` |
| 이동 | `/topic/admin/transfer/{clientId}` | `/topic/admin/transfer/{clientId}/{orderId}` |
| 기타입출고 | `/topic/admin/etc-inout/{clientId}` | `/topic/admin/etc-inout/{clientId}/{orderId}` |
| 재고실사 | `/topic/admin/stock-count/{clientId}` | `/topic/admin/stock-count/{clientId}/{orderId}` |

→ **발행 패턴**: 한 액션이 발생하면 `목록 채널` + `상세 채널` 두 군데에 동일 메시지를 발행한다. 목록 화면은 목록 채널만 구독하고, 상세 화면은 상세 채널을 추가로 구독해서 자기 화면에 필요한 만큼만 받는다.

### 5.2 작업자 채널 — `/topic/worker/{userId}/{module}`

| 채널 | 용도 |
|------|------|
| `/topic/worker/{userId}/etc-inout` | 작업자 본인에게 배정된 기타입출고 액션 푸시 |

→ 향후 모바일 앱에 작업 배정/회수가 더 필요하면 같은 패턴(`/topic/worker/{userId}/{module}`)으로 확장.

### 5.3 알림 채널 — `/topic/admin/alerts/{clientId}`

| 발행 시점 | 페이로드 종류 |
|-----------|--------------|
| 안전재고 부족 발생/해소 | `LowStockAlertDto` |
| 출고불가 발생/부분해소/해소 | `SalesOrderShortageAlertDto` |

→ 알림 시스템 상세는 [알림·감사 로그 스펙](ALERT_AUDIT_SPEC.md) 참고.

### 5.4 멀티테넌시 격리

채널 경로에 `{clientId}` 가 포함되므로 다른 회사 사용자는 다른 토픽을 구독한다. 한 회사의 알림이 다른 회사 사용자에게 노출될 수 없다. (구독 권한 검증은 현재 destination 자체로 자연 분리되며, 향후 `ChannelInterceptor` 로 구독 시 토큰의 `clientId` 와 destination 의 `{clientId}` 일치 여부를 강제할 여지 있음.)

---

## 6. 핸드셰이크 인증

### 6.1 왜 일반 access token 을 못 쓰는가

브라우저의 표준 `WebSocket` API 는 **연결 단계에서 HTTP `Authorization` 헤더를 임의로 설정할 수 없다.** 그래서 access token 을 헤더로 보내는 일반 HTTP 인증 방식을 그대로 쓸 수 없다.

### 6.2 5분 ticket 방식

1. 클라이언트는 일반 access token 으로 `GET /account-service/user/ws-ticket` 호출
2. account 서비스가 동일 secret 으로 서명하되 **만료 5분짜리 단기 ticket** 을 발급
3. 클라이언트는 `wss://gateway/stock-service/ws?ticket=<jwt>` 로 핸드셰이크
4. 게이트웨이의 `JwtAuthFilter` 가 query string `?ticket=` 을 꺼내 일반 토큰처럼 검증
5. 검증 통과 시 `X-User-Id` / `X-Client-Id` 등 식별 헤더를 stock 서비스로 주입

```text
[브라우저]
   │ ① GET /account-service/user/ws-ticket (Authorization: Bearer AT)
   ↓
[account 서비스] → JwtTokenProvider.createWsTicket(userId)
   │ ② { ticket: "<5분짜리 jwt>" }
   ↓
[브라우저]
   │ ③ WebSocket connect: /stock-service/ws?ticket=<jwt>
   ↓
[apigateway JwtAuthFilter]
   │ ④ urlPath.startsWith("/stock-service/ws") → query string 의 ticket 파싱·검증
   ↓
[stock 서비스 /ws 엔드포인트] → STOMP 세션 수립
```

### 6.3 ticket 페이로드

| Claim | 값 | 비고 |
|-------|-----|------|
| `sub` | userId (UUID) | 사용자 식별자 |
| `clientId` | UUID | 회사 식별자 |
| `isDeveloper` | boolean | 개발자 플래그 |
| `role` | role code | 역할 |
| 만료 | 5분 | access token (30분) 보다 짧게 |

→ 일반 access token 과 같은 secret 으로 서명되므로 게이트웨이 측에서 별도 분기 없이 동일 검증 로직을 재사용한다.

---

## 7. 페이로드 — `WorkEventMessage`

운영자/작업자 채널의 공통 페이로드. 모듈/액션 종류와 무관하게 같은 구조를 쓴다.

| 필드 | 타입 | 의미 |
|------|------|------|
| `module` | `String` | 모듈 식별자 (예: `"transfer"`, `"inbound"`, `"outbound"`, `"picking"`, `"placement"`, `"etc-inout"`, `"stock-count"`) |
| `type` | `String` | 액션 종류 (예: `"CREATED"`, `"APPROVED"`, `"CANCELLED"`, `"PICKED"`, `"PLACED"`, `"RECEIVED"`, `"DISPATCHED"`, `"COMPLETED"`) |
| `clientId` | `UUID` | 회사 식별자 — destination 이외에 페이로드에서도 검증 가능하게 포함 |
| `orderId` | `UUID` | 대상 지시서 ID |
| `orderNo` | `String` | 표시용 지시서 번호 (예: `"TR-2026-001"`) |
| `userId` | `UUID` | 액션 수행자 ID |
| `occurredAt` | `LocalDateTime` | 발생 시각 |

→ 프론트는 `module` + `type` 조합으로 분기 처리(예: 토스트 메시지, 목록 행 갱신, 상세 새로고침). 새로운 모듈이 생겨도 페이로드 형식을 그대로 재사용한다.

---

## 8. 멀티 인스턴스 broadcast 흐름

```text
[발행]
서비스 코드 — webSocketPublisher.send("/topic/admin/inbound/<clientId>", msg)
   ↓
WebSocketBroadcastEnvelope(destination, msg) 로 wrap
   ↓
webSocketRedisTemplate.convertAndSend("ws-broadcast", envelope)
   ↓
[Redis Pub/Sub 채널 "ws-broadcast"]
   ↓ (모든 stock 인스턴스가 함께 구독)
   ├─ 인스턴스 1 — WebSocketRedisSubscriber.onMessage
   │     ↓ messagingTemplate.convertAndSend(destination, payload)
   │     SimpleBroker → 자기 STOMP 세션의 클라이언트
   ├─ 인스턴스 2 — 동일
   └─ 인스턴스 N — 동일
```

요점:

- **발행은 한 곳에서, 수신은 모든 곳에서.** 발행 인스턴스는 어느 인스턴스에 누가 붙어 있는지 알 필요가 없다.
- **봉투 안에 destination 동봉.** 받는 측 SimpleBroker 가 자기 STOMP 라우팅 결정에 사용.
- **인스턴스 추가는 자동 합류.** 새 인스턴스가 떠서 `webSocketRedisMessageListenerContainer` 가 시작되면 즉시 broadcast 수신 시작.

---

## 9. 장애 처리

| 시나리오 | 동작 |
|----------|------|
| Redis Pub/Sub 발행 실패 | `WebSocketPublisher.send` 가 try-catch 로 예외 흡수 → 호출부 트랜잭션 정상 commit |
| 역직렬화 실패 | `WebSocketRedisSubscriber.onMessage` 가 try-catch 로 흡수 → 다음 메시지 수신 계속 |
| Redis 다운 | broadcast 일시 중단. 핵심 비즈니스 로직(재고 변동, 지시서 상태)은 영향 없음. 클라이언트는 다음 화면 진입 시 RDB 기반 조회로 최신 상태 복구 |
| 클라이언트 재연결 | 끊긴 사이 발행된 메시지는 유실. 화면 진입/refresh 시 REST 조회로 동기화 |

→ **설계 원칙**: WebSocket 메시지는 **사용자 경험 개선 수단**이지 source-of-truth 가 아니다. RDB 가 최종 진실이며, WS 가 끊겨도 새로고침 시 화면이 정상 복구되어야 한다.

---

## 10. 운영 체크리스트

| 항목 | 현재 | 운영 권장 |
|------|------|-----------|
| Redis Pub/Sub 모니터링 | 별도 없음 | 채널 발행/소비 지연, 연결 상태 관찰 |
| 구독 권한 검증 | destination 분리로 자연 격리 | `ChannelInterceptor` 로 SUBSCRIBE 시 토큰 `clientId` 와 destination `{clientId}` 강제 일치 검증 |
| ws ticket 만료 | 5분 (단기) | 유지 — 핸드셰이크 직전 발급 흐름 권장 |
| 클라이언트 재연결 | 자동 (브라우저 ws lib) | exponential backoff + 화면 복구 로직 명문화 |
| 메시지 유실 허용 정책 | 새로고침 시 REST 로 복구 | 유지 — WS 는 UX 보강이며 영속 채널 아님 |
| `@TransactionalEventListener(AFTER_COMMIT)` 적용 | 일부 도메인은 트랜잭션 내부에서 직접 발행 | DB commit 실패 시 잘못된 알림 방지 위해 점진 마이그레이션 검토 |
| 인스턴스 수평 확장 | broadcast 자동 합류 | 유지 — Redis 만 공유되면 인스턴스 수 무관 |

---

## 11. 핵심 파일 위치

| 역할 | 위치 |
|------|------|
| STOMP 브로커 설정 | `stock/src/main/java/com/beyond/wbs/websocket/WebSocketConfig.java` |
| 발행 진입점 | `stock/src/main/java/com/beyond/wbs/websocket/WebSocketPublisher.java` |
| Redis 수신 → SimpleBroker 다리 | `stock/src/main/java/com/beyond/wbs/websocket/WebSocketRedisSubscriber.java` |
| 직렬화 / Redis 빈 등록 | `stock/src/main/java/com/beyond/wbs/websocket/RedisWebSocketConfig.java` |
| 봉투 | `stock/src/main/java/com/beyond/wbs/websocket/WebSocketBroadcastEnvelope.java` |
| 공통 페이로드 | `stock/src/main/java/com/beyond/wbs/websocket/WorkEventMessage.java` |
| ws ticket 발급 | `account/src/main/java/com/beyond/wbs/account/auth/JwtTokenProvider.java` (`createWsTicket`) |
| ticket 발급 API | `account/src/main/java/com/beyond/wbs/account/controller/UserController.java` (`GET /user/ws-ticket`) |
| 핸드셰이크 ticket 검증 | `apigateway/src/main/java/com/beyond/wbs/JwtAuthFilter.java` (`/stock-service/ws` 분기) |

---

## 12. 다른 명세서와의 관계

```
┌────────────────────────────────────────────┐
│ WebSocket·Redis Pub/Sub 스펙 (이 문서)       │
│   채널 계층, 봉투, broadcast 인프라           │
└─────┬───────────────────────────┬───────────┘
      │ /topic/admin/alerts/...   │ /topic/admin/{module}/...
      ↓                           ↓
┌──────────────────────┐  ┌──────────────────────┐
│ 알림·감사 로그 스펙     │  │ Kafka 스펙            │
│ (ALERT_AUDIT_SPEC)   │  │ (KAFKA_SPEC 9번 섹션) │
│ 알림 라이프사이클       │  │ 이벤트 → 알림 이력 ES  │
└──────────────────────┘  └──────────────────────┘
```

| 명세서 | 역할 |
|--------|------|
| WebSocket·Redis Pub/Sub 스펙 | 채널/봉투/broadcast 인프라 — "어떻게 전달되나" |
| 알림·감사 로그 스펙 | 알림 발생/해소 라이프사이클 — "언제 무엇을 보내나" |
| Kafka 스펙 9번 섹션 | 알림 이력 검색 파이프라인 — "이력은 어떻게 보존/색인되나" |

세 명세서는 같은 실시간 알림 흐름을 각자 다른 책임으로 분담한다.

---

**문서 끝**