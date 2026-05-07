# 운영 환경변수 정리

## 기본 원칙

- 운영 실행 시 `SPRING_PROFILES_ACTIVE=prod`를 반드시 설정한다.
- 실제 비밀번호, JWT secret, AWS key는 Kubernetes Secret에만 둔다.
- Kubernetes 배포에서는 공개 설정값을 각 서비스 `k8s/depl_svc.yml`에 직접 둔다.
- 운영 `application-prod.yml`은 `ddl-auto: validate` 기준이다. 배포 전에 DB 스키마가 준비되어 있어야 한다.

## 공개 설정값

아래 값들은 Secret이 아니므로 각 서비스 Deployment의 `env.value`에 직접 넣는다.

| 변수 | 용도 | 예시 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 운영 프로필 활성화 | `prod` |
| `REDIS_HOST` | Redis host | `redis` |
| `REDIS_PORT` | Redis port | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap 서버 | `kafka:9092` |
| `SPRING_ELASTICSEARCH_URIS` | Elasticsearch/OpenSearch 주소 | `http://elasticsearch:9200` |

운영 Kubernetes 배포에서는 Eureka를 사용하지 않는다. 각 서비스는 Kubernetes Service DNS로 통신한다.

## Gateway / 인증

| 변수 | 필수 | 용도 |
| --- | --- | --- |
| `GATEWAY_ALLOWED_ORIGINS` | O | 운영 프론트엔드 origin |
| `JWT_SECRET_KEY` | O | Access token 서명 키 |
| `JWT_REFRESH_SECRET_KEY` | O | Refresh token 서명 키 |
| `JWT_EXPIRATION_MINUTES` | 선택 | Access token 만료 분 |
| `JWT_REFRESH_EXPIRATION_MINUTES` | 선택 | Refresh token 만료 분 |

Gateway 라우팅은 Eureka `lb://`가 아니라 Kubernetes Service DNS를 사용한다.

| 변수 | 필수 | 기본값 |
| --- | --- | --- |
| `ACCOUNT_SERVICE_URI` | 선택 | `http://account-service` |
| `MASTER_SERVICE_URI` | 선택 | `http://master-service` |
| `STOCK_SERVICE_URI` | 선택 | `http://stock-service` |
| `SEARCH_SERVICE_URI` | 선택 | `http://search-service` |

내부 Feign 호출도 운영에서는 Kubernetes Service DNS를 사용한다.

| 변수 | 필수 | 기본값 |
| --- | --- | --- |
| `ACCOUNT_SERVICE_URL` | 선택 | `http://account-service` |
| `MASTER_SERVICE_URL` | 선택 | `http://master-service` |
| `STOCK_SERVICE_URL` | 선택 | `http://stock-service` |

## DB

| 서비스 | 필수 변수 |
| --- | --- |
| account | `ACCOUNT_DB_URL`, `ACCOUNT_DB_USERNAME`, `ACCOUNT_DB_PASSWORD` |
| master | `MASTER_DB_URL`, `MASTER_DB_USERNAME`, `MASTER_DB_PASSWORD` |
| stock | `STOCK_DB_URL`, `STOCK_DB_USERNAME`, `STOCK_DB_PASSWORD` |

이번 1차 배포에서는 `ai-service`를 제외한다. 따라서 AI용 PostgreSQL, pgvector, read-only MySQL 계정, OpenAI API key는 아직 Kubernetes 공통 설정에 넣지 않는다.

## Kubernetes Secret

실제 비밀값은 파일로 만들지 않고 아래 명령으로 클러스터에 직접 생성한다.

```bash
kubectl create secret generic wbs-secrets \
  -n wbs-ns \
  --from-literal=ACCOUNT_DB_PASSWORD='실제값' \
  --from-literal=MASTER_DB_PASSWORD='실제값' \
  --from-literal=STOCK_DB_PASSWORD='실제값' \
  --from-literal=JWT_SECRET_KEY='실제값' \
  --from-literal=JWT_REFRESH_SECRET_KEY='실제값' \
  --from-literal=AWS_ACCESS_KEY_ID='실제값' \
  --from-literal=AWS_SECRET_ACCESS_KEY='실제값' \
  --from-literal=MAIL_USERNAME='실제값' \
  --from-literal=MAIL_PASSWORD='실제값'
```

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

## 기동 전 체크

1. `.env.example`을 복사해 실제 `.env`를 만든다.
2. `change-me` 값을 모두 실제 운영값으로 교체한다.
3. `SPRING_PROFILES_ACTIVE=prod`가 들어갔는지 확인한다.
4. 운영 DB 스키마가 준비되어 있는지 확인한다.
5. `JWT_SECRET_KEY`, `JWT_REFRESH_SECRET_KEY`, `AWS_SECRET_ACCESS_KEY`, `MAIL_PASSWORD`가 로그나 커밋에 남지 않게 관리한다.
