# WBS (We Build Systems)
**지능형 스마트 창고 관리 플랫폼 (WMS)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/baaa3ab9-6b25-4142-9109-8672c41e113e" width="50%">
</div>

<br>

## 👥 팀원 소개

| 김세연 | 황건하 | 이은경 | 박준형 |
| :---: | :---: | :---: | :---: |
| <img src="https://github.com/user-attachments/assets/2d8f51f4-69a8-4146-9fa5-03c11ebca166" width="120" height="120"><br>[@tpdus55](https://github.com/tpdus55) | <img src="https://github.com/user-attachments/assets/3a54b9d7-ca1b-4cd1-8a2c-8a39e5083c86" width="120" height="120"><br>[@LittleNiddle](https://github.com/LittleNiddle) | <img width="150" height="150" alt="Image" src="https://github.com/user-attachments/assets/08bf9777-8144-4012-b871-7cd268fc219d" /><br>[@DLDMSRUD-BIT](https://github.com/DLDMSRUD-BIT) | <img src="https://github.com/user-attachments/assets/a4d97c2f-9be3-4a7a-87ea-bd23c56a08aa" width="150" height="150"><br>[@tony00](https://github.com/abilitytony) 

<br>

> 🛠 **개발 환경 세팅·AI 기능 가이드** → [docs/SETUP.md](docs/SETUP.md)

## 1. 프로젝트 개요
WBS는 규모에 관계없이 물류 및 제조 창고를 효율적으로 운영할 수 있도록 지원하는 웹 기반 창고 관리 시스템(WMS)입니다. 기존 창고 관리의 고질적인 문제들을 해결하고, 2D 시각화 및 모바일 앱을 통해 현장 업무를 완전히 디지털화하는 것을 목표로 합니다. 다수의 고객사를 지원하는 SaaS 방식으로, 고객사별 독립적인 운영 환경을 제공합니다.

## 2. 기획 배경 (해결하고자 하는 문제)
기존 물류 창고 현장에서 발생하는 주요 한계점을 극복하기 위해 기획되었습니다.

* 위치 파악의 한계: 상품 위치 확인이 어려워 발생하는 피킹 및 출고 지연 문제
* 수기 입력의 오류: 엑셀 수기 관리 방식으로 인한 재고 불일치 및 클레임 발생
* 고비용 장비 의존: 고가의 PDA 장비 필수 사용으로 인한 초기 도입 및 유지보수 부담

## 3. 핵심 가치 및 주요 기능
WBS는 차별화된 4가지 핵심 기능을 통해 창고 운영을 최적화합니다.

* 창고 2D 시각화 및 커스터마이징
  - Drag & Drop 방식으로 고객사별 창고 레이아웃(구역, 랙) 자유 배치
  - 2D 평면도 상에서 품번 검색 시 해당 랙 즉시 강조 및 실시간 적재율 색상 구분

* 상품코드 기반 정확한 재고 관리
  - 수기 입력 대신 상품코드 목록 선택 방식으로 입고, 출고, 이동 처리하여 오입력 원천 차단

* 모바일 앱 기반 현장 운영 (PDA 대체)
  - 고가의 PDA 대신 스마트폰(Android/iOS) 앱을 활용하여 입출고 및 재고 실사 현장 즉각 처리

* AI 챗봇 최적화 지원
  - 상품의 최적 적치 위치 추천 및 창고 효율성 분석/개선 방안 제시
  - 자연어 기반의 편리한 재고 조회 및 상담 기능

## 4. 기대 효과
* 운영 효율 극대화: 2D 시각화로 이동 동선을 최소화해 피킹 시간을 단축하고, 상품코드 선택 방식으로 데이터 신뢰성을 확보합니다.
* 비용 절감: 현장 작업자의 개인 스마트폰 활용으로 별도의 장비 구입 및 유지보수 비용을 절감할 수 있습니다.

## 5. 기능 영역별 적용 기술
| 기능 영역        | 최종 적용 기술                       | 적용 방향                                            |
| ------------ | ------------------------------ | ------------------------------------------------ |
| 인증/권한        | `JWT`, `Spring Security`       | SaaS 환경에서 사용자/고객사 단위 접근 제어                       |
| 주문/재고/위치 관리  | `Spring Data JPA`, `QueryDSL`  | 상품, 재고, 위치(랙/구역), 이동 이력 복합 조회                    |
| 입출고 프로세스     | `Spring Boot`                  | 입고/출고/이동 트랜잭션 처리                                 |
| 이벤트 처리       | `Kafka`                        | 출고/입고/이동/재고 부족 이벤트 기반 비동기 처리                     |
| 실시간 통계 처리    | `Kafka Streams`                | 재고 변동 이벤트 기반 실시간 집계 처리                           |
| 창고 2D 시각화    | `React`                        | Drag & Drop 기반 창고 레이아웃 및 상태 시각화                  |
| 모바일 앱        | `React Native`                 | PDA 대체 모바일 입출고 및 재고 처리                           |
| 실시간 알림/상태 반영 | `WebSocket`, `STOMP`           | 재고 상태 및 작업 진행 상황 실시간 반영                          |
| 데이터 저장       | `MySQL`, `MongoDB`, `VectorDB` | 트랜잭션(MySQL) + 통계/이벤트(MongoDB) + AI 임베딩(VectorDB) |
| 캐시           | `Redis`                        | 재고 조회, 세션, 빈번 데이터 캐싱                             |
| 검색           | `Elasticsearch`                | 상품, 위치, 품번 검색 최적화                                |
| 통계/배치        | `Spring Batch`                 | 정기 집계 및 데이터 정합성 처리                               |
| AI 기능        | `Spring AI`, `sLLM`            | 적치 추천, 자연어 질의, 분석 기능                             |
| 인프라          | `Docker`                       | 컨테이너 기반 서비스 구성                                   |
| 배포/운영        | `Kubernetes`                   | 확장 가능한 SaaS 운영 환경                                |
| CI/CD        | `GitHub Actions`               | 자동 빌드 및 배포 파이프라인                                 |

## 6. 팀원별 기술/기능
| 제안자 | 담당 영역             | 회의 반영 내용                                | 연결 기술/기능                        |
| --- | ----------------- | --------------------------------------- | ------------------------------- |
| 김세연 | 이벤트 처리 / 실시간 스트림  | 재고 흐름 기반 이벤트 아키텍처 및 실시간 통계 스트림 처리 설계    | `Kafka`, `Kafka Streams`        |
| 황건하 | 2D 시각화 / 통계 / 데이터 | 창고 2D UI 구현 및 대시보드 통계 화면 + 통계 데이터 구조 설계 | `React`, `MongoDB`              |
| 이은경 | QR / 검색           | QR 기반 입출고 처리 및 상품/위치 검색 최적화             | `QR`, `Elasticsearch`           |
| 박준형 | AI / 벡터 데이터       | AI 추천 및 임베딩 기반 검색/분석 구조 설계              | `Spring AI`, `sLLM`, `VectorDB` |

---

### 프로세스 흐름도

<details>
  <summary><b>입출고 프로세스 다이어그램</b></summary>
  <p align="center">
    <img src="https://github.com/user-attachments/assets/e5a9c2d5-e08d-42a7-ad41-498f8d20751e" width="85%" alt="WMS 입출고 프로세스 다이어그램">
  </p>

  **입고 흐름**
  입고지시서 승인 → 검수 완료 → 적치 완료 순으로 진행되며,
  각 단계를 완료해야 실제 사용 가능한 확정재고가 됩니다.
  불량품은 검수 시 격리되며 재고에 포함되지 않습니다.

  | 단계 | 상태 변화 |
  |------|----------|
  | 입고지시서 생성 | → draft |
  | 입고지시서 승인 | draft → approved |
  | 검수 처리 | approved → in_progress |
  | 적치 완료 | in_progress → completed |

  **출고 흐름**
  출고지시서 승인 시 즉시 재고를 잠그고(예약재고↑),
  출고 확정 시점에 실물이 창고에서 빠져나갑니다(확정재고↓).

  | 단계 | 상태 변화 |
  |------|----------|
  | 출고지시서 생성 | → draft |
  | 출고지시서 승인 | draft → approved |
  | 웨이브 생성 | approved → in_progress |
  | 피킹 완료 | in_progress → completed |
  | 출고 확정 | → completed / partial |
</details>

<details>
  <summary><b>재고 관리 프로세스 다이어그램</b></summary>
  <p align="center">
    <img src="https://github.com/user-attachments/assets/802e0624-918e-43af-aa61-169a34a6579e" width="85%" alt="WMS 재고 관리 프로세스 다이어그램">
  </p>

  **재고 종류**

  | 재고 | 의미 | 출고 가능 |
  |------|------|----------|
  | 입고예정재고 | 승인됐지만 아직 미입고 (참고용) | ❌ |
  | 가재고 | 검수 완료, 적치 전 | ❌ |
  | 확정재고 | 적치 완료, 창고에 있는 실재고 | ✅ |
  | 예약재고 | 출고 승인으로 잠긴 재고 | ❌ |
  | 가용재고 | 확정재고 − 예약재고 | ✅ |

  **재고 변화 시점**

  | 시점 | 재고 변화 |
  |------|----------|
  | 입고 승인 | 입고예정재고 ↑ |
  | 검수 완료 | 입고예정재고 ↓ / 가재고 ↑ |
  | 적치 완료 | 가재고 ↓ / 확정재고 ↑ |
  | 출고 승인 | 가용재고 ↓ / 예약재고 ↑ |
  | 출고 취소 | 예약재고 ↓ / 가용재고 ↑ (원복) |
  | 출고 확정 | 예약재고 ↓ / 확정재고 ↓ |
</details>

<br>

<details>
  <summary><b>📥 1. 입고과정 프로세스 (Inbound Process)</b></summary>
  <div align="center">
    <img src="https://github.com/user-attachments/assets/e658d3ce-3fe1-4649-9392-1ee2885d5d2c" width="90%" />
    <p><i>입고 표준 절차</i></p>
  </div>
</details>

<br>

<details>
  <summary><b>📦 2. 출고과정 프로세스 (Outbound Process)</b></summary>
  <div align="center">
    <img src="https://github.com/user-attachments/assets/cfb74614-d7f0-49b9-b3a0-3fb100ed7014" width="90%" />
    <p><i>피킹, 출고 절차</i></p>
  </div>
</details>
<br>

<details>
  <summary><b>프로젝트 기획서 전문 보기 </b></summary>
  <br>

  - [WBS-We-build-systems 기획서.pdf](https://github.com/user-attachments/files/26337151/WBS-We-build-systems.pdf)
</details>

<br>

<details>
  <summary><b>요구사항 명세서 </b></summary>
  <br>

  - [지능형 유통물류관리시스템 요구사항명세서 (Google Excel)](https://docs.google.com/spreadsheets/d/1ba527ZFP-LdppqA_cePZKZwfe6hjzHWkB06XtscqAlI/edit?gid=1528350653#gid=1528350653)
</details>

<br>

<details>
  <summary><b>wbs </b></summary>
  <br>

  - [wbs (Google Excel)](https://docs.google.com/spreadsheets/d/1ba527ZFP-LdppqA_cePZKZwfe6hjzHWkB06XtscqAlI/edit?gid=1480431342#gid=1480431342)
</details>

<br>

<details>
  <summary><b>erd </b></summary>
  <br>
  
  <img width="5840" height="3782" alt="Image" src="https://github.com/user-attachments/assets/631f0964-480b-4b9f-9009-f950f7646124" />
  
  - [erd](https://www.erdcloud.com/d/DHWzagGGXJfcLEbBB)
</details>

---

## 📌 산출물

<details>
  <summary><b>🖥️ 화면 설계서 (Figma)</b></summary>

  <br>

  [Figma](https://www.figma.com/design/pGYEcbs5fObYfpz73gkS6w/WMS-%EC%B0%BD%EA%B3%A0%EA%B4%80%EB%A6%AC%EC%8B%9C%EC%8A%A4%ED%85%9C-?node-id=0-1&t=absTrKly2dr7GkdK-1)

</details>

<details>
  <summary><b>📡 프로그램 사양서 및 단위테스트결과서 (Postman)</b></summary>

  <br>

  [Postman Documentation](https://documenter.getpostman.com/view/51368756/2sBXqDrNds#e34248e2-9a3b-42ef-a37b-f937fd112078)

</details>
