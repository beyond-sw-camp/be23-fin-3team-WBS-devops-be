#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────
# 시드 데이터 (data.sql) UUID 검증 스크립트
# ────────────────────────────────────────────────────────────
# 용도:
#   - master/stock 의 data.sql 에서
#     1) 중복된 UUID (PK 충돌 가능)
#     2) 비-hex 문자 포함된 UUID (UNHEX 실패)
#   를 찾아 출력한다.
#
# 사용:
#   ./docs/check-seed.sh
#
# 종료코드:
#   0 = 문제 없음
#   1 = 중복 또는 잘못된 UUID 발견
# ────────────────────────────────────────────────────────────

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FILES=(
  "$ROOT/master/src/main/resources/data.sql"
  "$ROOT/stock/src/main/resources/data.sql"
)

RED=$'\033[0;31m'
GREEN=$'\033[0;32m'
YELLOW=$'\033[0;33m'
RESET=$'\033[0m'

EXIT_CODE=0

echo "============================================="
echo " Seed UUID Validator"
echo "============================================="

for file in "${FILES[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "${YELLOW}⚠ Skip: $file (not found)${RESET}"
    continue
  fi

  rel="${file#$ROOT/}"
  echo ""
  echo "▶ $rel"

  # ────────────────────────────────
  # 1) 비-hex 문자 포함된 UUID
  #    UNHEX(REPLACE('...', '-', '')) 안의 문자열 중
  #    a-f / 0-9 / - 이외가 포함되면 실패
  # ────────────────────────────────
  invalid_hex=$(grep -oE "UNHEX\(REPLACE\('[^']+'" "$file" \
    | sed -E "s/UNHEX\(REPLACE\('//" \
    | grep -iE "[g-z]" \
    | sort -u)

  if [[ -n "$invalid_hex" ]]; then
    echo "  ${RED}✗ 비-hex 문자 포함 UUID:${RESET}"
    echo "$invalid_hex" | sed 's/^/    - /'
    EXIT_CODE=1
  else
    echo "  ${GREEN}✓ 모든 UUID hex 검증 통과${RESET}"
  fi

  # ────────────────────────────────
  # 2) PK 중복 탐지
  #    INSERT INTO <table> ... VALUES ( UNHEX(REPLACE('<uuid>', ...)) ... )
  #    첫 UNHEX 가 각 VALUES 레코드의 PK 라는 가정.
  #    테이블별 prefix 가 겹치지 않으므로 전체 UUID 기준 중복 검사.
  # ────────────────────────────────
  dup_uuids=$(grep -oE "UNHEX\(REPLACE\('[a-f0-9-]{36}'" "$file" \
    | sed -E "s/UNHEX\(REPLACE\('//; s/'$//" \
    | sort \
    | uniq -c \
    | awk '$1 > 1 { print $2 " (" $1 "회)" }')

  # 참고: 같은 UUID 가 FK 로 여러 번 등장하는 건 정상.
  # "PK 중복" 만 잡기 위해:
  #   - 라인이 "(" + UNHEX 로 시작 (새 VALUES 레코드의 첫 컬럼 = PK)
  #   - 그 라인의 **첫 번째** UUID 만 추출 (뒤쪽에 같은 라인에 FK 나와도 무시)
  pk_dup=$(grep -E "^[[:space:]]*\(\s*UNHEX\(REPLACE\('[a-f0-9-]{36}'" "$file" \
    | sed -E "s/^[[:space:]]*\([[:space:]]*UNHEX\(REPLACE\(('[a-f0-9-]{36}').*/\1/" \
    | sort \
    | uniq -c \
    | awk '$1 > 1 { print $2 " (" $1 "회)" }')

  if [[ -n "$pk_dup" ]]; then
    echo "  ${RED}✗ PK 중복 의심 UUID:${RESET}"
    echo "$pk_dup" | sed 's/^/    - /'
    echo "  ${YELLOW}  (같은 UUID 가 여러 INSERT 의 첫 컬럼으로 나옴)${RESET}"
    EXIT_CODE=1
  else
    echo "  ${GREEN}✓ PK 중복 없음${RESET}"
  fi
done

echo ""
echo "============================================="
if [[ $EXIT_CODE -eq 0 ]]; then
  echo " ${GREEN}OK: 검증 통과${RESET}"
else
  echo " ${RED}FAIL: 위 항목 수정 후 재실행 필요${RESET}"
  echo ""
  echo " 수정 가이드: docs/SEED_UUID_GUIDE.md"
fi
echo "============================================="

exit $EXIT_CODE
