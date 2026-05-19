# 9종 지시서 PDF 시스템 기술 명세서

> **목적**: 도메인 이벤트 기반으로 9종 지시서를 자동 PDF로 발행하고 S3에 저장
> **핵심 스택**: Spring Events → Kafka → Thymeleaf → Flying Saucer → OpenPDF → AWS S3
> **작성일**: 2026-05-08

---

## 1. 개요

### 1.1 발행 대상 9종

| # | 문서명 | docType | 트리거 시점 |
|---|---|---|---|
| 1 | 출고지시서 | `OUTBOUND_ORDER` | 출고지시서 승인 시 |
| 2 | 입고지시서 | `INBOUND_ORDER` | 입고지시서 승인 시 |
| 3 | 입고전표 | `INBOUND_RECEIPT` | 검수 완료 시 |
| 4 | 적치지시서 | `PLACEMENT_ORDER` | 검수 완료 시 (자동 생성된 경우) |
| 5 | 이동지시서 | `TRANSFER_ORDER` | 이동지시서 승인 시 |
| 6 | 피킹리스트 | `PICKING_LIST` | 피킹리스트 할당 시 |
| 7 | 출고전표 | `OUTBOUND_DISPATCH` | 출고 완료 시 |
| 8 | 기타입출고지시서 | `ETC_INOUT_ORDER` | 기타입출고 생성 시 |
| 9 | 실사지시서 | `STOCK_COUNT_ORDER` | 실사 시작 시 |

### 1.2 설계 원칙

- **비동기**: 트랜잭션 커밋 후 Kafka 큐에 적재 → 컨슈머가 PDF 생성
- **멱등성**: SHA-256 비교로 동일 내용 재발행 방지
- **재시도**: Kafka `@RetryableTopic` 으로 3회 자동 재시도, 실패 시 DLQ
- **버전 관리**: 같은 source의 재발행마다 version 증가 (1, 2, 3...)
- **회복성**: 마이크로서비스 호출 실패 시 best-effort 진행 (PDF 생성 차단 안 함)

---

## 2. 라이브러리 의존성

📂 [stock/build.gradle](../stock/build.gradle) (Lines 54-57)

```gradle
// 지시서 PDF 발행 (Thymeleaf → Flying Saucer → OpenPDF)
implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'
implementation 'com.github.librepdf:openpdf:1.3.30'
implementation 'org.xhtmlrenderer:flying-saucer-pdf-openpdf:9.1.22'
```

| 라이브러리 | 버전 | 역할 |
|---|---|---|
| Spring Boot Starter Thymeleaf | 3.4.8 | HTML 템플릿 엔진 |
| **Flying Saucer (XHTML → PDF)** | 9.1.22 | XHTML+CSS 를 PDF로 변환 (W3C CSS 레이아웃) |
| **OpenPDF** | 1.3.30 | 저수준 PDF 엔진 (iText 포크) |
| AWS SDK S3 | 2.29.50 | PDF 업로드 |

---

## 3. 전체 아키텍처

```
┌──────────────────────────────────────────────────┐
│ 도메인 서비스                                      │
│ (예: OutboundService.approve())                  │
│ ├─ 비즈니스 로직 처리                              │
│ └─ ApplicationEventPublisher.publishEvent(       │
│       new InstructionIssueRequested(...))        │
└──────────────────┬───────────────────────────────┘
                   │ Spring 트랜잭션 커밋
                   ▼
┌──────────────────────────────────────────────────┐
│ InstructionIssueEventBridge                       │
│ @TransactionalEventListener(AFTER_COMMIT)         │
│ └─ KafkaTemplate.send("instruction.issued",       │
│        sourceId, message)                         │
└──────────────────┬───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│ InstructionDocumentListener                       │
│ @KafkaListener + @RetryableTopic (3회)            │
│ └─ InstructionDocumentService.issue()             │
└──────────────────┬───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│ InstructionDocumentService.issue()                │
│ 1. version 결정 (이전 버전 + 1)                   │
│ 2. InstructionDocument INSERT (status=GENERATING) │
│ 3. Renderer.loadData() — 도메인 데이터 조회       │
│ 4. Renderer.render() — Thymeleaf → Flying Saucer │
│ 5. SHA-256 비교 — 멱등성 검증                     │
│ 6. S3 업로드                                       │
│ 7. status=READY, s3Key, fileSize, sha256 UPDATE   │
└──────────────────┬───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│ S3 버킷: wbs-instruction-docs                     │
│ {clientId}/{docType}/{yyyy-MM}/{sourceId}_v{n}.pdf│
└──────────────────────────────────────────────────┘
```

---

## 4. 핵심 컴포넌트

### 4.1 도메인 이벤트

📂 `stock/src/main/java/com/beyond/wbs/instruction/event/InstructionIssueRequested.java`

```java
public record InstructionIssueRequested(
    InstructionDocumentType docType,
    UUID    sourceId,
    String  sourceNo,
    UUID    clientId,
    UUID    issuedBy
) {}
```

### 4.2 Spring → Kafka 브리지

📂 `stock/src/main/java/com/beyond/wbs/instruction/event/InstructionIssueEventBridge.java`

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onIssueRequested(InstructionIssueRequested e) {
    InstructionDocumentIssuedEvent message = ...;
    kafkaTemplate.send(
        "instruction.issued",
        e.sourceId().toString(),  // partition key (순서 보장)
        message
    );
}
```

**핵심**: `AFTER_COMMIT` 으로 트랜잭션 롤백 시 Kafka 발행도 안 됨.

### 4.3 Kafka 컨슈머

📂 `stock/src/main/java/com/beyond/wbs/instruction/consumer/InstructionDocumentListener.java`

```java
@RetryableTopic(
    attempts = "${instruction-document.retry.max-attempts:3}",
    backoff = @Backoff(
        delayExpression  = "${instruction-document.retry.initial-interval-ms:500}",
        multiplierExpression = "${instruction-document.retry.multiplier:2.0}"
    ),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".dlq"
)
@KafkaListener(
    topics = "instruction.issued",
    groupId = "instruction-doc-group"
)
public void onMessage(InstructionDocumentIssuedEvent event) {
    instructionDocumentService.issue(event);
}
```

### 4.4 핵심 오케스트레이션

📂 `stock/src/main/java/com/beyond/wbs/instruction/service/InstructionDocumentService.java`

**`issue(InstructionDocumentIssuedEvent event)` 단계**:

```java
1. Renderer 선택
   InstructionDocumentRenderer<?> renderer = renderersByType.get(event.getDocType());

2. version 결정
   int previousVersion = documentRepository.findMaxVersion(clientId, docType, sourceId);
   int nextVersion = previousVersion + 1;

3. GENERATING row INSERT
   InstructionDocument generating = InstructionDocument.builder()
       .docType(event.getDocType())
       .sourceId(event.getSourceId())
       .version(nextVersion)
       .status(InstructionDocumentStatus.GENERATING)
       .build();
   documentRepository.save(generating);

4. 데이터 조회 + 렌더링
   byte[] pdfBytes = renderAndLoad(renderer, event, generating);

5. SHA-256 멱등성 검사 (F7 정책)
   String sha = sha256Hex(pdfBytes);
   if (previousReady.isPresent() && sha.equals(previousReady.get().getSha256())) {
       documentRepository.delete(generating);
       return previousReady.get().getId();  // 새 row 생성 안 함
   }

6. S3 업로드
   UploadResult uploaded = s3Uploader.upload(clientId, docType, sourceId, nextVersion, pdfBytes);
   generating.markReady(uploaded.s3Key(), uploaded.fileSize(), uploaded.sha256());

7. 상태 UPDATE
   documentRepository.save(generating);
```

### 4.5 InstructionDocument 엔티티 (common)

📂 `common/src/main/java/com/beyond/wbs/document/instruction/domain/InstructionDocument.java`

```sql
CREATE TABLE instruction_document (
  id BINARY(16) PRIMARY KEY,
  client_id BINARY(16) NOT NULL,
  doc_type VARCHAR(30) NOT NULL,        -- 9가지 enum
  source_id BINARY(16) NOT NULL,        -- 원본 주문 ID
  source_no VARCHAR(30) NOT NULL,       -- 원본 주문 번호 (SO-00001 등)
  version INT NOT NULL,                 -- 1, 2, 3 ... (재발행 차수)
  s3_key VARCHAR(512),                  -- 성공 시 채워짐
  file_size BIGINT,
  sha256 VARCHAR(64),                   -- 멱등성 검증
  status VARCHAR(20) NOT NULL,          -- GENERATING | READY | FAILED
  issued_by BINARY(16) NOT NULL,
  issued_at DATETIME NOT NULL,
  reissued_from_id BINARY(16),          -- 재발행 시 이전 row 참조
  error_message VARCHAR(1000),
  row_version BIGINT,                   -- @Version (낙관적 락)
  INDEX (client_id, doc_type, source_id, version DESC),
  INDEX (client_id, status)
);
```

**상태 머신**:
- `GENERATING` → 생성 중 (s3Key=null)
- `READY` → 성공 (s3Key, fileSize, sha256 채워짐)
- `FAILED` → 실패 (errorMessage 채워짐, DLQ 라우팅됨)

---

## 5. Renderer 9개 (도메인별 PDF 생성)

📂 `stock/src/main/java/com/beyond/wbs/instruction/render/`

| Renderer | 도메인 | 패키지 |
|---|---|---|
| `OutboundOrderPdfRenderer` | 출고지시서 | `outbound/` |
| `OutboundDispatchPdfRenderer` | 출고전표 | `outbound/` |
| `InboundOrderPdfRenderer` | 입고지시서 | `inbound/` |
| `InboundReceiptPdfRenderer` | 입고전표 | `inbound/` |
| `PlacementOrderPdfRenderer` | 적치지시서 | `inbound/` |
| `PickingListPdfRenderer` | 피킹리스트 | `picking/` |
| `TransferOrderPdfRenderer` | 이동지시서 | `transfer/` |
| `EtcInoutOrderPdfRenderer` | 기타입출고지시서 | `etc/` |
| `StockCountOrderPdfRenderer` | 실사지시서 | `inventory/` |

### 5.1 공통 인터페이스

```java
public interface InstructionDocumentRenderer<DATA> {
    InstructionDocumentType supportedType();
    DATA loadData(UUID sourceId, UUID clientId);
    byte[] render(DATA data, InstructionDocumentRenderContext context);
}
```

→ Spring DI가 모든 구현체를 List로 주입 → docType 키로 Map 빌드 (자동 라우팅).

### 5.2 렌더링 핵심 코드

```java
public byte[] render(OutboundOrderPdfData data, InstructionDocumentRenderContext context) {
    Context tlCtx = new Context();
    tlCtx.setVariable("data", data);
    tlCtx.setVariable("context", context);

    String html = templateEngine.process(supportedType().getTemplatePath(), tlCtx);
    // template = "instruction/outbound-order"

    try (ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)) {
        ITextRenderer renderer = new ITextRenderer();
        fontRegistry.registerInto(renderer.getFontResolver());
        renderer.setDocumentFromString(html);
        renderer.layout();
        renderer.createPDF(out);
        return out.toByteArray();
    }
}
```

### 5.3 한글 폰트 등록

📂 `stock/src/main/java/com/beyond/wbs/instruction/render/InstructionPdfFontRegistry.java`

```java
renderer.addFont(
    "classpath:fonts/NanumGothic-Regular.ttf",
    BaseFont.IDENTITY_H,
    BaseFont.EMBEDDED
);
```

→ 한글 PDF 렌더링용 NanumGothic 임베드.

### 5.4 Best-Effort 다운스트림 호출

```java
String warehouseName = bestEffort(
    () -> masterServiceClient.getWarehouse(order.getWarehouseId(), clientHeader),
    WarehouseResDto::getName,
    "창고"  // 실패 시 fallback "(미조회 창고)"
);
```

→ master/account 호출 실패해도 PDF 생성 계속 (시스템 장애로 PDF 발행 차단되는 것 방지).

---

## 6. Thymeleaf 템플릿

📂 `stock/src/main/resources/templates/instruction/`

```
instruction/
├── outbound-order.html
├── outbound-dispatch.html
├── inbound-order.html
├── inbound-receipt.html
├── placement-order.html
├── picking-list.html
├── transfer-order.html
├── etc-inout-order.html
└── stock-count-order.html
```

→ XHTML 호환 + Flying Saucer 가 인식하는 CSS 사용 (`@page`, `page-break-*` 등).

---

## 7. Kafka 토픽 구성

📂 `stock/src/main/java/com/beyond/wbs/instruction/config/InstructionDocumentTopicConfig.java`

| 토픽 | 파티션 | 복제본 | 용도 |
|---|---|---|---|
| `instruction.issued` | 3 | 1 | 메인 큐 |
| `instruction.issued-retry-0` | 3 | 1 | 1차 재시도 (500ms 백오프) |
| `instruction.issued-retry-1` | 3 | 1 | 2차 재시도 (1000ms) |
| `instruction.issued.dlq` | 1 | 1 | 최종 실패 큐 |

### 7.1 재시도 정책

📂 `stock/src/main/resources/application.yml` (Lines 113-121)

```yaml
instruction-document:
  kafka:
    topic: instruction.issued
    consumer-group: instruction-doc-group
    dlq-topic: instruction.issued.dlq
  retry:
    max-attempts: 3
    initial-interval-ms: 500
    multiplier: 2.0
```

**동작**:
- 1차 실패 → 500ms 후 재시도
- 2차 실패 → 1000ms 후 재시도
- 3차 실패 → 2000ms 후 재시도
- 최종 실패 → DLQ 적재 + InstructionDocument.status = FAILED + errorMessage 기록

---

## 8. S3 업로드

### 8.1 키 구조

📂 `stock/src/main/java/com/beyond/wbs/instruction/s3/InstructionDocumentS3Uploader.java`

```
{clientId}/{docType.code}/{yyyy-MM}/{sourceId}_v{version}.pdf
```

**예시**:
```
01935c00-0000-7000-8000-000000000001/outbound-order/2026-05/d15a1c6e-8c4f-11ec-81d7-0242ac130003_v1.pdf
01935c00-0000-7000-8000-000000000001/inbound-order/2026-05/a1a2a3a4-8c4f-11ec-81d7-0242ac130003_v2.pdf
```

**폴더 구조 이점**:
- `{clientId}` → 회사별 격리 (멀티테넌트)
- `{docType}` → 분류
- `{yyyy-MM}` → 월별 아카이빙·라이프사이클 정책 적용 용이
- `{sourceId}_v{n}` → 같은 source의 모든 발행본 추적

### 8.2 업로드 코드

```java
s3Client.putObject(
    PutObjectRequest.builder()
        .bucket(bucket)               // wbs-instruction-docs
        .key(key)
        .contentType("application/pdf")
        .contentLength((long) pdfBytes.length)
        .build(),
    RequestBody.fromBytes(pdfBytes));
```

### 8.3 설정

📂 `stock/src/main/resources/application.yml` (Lines 80-91)

```yaml
aws:
  region: ap-northeast-2
  s3:
    instruction-bucket: wbs-instruction-docs
    instruction-presign-ttl-seconds: 300
```

---

## 9. 다운로드 / 조회 API

📂 `stock/src/main/java/com/beyond/wbs/instruction/controller/InstructionDocumentController.java`

### 9.1 Presigned URL 발급

```
GET /instruction-documents/{id}/download
Headers: X-Client-Id, X-Presign-Ttl-Seconds (optional, default 300)
```

**응답**:
```json
{
  "url": "https://wbs-instruction-docs.s3.ap-northeast-2.amazonaws.com/...?X-Amz-Expires=300&...",
  "expiresAt": "2026-05-08T14:05:00Z",
  "doc": {
    "id": "...", "docType": "OUTBOUND_ORDER", "sourceNo": "SO-00001",
    "fileSize": 65536, "status": "READY", "version": 1,
    "issuedAt": "...", "issuedByName": "관리자"
  }
}
```

→ 브라우저가 URL로 S3 직접 다운로드 (Gateway/stock 거치지 않음, 서버 대역폭 0).

### 9.2 다조건 검색

```
GET /instruction-documents
  ?docType=OUTBOUND_ORDER
  &sourceId=...
  &sourceNo=SO
  &status=READY
  &issuedFrom=2026-05-01T00:00:00
  &issuedTo=2026-05-31T23:59:59
  &page=0&size=20
```

**정렬 규칙**:
- `sourceId` 명시 → `version DESC` (거래 단위 발행 이력 추적)
- 그 외 → `issuedAt DESC` (전사 최신순)

### 9.3 통계 카드 (사이드바용)

```
GET /instruction-documents/summary
```

**응답**:
```json
{
  "total": 245,
  "byDocType": { "OUTBOUND_ORDER": 80, "INBOUND_ORDER": 60, ... },
  "byStatus":  { "READY": 240, "GENERATING": 0, "FAILED": 5 },
  "last7DaysCount": 42
}
```

---

## 10. 안정성 / 회복성 요약

| 메커니즘 | 효과 |
|---|---|
| `@TransactionalEventListener(AFTER_COMMIT)` | 트랜잭션 롤백 시 Kafka 발행 안 됨 |
| `@RetryableTopic` (3회 + 지수 백오프) | 일시 장애 자동 회복 |
| DLQ (`instruction.issued.dlq`) | 최종 실패 메시지 격리 |
| `@Version` (낙관적 락) | 동시 발행 충돌 방지 |
| SHA-256 비교 | 동일 내용 재발행 방지 |
| Best-effort downstream | 외부 호출 실패가 PDF 발행 차단하지 않음 |
| `status=FAILED` + errorMessage | 운영자 진단 가능 |

---

## 11. 발행 시나리오 예시 (출고지시서)

```
1. UI: "출고지시서 승인" 버튼 클릭
   ↓ POST /outbound/{id}/approve
2. OutboundService.approve()
   - OutboundOrders 상태 draft → approved
   - applicationEventPublisher.publishEvent(InstructionIssueRequested(...))
   - 트랜잭션 커밋
   ↓
3. InstructionIssueEventBridge.onIssueRequested() [AFTER_COMMIT]
   - kafkaTemplate.send("instruction.issued", sourceId, event)
   ↓
4. InstructionDocumentListener.onMessage()
   - instructionDocumentService.issue(event)
   ↓
5. InstructionDocumentService.issue()
   - InstructionDocument INSERT (status=GENERATING, version=1)
   - OutboundOrderPdfRenderer.loadData() → DB 조회
   - OutboundOrderPdfRenderer.render() → Thymeleaf → Flying Saucer → PDF byte[]
   - SHA-256 계산
   - S3 업로드: "{clientId}/outbound-order/2026-05/{sourceId}_v1.pdf"
   - status=READY, s3Key, fileSize, sha256 UPDATE
   ↓
6. UI: 지시서 목록 갱신
   GET /instruction-documents?sourceId=...&docType=OUTBOUND_ORDER
   ↓
7. UI: "다운로드" 클릭
   GET /instruction-documents/{docId}/download
   → presigned URL 반환
   → 브라우저에서 S3 직접 다운로드
```

---

## 12. 새 docType 추가 절차 (확장)

1. **enum 추가** — `InstructionDocumentType.java`
   ```java
   NEW_TYPE("new-code", "instruction/new-type", "새 문서타입")
   ```

2. **Renderer 구현** — `*PdfRenderer.java`
   ```java
   @Component
   public class NewTypePdfRenderer implements InstructionDocumentRenderer<...> {
       public InstructionDocumentType supportedType() { return NEW_TYPE; }
       public X loadData(UUID sourceId, UUID clientId) { ... }
       public byte[] render(X data, ...) { ... }
   }
   ```

3. **Thymeleaf 템플릿** — `resources/templates/instruction/new-type.html`

4. **도메인 서비스에서 이벤트 발행**
   ```java
   applicationEventPublisher.publishEvent(
       new InstructionIssueRequested(NEW_TYPE, sourceId, sourceNo, clientId, userId));
   ```

→ 브리지/컨슈머/서비스는 모두 docType 으로 자동 디스패치하므로 추가 작업 없음.

---

## 13. 핵심 파일 위치 요약

| 카테고리 | 파일 |
|---|---|
| **enum** | `common/.../InstructionDocumentType.java` |
| **엔티티** | `common/.../InstructionDocument.java` |
| **이벤트** | `stock/.../instruction/event/InstructionIssueRequested.java` |
| **Kafka 메시지** | `common/.../kafka/event/InstructionDocumentIssuedEvent.java` |
| **브리지** | `stock/.../instruction/event/InstructionIssueEventBridge.java` |
| **컨슈머** | `stock/.../instruction/consumer/InstructionDocumentListener.java` |
| **오케스트레이션** | `stock/.../instruction/service/InstructionDocumentService.java` |
| **Renderer (9개)** | `stock/.../instruction/render/{domain}/*PdfRenderer.java` |
| **폰트** | `stock/.../instruction/render/InstructionPdfFontRegistry.java` |
| **S3 업로더** | `stock/.../instruction/s3/InstructionDocumentS3Uploader.java` |
| **컨트롤러** | `stock/.../instruction/controller/InstructionDocumentController.java` |
| **토픽 설정** | `stock/.../instruction/config/InstructionDocumentTopicConfig.java` |
| **템플릿 (9개)** | `stock/src/main/resources/templates/instruction/*.html` |

---

## 14. 운영 권장 사항

| 항목 | 현재 (PoC) | 운영 권장 |
|---|---|---|
| Kafka 복제본 | 1 | 3 (Multi-broker MSK) |
| Thymeleaf 캐시 | (개발은 false) | `spring.thymeleaf.cache: true` |
| S3 버킷 | 단일 (instruction + evidence 통합) | 분리 (라이프사이클 정책 차등) |
| 모니터링 | 로그만 | DLQ 메시지 알림, S3 PUT 실패율 |
| Presigned TTL | 300s | 비즈니스 요구에 맞게 조정 |
| 폰트 | NanumGothic 단일 | 회사별 브랜딩 가능 |

---

**문서 끝**
