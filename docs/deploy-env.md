# 운영 환경변수 정리

## 기본 원칙

- 운영 실행 시 `SPRING_PROFILES_ACTIVE=prod`를 반드시 설정한다.
- 실제 비밀번호, JWT secret, AWS key, OpenAI key는 `.env` 또는 서버 secret store에만 둔다.
- `.env.example`은 샘플 파일이다. 실제 값은 넣지 않는다.
- 운영 `application-prod.yml`은 `ddl-auto: validate` 기준이다. 배포 전에 DB 스키마가 준비되어 있어야 한다.

## 필수 공통값

| 변수 | 용도 | 예시 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 운영 프로필 활성화 | `prod` |
| `EUREKA_DEFAULT_ZONE` | Eureka 서버 주소 | `http://eureka:8761/eureka/` |
| `REDIS_HOST` | Redis host | `redis` |
| `REDIS_PORT` | Redis port | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap 서버 | `kafka:9092` |
| `SPRING_ELASTICSEARCH_URIS` | Elasticsearch/OpenSearch 주소 | `http://elasticsearch:9200` |

## Gateway / 인증

| 변수 | 필수 | 용도 |
| --- | --- | --- |
| `GATEWAY_ALLOWED_ORIGINS` | O | 운영 프론트엔드 origin |
| `JWT_SECRET_KEY` | O | Access token 서명 키 |
| `JWT_REFRESH_SECRET_KEY` | O | Refresh token 서명 키 |
| `JWT_EXPIRATION_MINUTES` | 선택 | Access token 만료 분 |
| `JWT_REFRESH_EXPIRATION_MINUTES` | 선택 | Refresh token 만료 분 |

## DB

| 서비스 | 필수 변수 |
| --- | --- |
| account | `ACCOUNT_DB_URL`, `ACCOUNT_DB_USERNAME`, `ACCOUNT_DB_PASSWORD` |
| master | `MASTER_DB_URL`, `MASTER_DB_USERNAME`, `MASTER_DB_PASSWORD` |
| stock | `STOCK_DB_URL`, `STOCK_DB_USERNAME`, `STOCK_DB_PASSWORD` |
| common | `COMMON_DB_URL`, `COMMON_DB_USERNAME`, `COMMON_DB_PASSWORD` |
| ai-service | `AI_DB_URL`, `AI_DB_USERNAME`, `AI_DB_PASSWORD` |
| ai-service readonly | `WMS_READONLY_DB_URL`, `WMS_READONLY_DB_USERNAME`, `WMS_READONLY_DB_PASSWORD` |

## AWS / S3

| 변수 | 필수 | 용도 |
| --- | --- | --- |
| `AWS_ACCESS_KEY_ID` | O | S3 접근 키 |
| `AWS_SECRET_ACCESS_KEY` | O | S3 secret |
| `AWS_REGION` | 선택 | 기본값 `ap-northeast-2` |
| `AWS_S3_PROFILE_BUCKET` | O | 프로필 이미지 버킷 |
| `AWS_S3_INSTRUCTION_BUCKET` | O | 지시서 PDF 버킷 |
| `AWS_S3_DEFECT_EVIDENCE_BUCKET` | 선택 | 불량 증빙 버킷, 기본은 지시서 버킷 |

## Stock 메일

| 변수 | 필수 | 용도 |
| --- | --- | --- |
| `MAIL_USERNAME` | O | SMTP 계정 |
| `MAIL_PASSWORD` | O | SMTP 비밀번호 또는 앱 비밀번호 |
| `INBOUND_REQUEST_MAIL_TO` | O | 기타출고 입고요청 수신자 |
| `INBOUND_REQUEST_MAIL_FROM` | 선택 | 미설정 시 `MAIL_USERNAME` 사용 |
| `MAIL_HOST`, `MAIL_PORT` | 선택 | 기본값 Gmail SMTP |

## AI / OpenAI / pgvector

| 변수 | 필수 | 용도 |
| --- | --- | --- |
| `OPENAI_API_KEY` | O | OpenAI API key |
| `OPENAI_CHAT_MODEL` | 선택 | 기본값 `gpt-4o-mini` |
| `OPENAI_EMBED_MODEL` | 선택 | 기본값 `text-embedding-3-small` |
| `PGVECTOR_INITIALIZE_SCHEMA` | 선택 | 운영 기본값 `false` |

운영에서 `PGVECTOR_INITIALIZE_SCHEMA=false`를 유지하려면 `vector_store` 테이블과 pgvector extension이 먼저 준비되어 있어야 한다.

## 기동 전 체크

1. `.env.example`을 복사해 실제 `.env`를 만든다.
2. `change-me` 값을 모두 실제 운영값으로 교체한다.
3. `SPRING_PROFILES_ACTIVE=prod`가 들어갔는지 확인한다.
4. 운영 DB 스키마가 준비되어 있는지 확인한다.
5. `JWT_SECRET_KEY`, `JWT_REFRESH_SECRET_KEY`, `OPENAI_API_KEY`, `AWS_SECRET_ACCESS_KEY`, `MAIL_PASSWORD`가 로그나 커밋에 남지 않게 관리한다.
