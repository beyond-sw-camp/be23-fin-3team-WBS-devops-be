# 시드 데이터 UUID 관리 가이드

`data.sql` 에 새 레코드 추가할 때 **UUID 중복 / 비-hex 문자**로 앱이 안 뜨는 사고를 방지하기 위한 가이드.

---

## 1. 왜 필요한가

지금 프로젝트는 `master/data.sql`, `stock/data.sql` 에 수백 개의 UUID 가 하드코딩되어 있다. 팀원 여럿이 각자 브랜치에서 새 시드를 추가하다 보면:

- **PK 중복**: 서로 다른 테이블 추가라고 해도 같은 UUID 쓰면 `Duplicate entry for key PRIMARY`
- **비-hex 문자**: `tf000001` 처럼 `t`/`i`/`p` 등 hex 가 아닌 문자 쓰면 `UNHEX()` 가 NULL 반환 → `Column 'id' cannot be null`
- **FK 불일치**: 참조하는 UUID 가 실제 존재하지 않음 → FK 제약 위반

**앱이 아예 안 뜬다**. 팀원 전부 blocked.

---

## 2. UUID 네이밍 규칙

현재 쓰이는 prefix 패턴:

| 테이블 | 첫 4자리 (prefix) | 예시 |
|---|---|---|
| clients | `01935c00-0000-7000-` | `01935c00-0000-7000-8000-000000000001` |
| product_categories | `01935c00-0000-7050-` | `01935c00-0000-7050-8000-000000000001` |
| product_groups | `01935c00-0000-7060-` | `01935c00-0000-7060-8000-000000000001` |
| products | `01935c00-0000-7700-` | `01935c00-0000-7700-8000-000000000001` |
| warehouses | `01935c00-0000-7100-` | `01935c00-0000-7100-8000-000000000001` |
| zones | `01935c00-0000-7200-` | `01935c00-0000-7200-8000-000000000001` |
| racks | `01935c00-0000-7300-` | `01935c00-0000-7300-8000-000000000001` |
| rack_layouts | `01935c00-0000-7310-` | `01935c00-0000-7310-8000-000000000001` |
| locations | `01935c00-0000-7400-` | `01935c00-0000-7400-8000-000000000001` |
| suppliers | `01935c00-0000-7500-` | `01935c00-0000-7500-8000-000000000001` |
| stores | `01935c00-0000-7600-` | `01935c00-0000-7600-8000-000000000001` |

**새 엔티티 시드 추가 시** 마지막 12자리(`000000000001`) 만 증가시켜 고유하게 유지.

### 절대 금지

- ❌ `tf000001-...` / `ib000001-...` / `pk000001-...` 처럼 **hex 아닌 문자** 사용 금지
  - 허용 문자: `0-9`, `a-f` 만
- ❌ 타 테이블과 같은 UUID 재사용 금지 (PK 는 UUID 이므로 테이블이 달라도 관례상 겹치지 않게)

---

## 3. 새 시드 추가 체크리스트

**data.sql 에 `INSERT` 추가하기 전 매번 확인**:

### 3-1. 넣으려는 UUID 가 이미 사용 중인지 검사

```bash
# 예: 01935c00-0000-7300-8000-000000000050 추가하려는 경우
grep -n "01935c00-0000-7300-8000-000000000050" master/src/main/resources/data.sql
grep -n "01935c00-0000-7300-8000-000000000050" stock/src/main/resources/data.sql
```

→ **아무 결과도 안 나와야** 안전하게 추가 가능.

### 3-2. UUID 포맷 검증 (hex 문자만 쓰였는지)

```bash
# 모든 UNHEX UUID 중 비-hex 문자 포함된 것 찾기
grep -oE "UNHEX\(REPLACE\('[^']+'" master/src/main/resources/data.sql stock/src/main/resources/data.sql \
  | grep -iE "[g-z]" \
  | sort -u
```

→ **빈 결과**가 나와야 정상. 뭔가 나오면 수정 필요.

### 3-3. 편한 방법: 일괄 검증 스크립트

매번 `grep` 타자 치기 귀찮으니 `docs/check-seed.sh` 돌리면 됨.

```bash
./docs/check-seed.sh
```

결과:
- 🟢 `OK: No duplicates / No invalid hex` → PR 올려도 안전
- 🔴 중복/잘못된 UUID 발견 → 터미널에 리스트 출력, 수정 후 재실행

---

## 4. 흔한 실수 & 대처

### Case 1: "앱 실행 시 Duplicate entry for key PRIMARY"

```
Caused by: SQLIntegrityConstraintViolationException: Duplicate entry '...' for key 'PRIMARY'
```

**원인**: 방금 추가한 UUID 가 기존 레코드의 UUID 와 같음.

**해결**:
```bash
# 에러 메시지에 나온 UUID (예: 00000000000d) 를 전체 검색
grep -n "7300-8000-00000000000d" master/src/main/resources/data.sql

# 새로 추가한 쪽의 UUID 를 미사용 번호(예: 00000000006) 로 변경
```

관련 FK 도 함께 수정해야 함 (locations.rack_id, rack_layouts.rack_id 등).

### Case 2: "Column 'id' cannot be null"

```
Caused by: SQLIntegrityConstraintViolationException: Column 'id' cannot be null
```

**원인**: `UNHEX('tf000001...')` 같이 비-hex 문자 포함 → NULL 반환.

**해결**: 해당 UUID 의 첫 두 글자를 hex 로 교체 (`t` → `a`, `i` → `1`, `p` → `9`, `k` → `c`, `s` → `5`).

### Case 3: "Cannot add or update a child row: foreign key constraint fails"

**원인**: FK 로 참조하는 UUID 가 대상 테이블에 존재하지 않음.

**해결**:
```bash
# FK 로 넣은 UUID 가 대상 테이블에 실제 존재하는지 확인
grep "01935c00-0000-7200-8000-000000000017" master/src/main/resources/data.sql
# 없으면 zone INSERT 먼저 하거나, 정확한 UUID 로 수정
```

---

## 5. PR 올리기 전 의무 절차

1. ✅ `./docs/check-seed.sh` 통과
2. ✅ 로컬에서 해당 모듈 재시작 성공 확인
3. ✅ 새 UUID 추가한 경우, **위 [UUID 네이밍 규칙](#2-uuid-네이밍-규칙)** 섹션에 테이블 prefix 가 이미 있는지 확인

---

## 6. 장기 개선 계획 (참고)

지금 방식은 수백 개 UUID 를 사람이 관리하는 구조라 근본적으로 충돌 위험이 상존함. 발표/1차 릴리스 이후 여유가 생기면 다음을 검토:

- [ ] **Java 기반 시드**: `ApplicationRunner` 에서 `@PrePersist` + `UuidCreator` 로 UUID 자동 생성, FK 는 엔티티 참조로 연결
- [ ] **Flyway/Liquibase** 로 마이그레이션 스크립트 버전 관리
- [ ] **통합 테스트**에 `data.sql` 로드 + 기본 쿼리 검증 포함 → CI 에서 걸러짐

지금은 이 가이드 + 스크립트로 충분.
