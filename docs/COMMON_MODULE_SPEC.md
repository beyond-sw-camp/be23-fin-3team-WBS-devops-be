# Common 모듈 기술 명세서

> **모듈명**: `common` (`com.beyond:common:0.0.1-SNAPSHOT`)
> **역할**: 전사 공통 기능 라이브러리 (인증·권한·감사로그·인프라 설정·코드 채번)
> **작성일**: 2026-05-08

---

## 1. 모듈 개요

| 항목 | 값 |
|---|---|
| 위치 | [common/](../common/) |
| 패키지 루트 | `com.beyond.wbs` |
| Java 클래스 수 | 약 51개 |
| 배포 방식 | JAR 라이브러리 (bootJar 비활성화) |
| 발행 저장소 | Maven Local + GitHub Packages |
| 의존하는 서비스 | account, master, stock, search, ai-service (5개 전부) |

**핵심 가치**: 보안·감사·인프라 설정을 중앙화하여 마이크로서비스 일관성 보장. 한 곳에서 변경 → 전 모듈 자동 반영.

---

## 2. 빌드 구성

### 2.1 라이브러리화 설정

📂 [common/build.gradle](../common/build.gradle)

```gradle
bootJar { enabled = false }   // Spring Boot fat JAR 생성 안 함
jar {
    enabled = true
    archiveClassifier = ''     // plain 제거 → common-0.0.1-SNAPSHOT.jar
}
```

→ 일반 라이브러리 JAR로 빌드되어 다른 모듈이 의존성으로 가져올 수 있음.

### 2.2 발행 (Publishing)

```gradle
publishing {
    repositories {
        maven {
            name = 'GitHubPackages'
            url = uri('https://maven.pkg.github.com/beyond-sw-camp/be23-fin-3team-WBS-be')
            credentials {
                username = project.findProperty('gpr.user') ?: System.getenv('GITHUB_ACTOR')
                password = project.findProperty('gpr.token') ?: System.getenv('GITHUB_TOKEN')
            }
        }
    }
}
```

| 시나리오 | 명령어 | 발행 위치 |
|---|---|---|
| 로컬 개발 | `./gradlew :common:publishToMavenLocal` | `~/.m2/repository/com/beyond/common/` |
| CI 공유 | `./gradlew :common:publish` | GitHub Packages |

### 2.3 의존성 (build.gradle:34-49)

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| spring-boot-starter-web | 3.4.8 | 공통 컨트롤러 (감사로그 조회 API) |
| spring-boot-starter-data-jpa | 3.4.8 | 공통 엔티티 |
| spring-boot-starter-validation | 3.4.8 | DTO 검증 |
| spring-boot-starter-data-redis | 3.4.8 | 권한 캐시 |
| spring-kafka | (Boot) | 이벤트 발행 |
| elasticsearch-java | 9.3.1 | ES 클라이언트 |
| elasticsearch-rest-client | 9.3.1 | REST 통신 |
| awssdk:s3 | 2.29.50 | S3 업로드 |
| uuid-creator (f4b6a3) | 6.0.0 | UUIDv7 생성 |
| jjwt-api / impl / jackson | 0.11.5 | JWT |

### 2.4 다른 모듈의 의존성 선언

```gradle
// account/master/stock/search/ai-service 의 build.gradle
implementation 'com.beyond:common:0.0.1-SNAPSHOT'
```

---

## 3. 인증 / 권한 시스템

### 3.1 권한 체크 어노테이션

📂 `common/src/main/java/com/beyond/wbs/auth/CheckPermission.java`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckPermission {
    Resource resource();
    Action   action();
}
```

**사용 예**:
```java
@CheckPermission(resource = Resource.INBOUND, action = Action.CREATE)
public ResponseEntity<?> createInbound(...) { ... }
```

### 3.2 Resource Enum

📂 `common/src/main/java/com/beyond/wbs/auth/Resource.java`

| 코드 | 이름 | 값 |
|---|---|---|
| RE001 | 입고 관리 | `INBOUND` |
| RE002 | 출고 관리 | `OUTBOUND` |
| RE003 | 이동 관리 | `TRANSFER` |
| RE004 | 재고 관리 | `INVENTORY` |
| RE005 | 실사 관리 | `STOCK_COUNT` |
| RE006 | 기타 입출고 | `ETC_INOUT` |
| RE007 | 마스터 관리 | `MASTER` |
| RE008 | 통계 | `STATISTICS` |

### 3.3 Action Enum

📂 `common/src/main/java/com/beyond/wbs/auth/Action.java`

| 코드 | 이름 | 값 |
|---|---|---|
| AC001 | 생성 | `CREATE` |
| AC002 | 조회 | `READ` |
| AC003 | 수정 | `UPDATE` |
| AC004 | 삭제 | `DELETE` |
| AC005 | 승인 | `APPROVE` |

### 3.4 권한 체크 AOP

📂 `common/src/main/java/com/beyond/wbs/auth/PermissionAspect.java`

**처리 흐름**:
```
1. X-Is-Developer 헤더가 "true" → 모든 권한 통과
2. X-User-Id 헤더에서 사용자 추출
3. Redis(@Qualifier("accountRedis")) 에서 "perm:{userId}" 조회
   → 포맷: "INBOUND:CREATE,INBOUND:READ,OUTBOUND:APPROVE,..."
4. required = "INBOUND:CREATE" 와 비교
5. 매치 안 되면 SecurityException → CommonExceptionHandler가 403 반환
```

---

## 4. 감사 로그 (`@AuditLog`)

### 4.1 어노테이션 정의

📂 `common/src/main/java/com/beyond/wbs/audit/AuditLog.java`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    String action() default "";  // 빈 값이면 HTTP Method에서 자동 추출
}
```

### 4.2 HTTP Method → 액션 자동 추출

| HTTP | 액션 |
|---|---|
| GET | 조회 |
| POST | 생성 |
| PUT | 수정 |
| PATCH | 수정 |
| DELETE | 삭제 |

비즈니스 액션은 명시적 지정: `@AuditLog(action = "승인")`

### 4.3 AuditLogAspect — 자동 캡처 항목

📂 `common/src/main/java/com/beyond/wbs/audit/AuditLogAspect.java`

| 항목 | 추출 위치 |
|---|---|
| clientId | X-Client-Id 헤더 |
| userId | X-User-Id 헤더 |
| userName | X-User-Name 헤더 |
| ipAddress | X-Forwarded-For / remoteAddr |
| httpMethod | request.getMethod() |
| requestUri | request.getRequestURI() |
| action | `@AuditLog.action()` 또는 자동 추출 |
| responseStatus | ResponseEntity 상태 코드 |
| durationMs | 요청~응답 경과 시간 |

**로그인 특수 처리**:
- `/doLogin` 호출은 인증 전이라 X-User-Id 없음
- 성공 시 `action="로그인"`, 실패 시 `action="로그인실패"`

### 4.4 저장 + Kafka 발행

📂 `common/src/main/java/com/beyond/wbs/audit/AuditLogEventPublisher.java`

```
[AuditLogAspect]
   ├─ 1. RDB(audit_logs) 저장 (BINARY(16) PK + 인덱스)
   └─ 2. Kafka 토픽 "audit.created" 비동기 발행
              ↓
   [search-service: AuditLogEventConsumer (groupId=search-group)]
              ↓
   [Elasticsearch 인덱스 audit-logs-v2 색인]
```

**실패 복원력**:
- ES 색인 실패해도 RDB에 원본 보존 → 나중에 재색인 가능
- Kafka 발행 실패해도 RDB는 정상 저장 (예외 무시)

### 4.5 audit_logs 테이블

```sql
CREATE TABLE audit_logs (
  id BINARY(16) PRIMARY KEY,           -- TimeOrderedEpoch UUID
  client_id BINARY(16),
  user_id BINARY(16),
  user_name VARCHAR(50),
  action VARCHAR(30) NOT NULL,
  http_method VARCHAR(10) NOT NULL,
  request_uri VARCHAR(255) NOT NULL,
  entity_name VARCHAR(50),
  request_body TEXT,                    -- 알림 이벤트의 payload 운반용
  response_status INT,
  ip_address VARCHAR(45),
  duration_ms BIGINT,
  created_at DATETIME NOT NULL,
  INDEX (client_id, created_at DESC)
);
```

### 4.6 조회 API

📂 `common/src/main/java/com/beyond/wbs/audit/AuditLogController.java`

```
GET /audit-logs?action=생성&from=2026-04-01&to=2026-04-20&page=0&size=20
```

→ DB 직접 조회 (Elasticsearch 검색은 search-service의 별도 API)

---

## 5. 공통 인프라 설정

### 5.1 Redis 설정 — `RedisConfig`

📂 `common/src/main/java/com/beyond/wbs/redis/RedisConfig.java`

- **Database 0** 사용
- Bean 이름: `@Qualifier("accountRedis")`
- Key/Value 모두 `StringRedisSerializer`
- 활용처: 권한 캐시(`perm:{userId}`), Refresh Token 저장

### 5.2 AWS S3 설정 — `AwsS3Config`

📂 `common/src/main/java/com/beyond/wbs/s3/AwsS3Config.java`

```java
@ConditionalOnProperty(name = "aws.credentials.access-key")
@Bean public S3Client client() { ... }
@Bean public S3Presigner s3Presigner() { ... }
```

→ 자격증명이 있는 환경에서만 빈 등록. 두 빈을 모든 모듈이 공통 사용.

### 5.3 Elasticsearch 설정 — `ElasticsearchConfig`

📂 `common/src/main/java/com/beyond/wbs/search/ElasticsearchConfig.java`

```java
@ConditionalOnProperty(name = "elasticsearch.enabled", havingValue = "true")
@Bean public ElasticsearchClient elasticsearchClient(...)
```

**프로퍼티** (`ElasticsearchProperties`):
- `endpoint` (기본: `http://localhost:9200`)
- `authType` (NONE / BASIC / IAM(미지원))
- `username`, `password`
- `awsRegion` (기본: `ap-northeast-2`)

### 5.4 Kafka Producer 설정 — `CommonKafkaProducerConfig`

📂 `common/src/main/java/com/beyond/wbs/kafka/CommonKafkaProducerConfig.java`

- `KafkaTemplate<String, Object>` 를 `@Primary` 로 등록
- Value Serializer: **`JsonSerializer`** (POJO 직렬화)
- 이유: Spring 기본은 `StringSerializer` → 복잡한 이벤트 객체 보낼 수 없음

---

## 6. 공통 도메인 / 컨버터 / DTO

### 6.1 BaseTimeEntity

📂 `common/src/main/java/com/beyond/wbs/domain/BaseTimeEntity.java`

```java
@MappedSuperclass @Getter
public class BaseTimeEntity {
    @CreationTimestamp private LocalDateTime createdTime;
    @UpdateTimestamp   private LocalDateTime updatedTime;
}
```

→ 대부분의 도메인 엔티티가 상속.

### 6.2 UuidBinaryConverter — UUID ↔ BINARY(16)

📂 `common/src/main/java/com/beyond/wbs/converter/UuidBinaryConverter.java`

```java
@Converter(autoApply = true)
public class UuidBinaryConverter implements AttributeConverter<UUID, byte[]> {
    // CHAR(36) 대신 BINARY(16) 저장
    // 디스크 사용량 ≈ 절반, B-Tree 인덱스 성능 ↑
}
```

→ `autoApply = true` → 모든 UUID 필드에 자동 적용 (별도 `@Convert` 불필요).

### 6.3 SystemUser — 시스템 사용자 식별자

📂 `common/src/main/java/com/beyond/wbs/common/SystemUser.java`

```java
public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
```

**용도**: 스케줄러·배치·자동작업의 행위자 표시. created_by/user_id에 이 UUID가 있으면 "시스템이 한 작업"으로 식별 가능.

### 6.4 CommonErrorDto

```java
{ "status_code": 400, "error_message": "..." }
```

---

## 7. 공통 예외 처리

📂 `common/src/main/java/com/beyond/wbs/exception/CommonExceptionHandler.java`

```java
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
@RestControllerAdvice
public class CommonExceptionHandler { ... }
```

→ Servlet 환경(MVC)에서만 등록. WebFlux 모듈(ai-service)에서는 자동 스킵.

### 매핑표

| 예외 | HTTP 상태 |
|---|---|
| `IllegalArgumentException` | 400 |
| `IllegalStateException` | 400 (비즈니스 상태 위반) |
| `MethodArgumentNotValidException` | 400 (`@Valid` 실패) |
| `MissingServletRequestParameterException` | 400 |
| `MethodArgumentTypeMismatchException` | 400 (UUID 형식 오류 등) |
| `NoSuchElementException` | 404 |
| `EntityNotFoundException` | 404 |
| `SecurityException` | 403 (PermissionAspect 발생) |
| `Exception` (catch-all) | 500 |

---

## 8. 코드 채번 시스템

### 8.1 Sequence 도메인 (비관적 락 기반)

📂 `common/src/main/java/com/beyond/wbs/code/domain/Sequence.java`
📂 `common/src/main/java/com/beyond/wbs/code/repository/SequenceRepository.java`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT s FROM Sequence s WHERE s.type = :type")
Optional<Sequence> findByTypeWithLock(@Param("type") String type);
```

→ 동시 채번 시 동일 번호 발급 방지.

### 8.2 NumberingUtil — 지시서 번호 생성

📂 `common/src/main/java/com/beyond/wbs/code/NumberingUtil.java`

| 메서드 | 포맷 예 | 용도 |
|---|---|---|
| `generateOrderNo()` | OB-20260415-00001 | 출고지시서 |
| `generatePickingNo()` | PK-20260415-00001 | 피킹리스트 |
| `generateDispatchNo()` | DS-20260415-00001 | 출고전표 |
| `generateInboundOrderNo()` | IB-20260415-00001 | 입고지시서 |
| `generatePlacementNo()` | PL-20260415-00001 | 적치지시서 |
| `generateReceiptNo()` | RC-20260415-00001 | 입고전표 |
| `generateTransferOrderNo()` | TR-20260415-00001 | 이동지시서 |
| `generateEtcInoutNo()` | ETC-20260415-00001 | 기타입출고 |
| `generateStockCountNo()` | SC-20260415-00001 | 재고실사 |

### 8.3 CodeGenerator — 마스터 코드 생성

📂 `common/src/main/java/com/beyond/wbs/code/CodeGenerator.java`

| 대상 | 포맷 예 |
|---|---|
| 창고 | `WH-SEL-NOR-001` |
| 구역 | `ZN-SEL-ELC-001` |
| 랙 | `RK-ZN-SEL-ELC-001-LGX-001` |
| 로케이션 | `LC-RK-ZN-SEL-ELC-001-LGX-001-03` |
| 상품 | `PRD-20260415-001` |
| 상품 SKU | `ELC-001` |

### 8.4 RegionCode (지역 코드)

| 코드 | 지역 |
|---|---|
| SEL | 서울 |
| PUS | 부산 |
| DAE | 대구 |
| ICN | 인천 |
| GWJ | 광주 |
| DJN | 대전 |
| USN | 울산 |

---

## 9. Kafka 이벤트 DTO

📂 `common/src/main/java/com/beyond/wbs/kafka/event/`

### 9.1 AuditLogEvent — 감사로그 이벤트

| 필드 | 설명 |
|---|---|
| id, clientId, userId | 식별자 |
| serviceName | spring.application.name |
| action, httpMethod, requestUri, entityName | 행위 |
| responseStatus, ipAddress, durationMs | 결과/메타 |
| requestBody | 알림 이벤트(`AlertAuditLogger`)의 payload 운반용 |

**토픽**: `audit.created` → search-service 컨슈머

### 9.2 InboundStockEvent — 입고 재고 이벤트

```java
{ clientId, warehouseId, refId, userId, originType,
  items: [{ productId, locationId, qty }],
  defectItems: [...]  // 검수 시 불량 분리
}
```

**토픽들**:
| 토픽 | 시점 | 효과 |
|---|---|---|
| `inbound.approved` | 승인 | 입고예정 수량 +qty |
| `inbound.inspected` | 검수 정상품 | 검수중 수량 +qty |
| `inbound.defect` | 검수 불량 | 불량 수량 +qty |
| `inbound.placed` | 적치 완료 | 가용 수량 +qty |

### 9.3 OutboundStockEvent — 출고 재고 이벤트

**토픽들**:
| 토픽 | 시점 | 효과 |
|---|---|---|
| `outbound.approved` | 승인 | 가용 → 예약 |
| `outbound.cancelled` | 취소 | 예약 → 가용 원복 |
| `outbound.completed` | 확정 | 예약 차감, 실물 출고 |

### 9.4 기타 이벤트

- `TransferStockEvent` — 창고 간 이동
- `EtcInoutStockEvent` — 기타 입출고
- `InstructionDocumentIssuedEvent` — 지시서 발행 트리거 → instruction.issued 토픽

---

## 10. 지시서 메타데이터 & 불량 증빙

### 10.1 InstructionDocument 엔티티

📂 `common/src/main/java/com/beyond/wbs/document/instruction/domain/InstructionDocument.java`

→ 9종 PDF 발행 메타데이터. 자세한 내용은 [PDF_SYSTEM_SPEC.md](PDF_SYSTEM_SPEC.md) 참조.

### 10.2 DefectEvidenceS3Uploader

📂 `common/src/main/java/com/beyond/wbs/evidence/defect/s3/DefectEvidenceS3Uploader.java`

```
@ConditionalOnProperty(name = "aws.s3.defect-evidence-bucket")
```

**S3 키 패턴**:
```
defect-evidence/{clientId}/{sourceType}/{yyyy-MM}/{sourceId}/{evidenceId}.{ext}
```

**메서드**:
- `uploadBytes(s3Key, contentType, bytes)` — PUT
- `presignDownload(s3Key)` — 다운로드 URL 발급 (TTL 300s)
- `headOrNull(s3Key)` — 객체 존재 검증
- `delete(s3Key)` — 삭제

자세한 내용은 [S3_INTEGRATION_SPEC.md](S3_INTEGRATION_SPEC.md) 참조.

---

## 11. 모듈 간 의존성 흐름

```
[common] (라이브러리 JAR, mavenLocal/GitHub Packages)
    │
    ├── account     (권한·Redis·예외처리·감사로그)
    ├── master      (코드생성·감사로그·예외처리·S3)
    ├── stock       (코드생성·Kafka 이벤트·감사로그·S3·인스트럭션)
    ├── search      (ES 설정·Kafka 컨슈머·감사로그 조회)
    └── ai-service  (UuidConverter·DTO만 — Servlet 빈은 자동 스킵)
```

### 로컬 개발 사이클

```bash
# 1. common 변경
cd common && ./gradlew publishToMavenLocal

# 2. 의존 모듈 빌드 (자동으로 ~/.m2 에서 가져옴)
cd ../stock && ./gradlew build
```

---

## 12. 확장 시 가이드

| 추가하려는 것 | 추가 위치 |
|---|---|
| 새 권한 리소스 | `auth/Resource.java` |
| 새 Kafka 이벤트 | `kafka/event/` |
| 공통 예외 처리 추가 | `exception/CommonExceptionHandler.java` |
| 공통 인프라 빈 | `*/Config.java` (Conditional 패턴 권장) |
| 공통 도메인 엔티티 | `domain/` |

---

## 부록 A. 디렉터리 구조

```
common/src/main/java/com/beyond/wbs/
├── audit/             # @AuditLog 관련
├── auth/              # 권한 (CheckPermission, Resource, Action, Aspect)
├── code/              # 채번 (NumberingUtil, CodeGenerator, Sequence)
├── common/            # SystemUser
├── converter/         # UuidBinaryConverter
├── document/          # InstructionDocument
├── domain/            # BaseTimeEntity
├── dtos/              # CommonErrorDto
├── evidence/          # DefectEvidence (불량 증빙)
├── exception/         # CommonExceptionHandler
├── kafka/             # KafkaConfig + 이벤트 DTO
├── redis/             # RedisConfig
├── s3/                # AwsS3Config
└── search/            # ElasticsearchConfig
```

---

**문서 끝** — 변경 시 갱신 필요.
