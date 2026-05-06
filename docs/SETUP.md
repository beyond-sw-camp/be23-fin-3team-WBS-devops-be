# 개발 환경 세팅 & AI 기능 가이드

메인 README 가 "프로젝트 소개·팀 정보"라면, 이 문서는 **"코드를 처음 받은 사람이 바로 돌릴 수 있게"** 하는 실전 가이드다.

---

## 🏗 전체 아키텍처

```
┌──────────────────────────────────────────────────────────┐
│                  React Frontend (별개 repo)               │
└──────────────────────┬───────────────────────────────────┘
                       │
              ┌────────▼─────────┐
              │  API Gateway     │  :8080
              │  (Spring Cloud)  │
              └────────┬─────────┘
                       │
      ┌────────────────┼────────────────┬─────────────────┐
      ▼                ▼                ▼                 ▼
  account-svc     master-svc       stock-svc         ai-service
  (유저·권한)    (창고·제품)      (재고·주문)        (AI 기능)
      │                │                │                 │
      └────────────────┴────────────────┘                 │
                       │                                  │
              ┌────────▼─────────┐              ┌─────────▼─────────┐
              │  MariaDB :3306   │              │  Postgres :5433   │
              │  account_db      │              │  + pgvector       │
              │  master_db       │              │  ai_db            │
              │  stock_db        │              └─────────┬─────────┘
              └──────────────────┘                        │
                                                ┌────────▼────────┐
                                                │  Ollama :11434  │
                                                │  gemma4:e4b     │
                                                │  bge-m3         │
                                                └─────────────────┘
```

---

## ⚙️ 사전 요구사항

| 도구 | 버전 | 용도 |
|---|---|---|
| JDK | **17** (Amazon Corretto 권장) | 전 서비스 공통 |
| Docker Desktop | 최신 | ai-postgres / Kafka / Elasticsearch 컨테이너 |
| MariaDB | 10+ | 로컬 설치. `root` / `test1234`, 포트 3306 |
| Ollama | 0.21+ | `brew install ollama` |
| RAM | **최소 16GB**, 32GB 이상 권장 | gemma4:e4b + Postgres + JVM 여럿 동시 구동 |
| 디스크 여유 | 30GB+ | 모델 파일 (bge-m3 1GB + gemma4:e4b 10GB + gemma4:26b 17GB 선택) |

---

## 🚀 기동 순서

### 1. 인프라 컨테이너 올리기
```bash
docker compose up -d ai-postgres
# 필요 시: kafka, elasticsearch, kibana 등도
docker compose up -d
```

### 2. MySQL readonly 계정 (최초 1회) — Text-to-SQL 전용
```bash
mysql -uroot -ptest1234 <<'EOF'
CREATE USER IF NOT EXISTS 'ai_readonly'@'localhost' IDENTIFIED BY 'readonly_pass';
GRANT SELECT ON stock_db.*  TO 'ai_readonly'@'localhost';
GRANT SELECT ON master_db.* TO 'ai_readonly'@'localhost';
FLUSH PRIVILEGES;
EOF
```

### 3. Ollama 모델 pull (최초 1회, ~11GB)
```bash
brew services start ollama       # 백그라운드 기동 + 로그인 시 자동 시작
ollama pull bge-m3               # 임베딩 (1.2GB)
ollama pull gemma4:e4b           # 챗 생성 모델 (9.6GB)
# (선택) ollama pull gemma4:26b  # 발표용 고성능 (17GB)
```

### 4. common 라이브러리 Maven Local 배포 (최초 1회)
```bash
./install-common.sh
```

### 5. 서비스 기동 순서 (각 IDE 에서 실행 버튼 또는 CLI)
```bash
# 반드시 Eureka → Gateway → 나머지 순서
(cd eureka     && ./gradlew bootRun)   # :8761
(cd apigateway && ./gradlew bootRun)   # :8080
(cd account    && ./gradlew bootRun)   # random
(cd master     && ./gradlew bootRun)
(cd stock      && ./gradlew bootRun)
(cd ai-service && ./gradlew bootRun)
```

**확인**: http://localhost:8761 대시보드에서 **AI-SERVICE / ACCOUNT-SERVICE / MASTER-SERVICE / STOCK-SERVICE / API-GATEWAY** 5개가 UP 이면 성공.

---

## 🤖 AI 기능 호출 예시

### 1) RAG 운영 가이드 (문서 기반 QA)

```bash
# 문서 인덱싱 (샘플 SOP 사용)
curl -X POST http://localhost:8080/ai-service/rag/ingest \
  -H "Content-Type: application/json" \
  -d "$(python3 -c "
import json
c = open('ai-service/src/main/resources/samples/wms-sop-sample.md').read()
print(json.dumps({
    'content': c,
    'metadata': {'source':'wms-sop-sample.md','category':'SOP','language':'ko'}
}, ensure_ascii=False))
")"

# 질의 (SSE 스트리밍)
curl -N -G "http://localhost:8080/ai-service/rag/chat/stream" \
  --data-urlencode "q=지게차 운행 시 주의사항이 뭐야?"
# → "지게차는 인증 자격자만 조작... (출처: ... > 4.1 지게차 운행)"

# 카테고리 필터
curl -N -G "http://localhost:8080/ai-service/rag/chat/stream" \
  --data-urlencode "q=PDA 꺼지면 어떻게 해?" \
  --data-urlencode "category=FAQ"
```

### 2) 지능형 재고 분석 (Text-to-SQL)

```bash
curl -X POST http://localhost:8080/ai-service/sql/analyze \
  -H "Content-Type: application/json" \
  -d '{"question": "창고별 재고 총합(total_qty) 내림차순 상위 5개"}'

# 응답 예시
# {
#   "question": "창고별 재고 총합...",
#   "generatedSql": "SELECT warehouse_id, SUM(total_qty) ... LIMIT 5",
#   "rows": [...],
#   "executionTimeMs": 23
# }
```

**보안 3중 방어**:
1. MySQL 레벨: `ai_readonly` 계정이 SELECT 권한만 보유
2. Connection 레벨: HikariCP `read-only: true`
3. SQL 검증: 키워드 차단 (`INSERT/UPDATE/DELETE/DROP/...`) + 허용 테이블 화이트리스트 + `LIMIT 100` 강제

### 3) 작업자 자동 배정

```bash
curl -X POST http://localhost:8080/ai-service/assign \
  -H "Content-Type: application/json" \
  -d '{"targetZoneCode": "A", "taskType": "PICKING"}'

# 응답 예시
# {
#   "recommended": {"userId": "...", "lastZoneCode": "A", "activeTaskCount": 0},
#   "score": 1.000,
#   "reasoning": "같은 Zone A 에 마지막 위치 · 현재 진행 작업 0건 (점수 1.00)",
#   "candidates": [...]  // 전체 점수 분해 (투명성)
# }
```

**스코어 공식**: `0.7 × proximity + 0.3 × load`
- proximity: 같은 zone = 1.0, 다른 zone = 0.3
- load: `1 - min(activeTaskCount / 5.0, 1.0)`

---

## 🧪 트러블슈팅 노트 (실제로 겪은 함정)

### pgvector 카테고리 필터가 0 결과 반환
Spring AI 1.0 `PgVectorStore` 는 기본 `metadata` 컬럼을 **`json`** 으로 만드는데, 필터는 `jsonb` 연산자(`@@`, `@>`) 를 사용함 → 타입 불일치로 결과 없음.

```sql
ALTER TABLE vector_store ALTER COLUMN metadata TYPE jsonb USING metadata::jsonb;
```
JDBC 연결 풀이 컬럼 타입을 **캐시**하므로 **ai-service 재기동 필수**.

### WebFlux 에서 Feign 주입이 `HttpMessageConverters` 로 실패
WebFlux 만 있는 프로젝트에서는 Spring Boot 가 해당 빈을 자동 생성하지 않음. Feign 이 요구해서 부팅 실패.
→ `ai-service/config/FeignMessageConvertersConfig.java` 로 수동 등록 (Jackson + String + ByteArray 3개 최소 세트).

### Gateway `StripPrefix` 가 JWT 필터보다 먼저 실행
`/ai-service/**` 를 공개 경로로 뚫으려 했지만, 필터 도달 시점엔 이미 `/ai-service` 가 제거된 상태였음 (`/ping` 만 보임).
→ `JwtAuthFilter` 에 `@Order(Ordered.HIGHEST_PRECEDENCE)` 로 최우선 실행하게 변경.

### common 라이브러리가 Tomcat 을 물고 와서 WebFlux 와 충돌
common 이 `spring-boot-starter-web` 을 가져오면서 ai-service 가 Netty 대신 Tomcat 으로 뜸 → SSE 가 깨짐.
→ ai-service `build.gradle` 에서 해당 의존성 exclude:
```gradle
implementation('com.beyond:common:0.0.1-SNAPSHOT') {
    exclude group: 'org.springframework.boot', module: 'spring-boot-starter-web'
}
```

### common 의 RedisConfig 가 `${spring.redis.host}` 요구
ai-service 는 Redis 를 안 쓰지만 common 이 `RedisConfig` 를 물고 와서 부팅 실패.
→ `application.yml` 에 더미 `spring.redis.host/port` 추가 (실제로는 접속 시도 안 함).

### 두 번째 DataSource 가 PgVectorStore 에 잘못 주입됨
MySQL readonly 를 `@Bean` 으로 추가했더니 Spring Boot 자동설정의 `PgVectorStore` 가 이걸 primary 로 잘못 주입받아 `CREATE EXTENSION vector` 시도 → read-only 거부.
→ `@Bean(name = "readonlyDataSource", defaultCandidate = false)` 로 **autowire 후보에서 제외**, 명시적 `@Qualifier` 요청 시만 주입.

### HikariDataSource 바인딩 시 `jdbcUrl` vs `url`
`@ConfigurationProperties` 가 `HikariDataSource` 에 직접 바인딩될 때는 **`jdbc-url`** 이어야 함 (Hikari 의 setter 이름). 일반 `DataSource` 빌더는 `url` 로 충분.

---

## 📁 모듈 구조 (Phase 1~6 기준)

```
be23-fin-3team-WBS-be/
├── eureka/                 # 서비스 디스커버리 (:8761)
├── apigateway/             # API Gateway (:8080) — JwtAuthFilter, 라우트
├── common/                 # 공통 라이브러리 (Maven Local 배포)
├── account/                # 유저·권한·JWT 발급
├── master/                 # 창고·구역·랙·상품 기준정보
├── stock/                  # 입고·출고·재고·작업자 위치
│   └── assignment/             # [Phase 6] 작업자 위치 엔티티·API
├── ai-service/             # [Phase 1~6 신규] AI 기능 전용
│   ├── rag/                    # Phase 4 : RAG 챗봇
│   ├── sql/                    # Phase 5 : Text-to-SQL
│   ├── assignment/             # Phase 6 : 자동 배정 (규칙 기반)
│   └── config/                 # ReadonlyDataSource, FeignMessageConverters 등
└── docker-compose.yml      # ai-postgres, kafka, es, kibana
```

---

## 📊 AI 기능 요약 표 (발표용)

| 기능 | 목적 | 기술 스택 | 차별화 포인트 |
|---|---|---|---|
| **RAG 챗봇** | SOP·매뉴얼 자연어 조회 | pgvector HNSW + bge-m3 + gemma4 + Spring AI `QuestionAnswerAdvisor` | 섹션 인식 청킹 + 출처 인용 + 카테고리 필터 + 환각 차단 |
| **Text-to-SQL** | 실시간 재고 분석 | Gemma4 + MySQL readonly + JdbcTemplate | 3중 보안 (DB권한·연결·검증), 생성 SQL 투명 공개 |
| **작업자 자동 배정** | 근접·부하 고려 추천 | 규칙 기반 가중합 + Feign 서비스 간 호출 | 점수 분해 공개 (투명성), LLM 배제로 고속 응답 |

---

## 🔐 보안 · 운영 메모

- `application.yml` 은 `.gitignore` 에 등록되어 있음. 시크릿은 환경변수 또는 별도 secrets store 사용.
- AWS 액세스 키 등 민감 정보는 **절대 커밋 금지**. 커밋됐다면 즉시 해당 키 폐기.
- ai_readonly MySQL 계정: **SELECT 권한만**, Text-to-SQL 전용. 주기적으로 쿼리 로그 감시.
- Gateway JwtAuthFilter 의 `PUBLIC_PREFIX = /ai-service` 는 **개발 단계 전용**. 운영 배포 전 JWT 토큰 기반 인증으로 전환할 것.
