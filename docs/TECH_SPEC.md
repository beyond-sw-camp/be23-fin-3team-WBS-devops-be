# 기술 명세서 (Technology Specification)

> **프로젝트명**: WBS (Warehouse & Batch System) — 멀티테넌트 WMS 플랫폼
> **문서 버전**: 1.0
> **작성일**: 2026-05-08

---

## 1. 개요

본 프로젝트는 다중 회사(client) 환경을 지원하는 **창고관리 시스템(WMS)** 으로,
Spring Cloud 기반 마이크로서비스 아키텍처(MSA)를 채택했다.
입고·출고·재고·이동·실사·반품·기타입출고의 풀 라이프사이클을 다루며,
RAG·Text-to-SQL·작업자 자동배정 등 **AI 보조 기능**과 **9종 지시서 자동 PDF 발행**을 통합 제공한다.

| 항목 | 값 |
|---|---|
| 아키텍처 스타일 | Microservices (Spring Cloud) |
| 통신 프로토콜 | REST/JSON, SSE(Server-Sent Events), Kafka Event |
| 배포 단위 | 서비스별 Spring Boot Fat-Jar |
| 주요 백킹 인프라 | MySQL · PostgreSQL+pgvector · Redis · Kafka · Elasticsearch · S3 |

---

## 2. 시스템 아키텍처

```
┌─────────────────────────────────────────┐
│         React SPA  (port 3000)          │
└────────────────┬────────────────────────┘
                 │ HTTPS / JWT
┌────────────────▼────────────────────────┐
│  Spring Cloud Gateway (port 8080)       │
│  · 단일 진입점 · JWT 검증 · CORS · 라우팅 │
└────────────────┬────────────────────────┘
                 │ Eureka 디스커버리 + 클라이언트 LB
   ┌─────────┬───┴───┬─────────┬──────────┐
   ▼         ▼       ▼         ▼          ▼
 account   master  stock   ai-service   search
 (MySQL)   (MySQL) (MySQL) (pgvector)  (ES Index)
   │         │       │         │          ▲
   │         │       └────────►│          │
   │         │   Feign 호출    │          │
   └─────────┴───────┬─────────┘          │
                     │  Kafka Topic       │
                     ▼                    │
            ┌────────────────┐            │
            │  Apache Kafka  │────────────┘
            └────────┬───────┘
                     │
                ┌────▼────┐
                │  Redis  │ (RT, 캐시, 세션)
                └─────────┘
                ┌─────────┐
                │ AWS S3  │ (PDF, 이미지)
                └─────────┘
                ┌─────────┐
                │ Ollama  │ (로컬 LLM)
                └─────────┘
```

---

## 3. 기술 스택 — 계층별 명세

### 3.1 언어 & 빌드 도구

| 기술 | 버전 | 적용 범위 | 선택 이유 |
|---|---|---|---|
| **Java** | 17 (Toolchain) | 전 모듈 | LTS, Records/Sealed/Pattern Matching 활용 |
| **Gradle** | 8.x (Wrapper) | 전 모듈 | 멀티 프로젝트 빌드, Kotlin DSL 선택지 보유 |
| **Spring Boot** | 3.4.8 | 전 모듈 | Jakarta EE 9+ 마이그레이션 완료, Native Image 지원 |
| **Spring Cloud** | 2024.0.0 (BOM) | 전 모듈 | MSA 컴포넌트 통합 관리 |
| **Spring AI** | 1.0.0 (BOM) | ai-service | LLM·RAG·VectorStore 표준 추상화 |

### 3.2 마이크로서비스 인프라

| 기술 | 버전 | 역할 |
|---|---|---|
| **Spring Cloud Gateway** | 4.x (BOM) | API Gateway · JWT 필터 · CORS · 라우팅 (Reactive Netty) |
| **Netflix Eureka Server** | 4.x | 서비스 레지스트리 (port 8761) |
| **Netflix Eureka Client** | 4.x | 자동 등록 + Heartbeat |
| **Spring Cloud OpenFeign** | 4.x | 서비스 간 선언적 REST 호출 (master·stock·ai-service) |
| **Spring Cloud LoadBalancer** | 4.x | 클라이언트 사이드 LB |
| **Spring WebFlux** | 6.x | Reactive 스택 (apigateway, ai-service의 SSE 스트리밍) |
| **Spring MVC (Tomcat)** | 6.x | 일반 서비스 (account, master, stock, search) |

### 3.3 데이터 저장소

| 기술 | 버전 | 사용 모듈 | 데이터베이스명 |
|---|---|---|---|
| **MySQL** | 8.0 | account, master, stock | account_db, master_db, stock_db |
| **PostgreSQL + pgvector** | pg16 / pgvector latest | ai-service | ai_db (벡터 임베딩 저장) |
| **Redis** | 7-alpine | account, master, stock, search | RT(Refresh Token), 캐시, RAG 응답 캐시 (AOF 영속화) |
| **Spring Data JPA / Hibernate** | 6.x | 모든 RDBMS 모듈 | ORM |
| **HikariCP** | 자동 주입 | 모든 DB 모듈 | 커넥션 풀 |
| **Spring Data Elasticsearch** | (Boot Starter) | stock, search | Elasticsearch 매핑 |

### 3.4 메시징 & 검색

| 기술 | 버전 | 역할 |
|---|---|---|
| **Apache Kafka** | Confluent 7.5.0 | 도메인 이벤트, 감사로그 비동기 전파, PDF 발행 트리거 |
| **Kafka Streams** | (Boot 동봉) | stock 모듈의 스트림 가공 |
| **Apache Zookeeper** | Confluent 7.5.0 | Kafka 코디네이션 |
| **Spring Kafka** | 3.x | KafkaTemplate, @KafkaListener |
| **Elasticsearch** | 9.3.1 | 풀텍스트 검색 (감사로그, 입고지시서, 지시서 문서함) |
| **Kibana** | 9.3.1 | ES 운영·디버깅 UI (port 5601) |
| **elasticsearch-java client** | 9.3.1 | common 모듈에서 Low-level Client 직접 사용 |
| **elasticsearch-rest-client** | 9.3.1 | REST 통신 |
| **Provectus Kafka UI** | latest | Kafka 토픽 모니터링 (port 8090) |

### 3.5 보안 & 인증

| 기술 | 버전 | 역할 |
|---|---|---|
| **JWT (jjwt)** | 0.11.5 (api/impl/jackson) | AT(30분) / RT(약 20일) 발급·검증, HS512 알고리즘 |
| **Spring Security Crypto** | 6.x | BCrypt 패스워드 해싱 (PasswordEncoder) |
| **자체 `@CheckPermission`** | - | (Resource × Action) 기반 RBAC 메서드 어노테이션 |
| **자체 `@AuditLog`** | - | 호출 이력 자동 캡처 → DB + Kafka → Elasticsearch |
| **헤더 기반 멀티테넌시** | - | `X-Client-Id`(회사 UUID), `X-User-Id`(사용자 UUID) 전 서비스 전파 |

**권한 체계**:
- Resource enum: `INBOUND, OUTBOUND, TRANSFER, INVENTORY, STOCK_COUNT, ETC_INOUT, MASTER, STATISTICS`
- Action enum: `CREATE, READ, UPDATE, DELETE, APPROVE`
- Role: `ADMIN`(전체), `MANAGER`(전체), `OPERATOR`(READ + 비-MASTER UPDATE)

### 3.6 AI / 머신러닝

| 기술 | 버전 | 역할 |
|---|---|---|
| **Spring AI** | 1.0.0 | LLM 통합 추상화 |
| **spring-ai-starter-model-openai** | 1.0.0 | OpenAI 호환 API 클라이언트 (Ollama OpenAI-compatible endpoint 활용) |
| **spring-ai-starter-vector-store-pgvector** | 1.0.0 | PostgreSQL pgvector 백엔드 |
| **spring-ai-tika-document-reader** | 1.0.0 | PDF/DOCX 등 다양한 문서 파싱 (RAG 인제스트) |
| **spring-ai-advisors-vector-store** | 1.0.0 | `QuestionAnswerAdvisor` — RAG 응답 생성 |
| **Ollama** | 외부 데몬 | 로컬 LLM 추론 (기본: llama3.2:3b, 임베딩: bge-m3 1024차원) |

**제공 기능**:
- **RAG 기반 챗봇** (스트리밍 SSE 지원)
- **Text-to-SQL** (자연어 → SQL 변환·실행, 별도 read-only DataSource 분리)
- **작업자 자동배정** (위치·작업량 기반 추천)
- **재고 예측** (`InventoryForecastService`)
- **ESG 챗봇** (별도 도메인 채팅)

### 3.7 클라우드 & 외부 연동

| 기술 | 버전 | 역할 |
|---|---|---|
| **AWS SDK for Java v2 (S3)** | 2.29.50 | 회사별 폴더 구조로 PDF·이미지 업로드 (ap-northeast-2) |
| **OpenPDF** | 1.3.30 | PDF 엔진 |
| **Flying Saucer (XHTML→PDF)** | 9.1.22 | Thymeleaf 렌더링 결과를 PDF로 변환 |
| **Spring Boot Thymeleaf Starter** | 3.4.8 | 9종 지시서 PDF 템플릿 렌더링 |
| **Spring Boot Mail Starter** | 3.4.8 | 알림·통보 메일 발송 (stock) |
| **Spring Boot WebSocket Starter** | 3.4.8 | 실시간 알림 (stock) |

**자동 PDF 발행 9종**: 출고지시서, 입고지시서, 입고전표, 적치지시서, 이동지시서, 피킹리스트, 출고전표, 기타입출고, 실사지시서

### 3.8 도구 라이브러리

| 기술 | 버전 | 역할 |
|---|---|---|
| **Lombok** | 1.18.x (Boot 관리) | 보일러플레이트 제거 (`@Getter`, `@Builder` 등) |
| **uuid-creator (f4b6a3)** | 6.0.0 | **UUIDv7** 생성 (시간순 정렬 가능, B-Tree 인덱스 친화적) |
| **자체 `UuidBinaryConverter`** | - | UUID ↔ MySQL `BINARY(16)` 매핑 |
| **Jakarta Bean Validation** | 3.x | DTO 검증 (`@Valid`, `@NotNull`) |
| **MySQL Connector/J** | (Boot 관리) | 모든 MySQL 모듈 드라이버 |
| **MariaDB Connector/J** | (Boot 관리) | stock 모듈 추가 드라이버 호환 |

### 3.9 테스트

| 기술 | 버전 | 역할 |
|---|---|---|
| **JUnit Jupiter (JUnit 5)** | 5.x | 단위/통합 테스트 |
| **Spring Boot Test** | 3.4.8 | `@SpringBootTest`, `@DataJpaTest` |
| **Reactor Test** | 3.x | ai-service의 WebFlux 스트림 테스트 |

---

## 4. 모듈별 사용 기술 매트릭스

| 모듈 | 포트 | DB | 주요 기술 | 책임 |
|---|---|---|---|---|
| **eureka** | 8761 (고정) | - | Eureka Server | 서비스 레지스트리 |
| **apigateway** | 8080 (고정) | - | Spring Cloud Gateway, JWT, WebFlux | 단일 진입점, 인증 필터 |
| **account** | 동적 | MySQL | Spring Web, JPA, Redis, Kafka, JWT | 사용자·회사·권한 |
| **master** | 동적 | MySQL | Spring Web, JPA, Redis, Kafka, OpenFeign, S3 | 마스터 데이터(상품·창고·존·랙·로케이션·레이아웃) |
| **stock** | 동적 | MySQL | Spring Web, JPA, Redis, Kafka(+Streams), ES, OpenFeign, S3, Thymeleaf+OpenPDF, WebSocket, Mail | 입고·출고·재고·이동·실사·기타입출고·PDF발행 |
| **search** | 동적 | (없음) | Spring Web, Kafka Consumer, Elasticsearch | 감사로그 인덱싱·검색 |
| **ai-service** | 동적 | PostgreSQL+pgvector | WebFlux, Spring AI, JPA, OpenFeign | RAG, Text-to-SQL, 자동배정, 예측 |
| **common** | (라이브러리) | - | Spring Web/JPA/Redis/Kafka, ES Java Client, S3, JWT, UUID | 공통 도메인 + Maven Local 배포 |

> **공통 모듈은 `mavenLocal()` 로 발행**되어 모든 서비스가 의존 (`com.beyond:common:0.0.1-SNAPSHOT`)

---

## 5. 인프라 구성 (Docker Compose)

| 컨테이너 | 이미지 | 노출 포트 | 데이터 영속성 |
|---|---|---|---|
| wbs-elasticsearch | docker.elastic.co/elasticsearch/elasticsearch:9.3.1 | 9200 | volume `elasticsearch-data` |
| wbs-kibana | docker.elastic.co/kibana/kibana:9.3.1 | 5601 | - |
| wbs-zookeeper | confluentinc/cp-zookeeper:7.5.0 | 2181 | - |
| wbs-kafka | confluentinc/cp-kafka:7.5.0 | 9092 | - |
| wbs-kafka-ui | provectuslabs/kafka-ui:latest | 8090 | - |
| wbs-ai-postgres | pgvector/pgvector:pg16 | 5433 → 5432 | volume `ai-postgres-data` (init.sql 자동 실행) |
| redis-container | redis:7-alpine | 6379 | volume `redis-data` (AOF) |

> **로컬 MySQL 8.0** 은 호스트에 직접 설치되어 사용 (3306).

---

## 6. 주요 도메인 핵심 기술 적용

### 6.1 재고 무결성

| 항목 | 적용 기술 |
|---|---|
| 동시성 제어 | JPA `@Lock(PESSIMISTIC_WRITE)` — 입출고 시 동일 재고 row 직렬화 |
| 5단계 상태 모델 | `available / reserved / defect / pending / incoming` (커스텀 enum) |
| 감사 이력 | `InventoryTransaction` 엔티티 — `qtyBefore/qtyAfter`, `statusFrom/statusTo`, `refId/refType`, `createdBy` 완전 추적 |
| 멀티테넌시 격리 | 모든 엔티티·쿼리에 `clientId` 필터 강제 |

### 6.2 비동기 흐름

| 흐름 | 기술 |
|---|---|
| 지시서 PDF 발행 | Kafka 이벤트 → 스트림 컨슈머 → Thymeleaf 렌더링 → Flying Saucer PDF → S3 업로드 |
| 감사로그 인덱싱 | `@AuditLog` → Kafka → search-service 컨슈머 → Elasticsearch 인덱스 |
| 웨이브 자동 생성 | `@Scheduled` cron(매일 07:00) — 일괄 묶음 + 수동 트리거 가능 |
| 저재고 알림 | 재고 변경 시 `minStockQty` 비교 → 알림 발행 |

### 6.3 인증·인가 흐름

```
[로그인] → /account-service/user/doLogin
        → JWT(AT/RT) 발급 → Redis에 RT 저장
[요청]   → API Gateway가 AT 검증
        → 사용자 정보(X-Client-Id, X-User-Id) 헤더 주입
        → 다운스트림 서비스 라우팅
[권한]   → @CheckPermission(Resource, Action) 메서드 인터셉터
        → RolePermission 매핑 조회 → 거부 시 403
```

### 6.4 UUID 전략

- **모든 PK는 UUIDv7** (`uuid-creator` 라이브러리)
- MySQL은 `BINARY(16)` 컬럼에 `UuidBinaryConverter`로 매핑 → 16바이트 저장 + 시간순 정렬 가능
- 회사·사용자 UUID는 **명명 규칙**으로 운영자 가독성 확보:
  - 회사: `01935c00-0000-7000-8000-000000000XXX`
  - 사용자: `01935c00-0000-8000-8000-000000000XXX`

---

## 7. 외부 통합 명세

| 통합 대상 | 프로토콜 | 인증 | 용도 |
|---|---|---|---|
| **AWS S3** | HTTPS | IAM Access Key | 회사별 폴더(`{clientId}/`) 하위에 PDF·증빙 사진 업로드 |
| **Ollama** | HTTP (OpenAI-compatible API) | (없음, 로컬) | LLM 추론 + 임베딩 (default `http://localhost:11434/v1`) |
| **ERP (시뮬레이션)** | 내부 DB 테이블 | - | `ErpPurchaseOrders`, `ErpSalesOrders` 더미 — 실제 EDI 856/940/945 미구현 |

---

## 8. 운영 환경 구성

| 환경 | 인프라 |
|---|---|
| 로컬 개발 | Docker Compose (ES/Kafka/PG/Redis) + 호스트 MySQL + 호스트 Ollama |
| 스테이징(예정) | AWS RDS (MySQL + PostgreSQL) + ECS/EC2 + S3 + ElastiCache + MSK |
| 운영(예정) | 위 + Multi-AZ + Auto Scaling + CloudWatch |

---

## 9. 버전 매트릭스 (요약)

```
Java                         17 LTS
Spring Boot                  3.4.8
Spring Cloud                 2024.0.0
Spring AI                    1.0.0
MySQL                        8.0
PostgreSQL                   16 (with pgvector)
Redis                        7-alpine
Apache Kafka                 7.5.0 (Confluent)
Apache Zookeeper             7.5.0 (Confluent)
Elasticsearch / Kibana       9.3.1
JJWT                         0.11.5
uuid-creator                 6.0.0
AWS SDK for Java v2 (S3)     2.29.50
OpenPDF                      1.3.30
Flying Saucer (XHTML→PDF)    9.1.22
elasticsearch-java client    9.3.1
```

---

## 10. 비기능 요구사항 충족 기술 매핑

| NFR 항목 | 적용 기술 |
|---|---|
| **확장성(Scalability)** | Eureka + Spring Cloud Gateway + 동적 포트 할당으로 서비스 N대 수평 확장 가능 |
| **가용성(Availability)** | Kafka 비동기 처리 → 다운스트림 장애가 메인 흐름 차단하지 않음. 운영 단계에서는 Multi-AZ |
| **보안(Security)** | JWT + RBAC + 멀티테넌시 격리 + S3 IAM. 향후 Secrets Manager 연동 예정 |
| **관측가능성(Observability)** | `@AuditLog` 자동 인덱싱 + Kibana 대시보드. (향후 Prometheus/Grafana, Sleuth 추가 권장) |
| **추적가능성(Traceability)** | InventoryTransaction의 완전 체인-of-커스터디 감사 |
| **유지보수성(Maintainability)** | Common 모듈 라이브러리화 + UUIDv7 + 명확한 모듈 경계 |

---

## 11. 향후 보강 예정 기술 (참고)

| 영역 | 도입 후보 |
|---|---|
| 분산 추적 | Spring Cloud Sleuth + Zipkin / OpenTelemetry |
| 회복성 | Resilience4j (Circuit Breaker, Retry) |
| 비밀 관리 | AWS Secrets Manager + Spring Cloud AWS |
| API 문서화 | springdoc-openapi (Swagger UI) |
| 라벨 출력 | ZPL 템플릿 (Zebra 프린터) |
| 분산 락(고도화) | Redisson 기반 분산 락 |

---

## 부록 A. 참고 파일 위치

| 파일 | 위치 |
|---|---|
| 멀티모듈 설정 | 각 모듈의 `build.gradle` |
| 인프라 정의 | `docker-compose.yml` |
| 인증·권한 | `account/src/main/java/com/beyond/wbs/account/...` |
| 공통 라이브러리 | `common/src/main/java/com/beyond/wbs/...` |
| AI 설정 | `ai-service/src/main/java/com/beyond/wbs/ai/config/` |
| 시드 데이터 | `account/.../init/InitialDataLoad.java`, `master/stock/src/main/resources/data.sql` |

---

**문서 끝** — 본 명세서는 코드 베이스의 build.gradle, application.yml, docker-compose.yml을 기반으로 작성되었으며, 변경 시 갱신이 필요하다.
