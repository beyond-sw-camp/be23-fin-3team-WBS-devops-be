# AWS S3 통합 기술 명세서

> **SDK 버전**: AWS SDK for Java v2 — `software.amazon.awssdk:s3:2.29.50`
> **리전**: `ap-northeast-2` (서울)
> **작성일**: 2026-05-08

---

## 1. 개요

본 시스템은 두 종류의 콘텐츠를 S3에 저장한다:

| 콘텐츠 | 모듈 | 발행 트리거 |
|---|---|---|
| **9종 지시서 PDF** | stock | 도메인 이벤트 (입고/출고 등 승인 시) |
| **불량 증빙 사진** | common (uploader) + stock (controller) | 입고 검수 시 클라이언트 multipart 업로드 |

**모든 다운로드는 Presigned URL 방식** — 버킷은 비공개(Private)이고, 시간 제한(TTL 300초)을 둔 서명 URL로 접근.

---

## 2. 의존성

### 2.1 SDK 의존성 (3개 모듈)

| 모듈 | 위치 | 용도 |
|---|---|---|
| common | [common/build.gradle:42](../common/build.gradle) | S3Client/S3Presigner 빈 등록, 불량 증빙 업로더 |
| master | [master/build.gradle:48](../master/build.gradle) | (확장 대비, 현재는 미사용) |
| stock | [stock/build.gradle:48](../stock/build.gradle) | 지시서 PDF 업로더, 불량 증빙 컨트롤러 |

```gradle
implementation 'software.amazon.awssdk:s3:2.29.50'
```

---

## 3. S3 클라이언트 설정

📂 `common/src/main/java/com/beyond/wbs/s3/AwsS3Config.java`

```java
@Configuration
@ConditionalOnProperty(name = "aws.credentials.access-key")
public class AwsS3Config {
    @Value("${aws.credentials.access-key}") private String accessKey;
    @Value("${aws.credentials.secret-key}") private String secretKey;
    @Value("${aws.region}")                 private String region;

    @Bean
    public S3Client client() {
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(creds))
            .build();
    }

    @Bean
    public S3Presigner s3Presigner() { /* ... */ }
}
```

**핵심**:
- `@ConditionalOnProperty` → AWS 자격증명이 있는 환경에서만 빈 등록
- 두 개의 빈(`S3Client`, `S3Presigner`)을 모든 모듈이 공유
- 키 기반 정적 자격증명 (운영 진입 시 IAM Role / IRSA 권장)

---

## 4. 환경 설정 (application.yml)

### 4.1 Common 모듈

📂 `common/src/main/resources/application.yml` (Lines 35-41)

```yaml
aws:
  region: ap-northeast-2
  s3:
    bucket1: littleniddle-board-profile-image  # 프로필 이미지용 (현재 미사용)
```

### 4.2 Stock 모듈

📂 `stock/src/main/resources/application.yml` (Lines 80-91)

```yaml
aws:
  region: ap-northeast-2
  s3:
    instruction-bucket: wbs-instruction-docs
    instruction-presign-ttl-seconds: 300
    defect-evidence-bucket: wbs-instruction-docs   # 1차 PoC: 같은 버킷 사용
    defect-evidence-download-ttl-seconds: 300
```

---

## 5. 버킷 / 키 구조

### 5.1 사용 버킷

| 버킷 | 용도 | 모듈 |
|---|---|---|
| `wbs-instruction-docs` | 지시서 PDF + 불량 증빙 (1차 PoC 통합) | stock |
| `littleniddle-board-profile-image` | 프로필 이미지 (코드 미구현) | common, master |

### 5.2 지시서 PDF 키 패턴

```
{clientId}/{docType.code}/{yyyy-MM}/{sourceId}_v{version}.pdf
```

**예시**:
```
01935c00-0000-7000-8000-000000000001/outbound-order/2026-05/d15a1c6e_v1.pdf
01935c00-0000-7000-8000-000000000001/inbound-order/2026-05/a1a2a3a4_v2.pdf
01935c00-0000-7000-8000-000000000001/picking-list/2026-05/b2b3b4b5_v1.pdf
```

**구조 의미**:
- `{clientId}` — 회사별 폴더 (멀티테넌트 격리)
- `{docType}` — 9종 분류 (outbound-order, inbound-order, ...)
- `{yyyy-MM}` — 월 단위 (라이프사이클 정책 적용 용이)
- `{sourceId}_v{n}` — 같은 source의 모든 발행본 추적

### 5.3 불량 증빙 사진 키 패턴

```
defect-evidence/{clientId}/{sourceType}/{yyyy-MM}/{sourceId}/{evidenceId}.{ext}
```

**예시**:
```
defect-evidence/01935c00-.../inbound_order_item/2026-05/a1a2a3a4-.../b1b2b3b4-....jpg
```

| 부분 | 값 예 |
|---|---|
| `sourceType` | `inbound_order_item` (소스 도메인 종류) |
| `sourceId` | 입고 아이템 ID |
| `evidenceId` | DefectEvidence row PK |
| `ext` | `jpg` / `png` / `webp` / `heic` |

---

## 6. 지시서 PDF 업로드 흐름

📂 `stock/src/main/java/com/beyond/wbs/instruction/s3/InstructionDocumentS3Uploader.java`

### 6.1 업로드 메서드

```java
public UploadResult upload(UUID clientId, InstructionDocumentType docType,
                           UUID sourceId, int version, byte[] pdfBytes) {
    String key = buildKey(clientId, docType, sourceId, version);
    String sha256 = sha256Hex(pdfBytes);
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType("application/pdf")
            .contentLength((long) pdfBytes.length)
            .build(),
        RequestBody.fromBytes(pdfBytes));
    return new UploadResult(key, pdfBytes.length, sha256);
}
```

### 6.2 호출 흐름

```
도메인 승인 → Kafka 이벤트 → InstructionDocumentListener
    → InstructionDocumentService.issue()
        → Renderer.render() → byte[]
        → InstructionDocumentS3Uploader.upload()
        → DB UPDATE (status=READY, s3Key, fileSize, sha256)
```

자세한 흐름은 [PDF_SYSTEM_SPEC.md](PDF_SYSTEM_SPEC.md) 참조.

### 6.3 Presigned 다운로드 URL

```java
public String presignDownloadUrl(String s3Key) {
    GetObjectRequest get = GetObjectRequest.builder()
        .bucket(bucket).key(s3Key).build();
    GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofSeconds(300))   // 기본 5분
        .getObjectRequest(get).build();
    return s3Presigner.presignGetObject(presign).url().toString();
}
```

**API**: `GET /instruction-documents/{id}/download`
**응답**:
```json
{
  "url": "https://wbs-instruction-docs.s3.ap-northeast-2.amazonaws.com/...?X-Amz-Signature=...",
  "expiresAt": "2026-05-08T14:05:00Z",
  "doc": { ... }
}
```

→ 브라우저가 URL로 직접 S3에서 다운로드. 서버 대역폭 0.

---

## 7. 불량 증빙 사진 업로드

📂 `common/src/main/java/com/beyond/wbs/evidence/defect/s3/DefectEvidenceS3Uploader.java`
📂 `stock/src/main/java/com/beyond/wbs/evidence/defect/controller/DefectEvidenceController.java`
📂 `stock/src/main/java/com/beyond/wbs/evidence/defect/service/DefectEvidenceService.java`

### 7.1 업로드 흐름 (서버 경유)

```
[클라이언트]
  POST /defect-evidence (multipart/form-data)
  ├─ file: 사진 바이너리
  ├─ sourceType: "inbound_order_item"
  └─ sourceId: UUID
        ↓
[DefectEvidenceController.upload()]
        ↓
[DefectEvidenceService.upload()]
  1. MIME 검증 (image/jpeg|png|webp|heic|heif)
  2. 파일 크기 검증 (≤ 5MB)
  3. source 당 첨부 장수 검증 (≤ 5장)
  4. S3 키 생성: defect-evidence/{clientId}/{sourceType}/{yyyy-MM}/{sourceId}/{evidenceId}.{ext}
  5. s3Uploader.uploadBytes() → S3 PUT
  6. DefectEvidence row INSERT (status=READY, s3Key, fileSize, sha256)
        ↓
[응답] { id, s3Key, fileSize, ... }
```

### 7.2 검증 규칙 (Stock application.yml)

```yaml
defect-evidence:
  max-file-size-bytes: 5242880   # 5MB
  max-per-source: 5              # source당 최대 5장
  allowed-mime-types:
    - image/jpeg
    - image/png
    - image/webp
    - image/heic
    - image/heif

spring:
  servlet:
    multipart:
      max-file-size: 6MB         # 검증보다 살짝 크게
      max-request-size: 6MB
```

### 7.3 업로더 메서드

| 메서드 | 용도 |
|---|---|
| `uploadBytes(s3Key, contentType, bytes)` | PUT 업로드 |
| `presignDownload(s3Key)` | 다운로드 URL 발급 (TTL 300s) |
| `headOrNull(s3Key)` | 객체 존재·메타 검증 (HEAD) |
| `delete(s3Key)` | 삭제 |

### 7.4 다운로드 API

```
GET /defect-evidence/{id}/download
Headers: X-Client-Id
```

**응답**:
```json
{
  "url": "https://wbs-instruction-docs.s3.../defect-evidence/...?X-Amz-Expires=300&...",
  "expiresAt": "2026-05-08T14:05:00Z",
  "doc": { "s3Key": "defect-evidence/...", "fileSize": 2048000, "sha256": "..." }
}
```

---

## 8. 멀티테넌시 격리

| 계층 | 격리 메커니즘 |
|---|---|
| 헤더 검증 | 모든 API에 `X-Client-Id` 필수 |
| DB 쿼리 | `WHERE client_id = ?` 강제 (모든 InstructionDocument/DefectEvidence 조회) |
| S3 키 | 항상 `{clientId}` 폴더 prefix |
| Presigned URL | DB row 조회 시 clientId 검증된 후 발급 |

**예시**: 회사 A 사용자가 회사 B의 `defect-evidence/B-clientId/...` 의 presigned URL을 받기는 불가능 — DB 단계에서 차단.

---

## 9. 보안

### 9.1 IAM 정책 (추정)

코드에서 사용하는 S3 액션:

| Action | 호출 위치 |
|---|---|
| `s3:PutObject` | upload (PDF, 사진) |
| `s3:GetObject` | presigned URL의 다운로드 권한 |
| `s3:HeadObject` | `headOrNull()` 무결성 검증 |
| `s3:DeleteObject` | `delete()` 불량 증빙 삭제 |

**권장 IAM 정책 (애플리케이션 IAM User)**:
```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": [
      "s3:PutObject", "s3:GetObject", "s3:HeadObject", "s3:DeleteObject"
    ],
    "Resource": [
      "arn:aws:s3:::wbs-instruction-docs/*",
      "arn:aws:s3:::littleniddle-board-profile-image/*"
    ]
  }]
}
```

### 9.2 버킷 공개/비공개

→ **Private 버킷** (블록 퍼블릭 액세스 활성화 권장)

근거:
- 모든 다운로드는 presigned URL 방식
- 직접 HTTP GET 미지원
- AWS 자격증명 기반 접근만 허용

### 9.3 자격증명 관리

| 단계 | 방식 |
|---|---|
| 현재 (PoC) | application.yml 평문 access/secret key |
| 권장 (개발) | 환경 변수 (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`) |
| 권장 (운영) | **IAM Role** (EC2/ECS) 또는 **AWS Secrets Manager** |

**Secrets Manager 마이그레이션 예**:
```yaml
spring.cloud.aws.secretsmanager:
  region: ap-northeast-2
  prefix: /wbs/${spring.profiles.active}/aws
  failFast: true
```

---

## 10. 에러 처리

### 10.1 지시서 PDF

| 시점 | 예외 | 처리 |
|---|---|---|
| 렌더링 실패 | `InstructionRenderException` | InstructionDocument.markFailed(msg), Kafka 재시도 |
| S3 PUT 실패 | `InstructionUploadException` | 동일 처리 |
| 최종 실패 | (3회 재시도 후) | DLQ `instruction.issued.dlq` 로 라우팅 |

```java
catch (InstructionRenderException | InstructionUploadException e) {
    generating.markFailed(e.getMessage());
    documentRepository.save(generating);
    throw e;  // Kafka 재시도 트리거
}
```

### 10.2 불량 증빙 사진

| 시점 | 예외 | 처리 |
|---|---|---|
| MIME/size/장수 검증 실패 | `IllegalArgumentException` | 400 Bad Request (DB row 미생성) |
| S3 PUT 실패 | `SdkClientException` | 트랜잭션 롤백 (DB row 미생성) |
| S3 DELETE 실패 | (warn 로그만) | DB row 는 삭제 → 고아 객체 가능 (향후 cron 정리) |

---

## 11. 멱등성 (지시서 PDF만)

PDF 발행은 SHA-256 비교로 중복 발행 방지:

```java
String sha = sha256Hex(pdfBytes);
if (previousReady.isPresent() && sha.equals(previousReady.get().getSha256())) {
    // 직전 READY와 바이트 동일 → 새 row 생성 안 하고 기존 row 반환
    documentRepository.delete(generating);
    return previousReady.get().getId();
}
```

**효과**:
- 동일 source 재발행 시도 → 내용 변경 없음 → S3 비용·DB row 절약
- 클라이언트가 재시도해도 안전

---

## 12. 핵심 파일 위치

| 카테고리 | 파일 |
|---|---|
| 설정 | `common/.../s3/AwsS3Config.java` |
| 지시서 PDF 업로더 | `stock/.../instruction/s3/InstructionDocumentS3Uploader.java` |
| 불량 증빙 업로더 | `common/.../evidence/defect/s3/DefectEvidenceS3Uploader.java` |
| 지시서 서비스 | `stock/.../instruction/service/InstructionDocumentService.java` |
| 불량 증빙 서비스 | `stock/.../evidence/defect/service/DefectEvidenceService.java` |
| 지시서 컨트롤러 | `stock/.../instruction/controller/InstructionDocumentController.java` |
| 불량 증빙 컨트롤러 | `stock/.../evidence/defect/controller/DefectEvidenceController.java` |
| Common application.yml | `common/src/main/resources/application.yml` (Lines 35-41) |
| Stock application.yml | `stock/src/main/resources/application.yml` (Lines 80-102) |

---

## 13. 운영 체크리스트

| 항목 | 현재 | 운영 권장 |
|---|---|---|
| 자격증명 | 평문 yml | Secrets Manager / IAM Role |
| 버킷 | 1개 통합 | 분리 (지시서 vs 증빙) |
| 라이프사이클 정책 | 없음 | 90일 후 IA, 1년 후 Glacier |
| 버전 관리 | (S3 자체 미적용) | 활성화 권장 |
| 암호화 | (기본) | SSE-S3 또는 SSE-KMS |
| 액세스 로그 | 없음 | 별도 버킷에 기록 |
| Cross-Region Replication | 없음 | 재해복구용 활성화 검토 |
| Block Public Access | (확인 필요) | 강제 활성화 |
| Presigned TTL | 300s | 비즈니스 요구에 맞게 |

---

## 14. 향후 확장 아이디어

| 기능 | 비고 |
|---|---|
| 클라이언트 직접 업로드 (presigned PUT) | 서버 대역폭 0, 큰 파일 효율적 |
| Multipart Upload | 대용량 파일 (100MB+) |
| CloudFront CDN 전면 | 글로벌 다운로드 가속 |
| S3 Object Lock | 법적 보관 의무 (감사 등) |
| Macie 통합 | 민감정보 자동 탐지 |

---

**문서 끝**
