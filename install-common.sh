#!/bin/bash
# common 라이브러리를 Maven Local에 설치하는 스크립트
# 사용법: ./install-common.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/common"

echo "Building common library..."
./gradlew clean publishToMavenLocal

echo ""
echo "✓ common-0.0.1-SNAPSHOT installed to ~/.m2/repository/com/beyond/common/"
echo ""
echo "이제 account, stock, master 모듈을 실행할 수 있습니다."
