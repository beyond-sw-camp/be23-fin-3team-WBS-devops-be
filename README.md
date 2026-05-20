# WBS (We Build Systems)

## 지능형 스마트 창고 관리 플랫폼 (WMS)

<div align="center">
  <img src="https://github.com/user-attachments/assets/1930ac8b-d94b-4267-ab8b-b7ac28588ce3">
</div>

<br>

## 프로젝트 소개

> 입고부터 출고까지 창고 운영 전 과정을 한 시스템에 통합한 **SaaS 기반 스마트 창고 관리 시스템(WMS)** 입니다.

- 입고·출고·재고를 하나의 흐름으로 통합 관리
- React Native 모바일 앱으로 PDA 없이 QR 기반 현장 작업
- 2D 창고 시각화 + 실시간 적재율·품번 검색
- Kafka Streams 기반 실시간 대시보드 + 통합 알림
- AI 챗봇으로 자연어 재고 조회·적치 추천

---

## 📑 목차

1. [팀원 소개](#1-팀원-소개)
2. [프로젝트 기획서](#2-프로젝트-기획서)
3. [주요 기능 & 시연](#3-주요-기능--시연)
4. [분석 및 설계](#4-분석-및-설계)
5. [기술스택](#5-기술스택)
6. [시스템 아키텍처](#6-시스템-아키텍처)
7. [상세 기능](#7-상세-기능)
8. [기술 문서](#8-기술-문서)
9. [트러블 슈팅](#9-트러블-슈팅)

---

## 1. 팀원 소개

| 김세연 | 황건하 | 이은경 | 박준형 |
| :---: | :---: | :---: | :---: |
| <img src="https://github.com/user-attachments/assets/2d8f51f4-69a8-4146-9fa5-03c11ebca166" width="120" height="120"><br>[@tpdus55](https://github.com/tpdus55) | <img src="https://github.com/user-attachments/assets/3a54b9d7-ca1b-4cd1-8a2c-8a39e5083c86" width="120" height="120"><br>[@LittleNiddle](https://github.com/LittleNiddle) | <img width="150" height="150" alt="Image" src="https://github.com/user-attachments/assets/08bf9777-8144-4012-b871-7cd268fc219d" /><br>[@DLDMSRUD-BIT](https://github.com/DLDMSRUD-BIT) | <img src="https://github.com/user-attachments/assets/a4d97c2f-9be3-4a7a-87ea-bd23c56a08aa" width="150" height="150"><br>[@tony00](https://github.com/abilitytony) |

---

## 2. 프로젝트 기획서

### 2.1 프로젝트 주제

<div align="center">
  <img width="1244" height="698" alt="주제 선정배경" src="https://github.com/user-attachments/assets/7522ec72-ee9d-4646-8335-d6218f0e037c" />
  <br><br>
  <img width="1247" height="700" alt="기존 WMS 한계" src="https://github.com/user-attachments/assets/2506e3ef-3ef9-43d7-8c95-132700d88661" />
</div>

<details>
<summary>📄 상세 내용 보기</summary>

#### 2.1.1 주제 선정 배경

이커머스 시장의 빠른 성장으로 물류 처리의 정확성과 속도가 기업 경쟁력의 핵심 요소가 되었습니다.

- 온라인 커머스 환경에서 입고, 출고, 재고, 반품의 실시간 관리 필요성 증가
- 국내 물류 기업의 WMS 해외 도입 및 수출 사례 확대
- 스마트 물류 시장은 2021년부터 2027년까지 연평균 26% 성장 전망

따라서 저희는 입고·출고·재고·반품을 통합 관리할 수 있는 WMS를 프로젝트 주제로 선정했습니다.

<br>

#### 2.1.2 기존 창고관리시스템의 한계점

기존 WMS는 물류 현장의 운영 효율을 높이기 위해 도입되어 왔지만, 실제 현장에서는 여전히 몇 가지 한계를 가지고 있습니다.

- 전용 PDA 장비에 대한 의존성과 높은 도입 비용
- 창고별 데이터가 분산되어 통합 관리가 어려운 구조
- 수작업과 엑셀 중심으로 운영되는 재고 관리 방식

특히 주문 채널이 늘어나고 SKU가 세분화된 환경에서는 이러한 한계가 더욱 크게 드러납니다.  
빠른 출고와 정확한 재고 관리가 중요해진 상황에서, 위치 혼선, 재고 불일치, 출고 오류, 반품 처리 지연은 곧 운영 리스크로 이어질 수 있습니다.

저희는 이러한 문제를 해결하기 위해 보다 효율적이고 통합적인 창고 관리 시스템이 필요하다고 판단했습니다.

</details>

<br>

### 2.2 기획 의도 및 차별성

<div align="center">
  <img width="1242" height="696" alt="기획의도" src="https://github.com/user-attachments/assets/0a397700-ba70-4299-a396-9bd1554509e0" />
</div>

<details>
<summary>📄 상세 내용 보기</summary>

#### 2.2.1 기획 의도

> "사람이 결정하던 창고를, 시스템이 판단하는 창고로"

저희 WBS는 기존 WMS가 가지고 있던 높은 장비 의존성, 분산된 재고 관리, 수작업 중심 운영의 한계를 개선하는 것을 목표로 기획되었습니다.  
단순히 재고를 기록하는 시스템이 아니라, 물류 현장에서 더 빠르고 정확한 판단이 이루어질 수 있도록 창고 운영 전반을 시스템 중심으로 전환하는 데 초점을 맞추었습니다.

이를 위해 다음과 같은 다섯 가지 차별점에 집중했습니다.

- **자동 추천 시스템**  
  물건의 보관 위치를 시스템이 자동으로 추천하여, 담당자의 경험에 의존하던 적치 판단을 데이터 기반으로 보완할 수 있도록 했습니다.

- **모바일 워크플로우**  
  전용 PDA 장비 없이 스마트폰만으로 입고, 출고, 피킹 등 현장 작업을 처리할 수 있도록 구성했습니다. 이를 통해 장비 도입 비용을 낮추고, 보다 유연한 현장 운영이 가능하도록 했습니다.

- **실시간 재고 확인**  
  실시간 대시보드를 통해 여러 창고의 재고 현황과 작업 진행 상황을 한눈에 확인할 수 있도록 했습니다. 분산된 재고 데이터를 하나의 화면에서 통합적으로 관리할 수 있다는 점이 핵심입니다.

- **2D 창고 시각화**  
  사용자가 자신의 창고 구조에 맞게 직접 평면도를 설계하고 수정할 수 있도록 하여, 창고 구조와 재고 위치를 더 직관적으로 관리할 수 있도록 했습니다.

- **AI 챗봇**  
  자연어 기반 질의를 통해 재고 현황이나 작업 상태를 바로 조회할 수 있도록 하여, 시스템 접근성과 활용성을 높이고자 했습니다.

결국 WBS는 기존 WMS의 한계를 보완하고, 창고 운영을 사람의 감각 중심이 아닌 시스템의 판단 중심으로 전환하는 것을 목표로 합니다.

<br>

#### 2.2.2 프로젝트 주제 특화 포인트

- 창고 운영 전 과정을 하나의 시스템 안에서 통합 관리할 수 있도록 구성
- 창고 레이아웃 기반 위치 관리로 실제 현장 구조를 반영한 재고 운영 지원
- 실시간 운영 대시보드와 알림 기능을 통해 빠른 상황 인지 및 대응 가능
- 모바일 앱에서 QR 기반으로 검수, 적치, 피킹, 출고 확정 작업 수행 가능
- AI 챗봇을 통해 업무 안내 및 운영 정보 확인 지원
- 문서/증빙, 감사 로그, 배치 및 자동화 기능까지 포함한 운영 지원 구조 제공

<br>

#### 2.2.3 기존 서비스와의 차별성

- 단순 수량 조회 중심이 아닌 창고 구조와 운영 흐름을 함께 반영한 통합 관리 방식
- 입고, 출고, 반품, 이동, 기타입출고를 개별 기능이 아닌 하나의 운영 흐름으로 연결
- Kafka Streams 기반 실시간 집계로 운영 현황을 빠르게 반영
- 모바일 앱을 통해 작업자가 별도 PDA 없이 QR 기반 작업 수행 가능
- AI 챗봇을 활용해 사용자 접근성과 편의성 강화
- 재고 부족, 출고 불가, 작업 지연 등 예외 상황에 대응할 수 있는 알림 및 운영 관리 기능 강화

</details>

---

## 3. 주요 기능 & 시연

#### 📊 핵심 프로세스 흐름도

<details open>
<summary><b>📥 입고 프로세스</b></summary>
<div align="center">
  <img src="https://github.com/user-attachments/assets/d548a369-23b0-4af5-a0df-506ed8b141de" width="90%" />
  <p><i>입고 표준 절차</i></p>
</div>
</details>

<details open>
<summary><b>📦 출고 프로세스</b></summary>
<div align="center">
  <img src="https://github.com/user-attachments/assets/787a9d5d-b421-4158-b613-afac180b70b6" width="90%" />
  <p><i>피킹, 출고 절차</i></p>
</div>
</details>

<details open>
<summary><b>🤖 AI 챗봇 프로세스</b></summary>
<div align="center">
  <img src="https://github.com/user-attachments/assets/dab23ce5-30e4-46c5-9e40-a3910a4b374f" width="90%" />
  <p><i>AI 챗봇 워크플로우</i></p>
</div>
</details>

---

### 3.1 웹 (관리자)

#### 3.1.1 입고 — 웹 단계

**🎬 발주서 기반 입고지시서 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/904635f6-d15e-4a56-b75a-9ed31efad642" width="720" alt="발주서 기반 입고지시서 생성 시연 영상" />
</div>

**🎬 입고지시서 수동 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/e2065dd0-d6df-476f-92ca-7ff003a98f70" width="720" alt="입고지시서 수동 생성 시연 영상" />
</div>

**🎬 입고 확정 및 검수 (웹)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/fa4f7579-34b5-4bfa-a21a-ae9a9708039b" width="720" alt="입고 확정 및 검수 웹 시연 영상" />
</div>

**🎬 적치 (웹)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/699d5f12-0872-450a-b487-884fab50004f" width="720" alt="적치 웹 시연 영상" />
</div>

ERP 발주서를 입고지시서로 전환하거나 운영자가 직접 입고지시서를 생성한 뒤, 검수·적치까지 이어지는 입고 관리자 작업입니다.

- **3가지 생성 경로**: 발주서 자동 / 수동 / 반품 기반 — 출처 유형 자동 분기
- **추천 창고**: 협력사 전용 랙(1순위) → 카테고리 매칭 구역(2순위) → 활성 창고 점수로 후보 응답, 운영자가 직접 선택
- **승인 시 자동 처리**: 검수 작업자 자동 배정 + 입고지시서 PDF 자동 발행 + **입고예정재고 ↑**
- **이력 관리**: 발주서 진행률 자동 갱신, 다층 조회·키워드 통합 검색

→ 검수·적치 단계는 [3.2.1 모바일 입고](#321-입고--검수적치)로 이어집니다.

#### 3.1.2 출고 — 웹 단계

**🎬 수주서 기반 출고지시서 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/bbaedb55-e7c7-44ba-aea6-c05534f0a1e9" width="720" alt="수주서 기반 출고지시서 생성 시연 영상" />
</div>

**🎬 출고지시서 수동 생성 및 승인**

<div align="center">
  <img src="https://github.com/user-attachments/assets/2443fe93-780b-4696-a8ff-0878e1542516" width="720" alt="출고지시서 수동 생성 및 승인 시연 영상" />
</div>

**🎬 피킹 리스트 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/ea6a225e-4192-42d6-8fa9-297c941794af" width="720" alt="피킹 리스트 생성 시연 영상" />
</div>

**🎬 피킹 완료 (웹)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/17a39eae-d9b3-4310-a436-888516063181" width="720" alt="피킹 완료 웹 시연 영상" />
</div>

ERP 수주서를 출고지시서로 전환하거나 운영자가 직접 출고지시서를 생성한 뒤, ATP 검증 → 승인 → 피킹 → 출고 확정까지 처리합니다.

- **단일 / 분할 출고**: 한 창고로 충분하면 단일, 부족하면 창고별 분할 출고지시서 자동 분기
- **ATP 사전 검증**: 가용재고 + 입고예정 + 검수중 합산. 부족하면 승인 차단 + **출고 불가 수주 화면**에 자동 등록·해제
- **승인 시**: **가용재고 ↓ / 예약재고 ↑** (잠금) + 피커 자동 배정 + 출고지시서 PDF 자동 발행
- **웨이브 + 자동 위치 분배**: 여러 출고지시서를 묶어 상품별 피킹리스트 생성, 한 위치 부족 시 여러 로케이션 자동 분산
- **출고 확정**: 출고전표 PDF 자동 발행 + **예약재고·확정재고 동시 차감** + 통계 실시간 반영

→ 피킹 단계는 [3.2.2 모바일 출고](#322-출고--피킹출고-확정)로 이어집니다.

---


### 3.2 모바일 앱 (작업자)

> React Native + Expo SDK 54 기반 단일 앱. 입고·적치·피킹·출고를 PC 없이 현장에서 처리합니다. JWT 자동 주입 + Gateway 헤더 인가 + expo-camera QR 스캐너로 UUID 단위 매칭.

#### 3.2.1 입고 — 검수·적치

**🎬 입고 (앱)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/5d40ac80-9b8e-41ed-b054-a35b6a8be0db" width="360" alt="시연 영상" />
</div>

웹에서 승인된 입고지시서를 현장에서 QR 스캔으로 처리합니다.

- **검수**: 입고지시서 QR 스캔 → 품목별 정상·불량·로트번호 한 화면 입력. **부분 검수 차단** (전 품목 입력해야 완료)
- **자동 적치 지시 생성**: 검수 완료 시점에 시스템이 추천 위치(협력사·카테고리 매칭, 1로케이션-1SKU 룰)로 적치 지시 자동 생성
- **적치**: 위치 QR + 상품 QR 스캔 후 완료. 한 위치 부족 시 여러 로케이션 자동 분산. 적치 중 불량 발견 시 DEFECT 구역으로 즉석 분기
- **재고 변화**: 검수 완료 → **가재고 ↑** / 적치 완료 → **확정재고 ↑** (출고 가능)

#### 3.2.2 출고 — 피킹·출고 확정

**🎬 출고 (앱)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/037cd47c-2f55-4887-b745-f49031b25d5c" width="360" alt="시연 영상" />
</div>

웹에서 승인된 출고지시서·피킹리스트를 현장에서 QR 스캔으로 처리합니다.

- **피킹**: 위치 QR → 상품 QR → 꺼낸 수량 입력. 부족 발생 시 **부분 피킹(shortage)** 처리, 정상분만 진행 가능
- **이동 동선 최적화**: 마지막 작업 위치 기록 → 다음 배정 거리 페널티 점수에 반영
- **출고 확정**: 모바일에서 직접 확정 가능. 출고전표 자동 생성 + **예약재고·확정재고 동시 차감**
- **부분/전량 마감**: 부족 품목 있으면 PARTIAL, 전량 정상이면 COMPLETED

---

### 3.3 창고 2D 레이아웃 시각화

**🎬 레이아웃 위치 확인**

<div align="center">
  <img src="https://github.com/user-attachments/assets/3d8beaf9-d649-40fd-8923-8414e3b5652d" width="720" alt="시연 영상" />
</div>

물리적 창고 구조를 화면에 그대로 옮겨 위치 기반 운영을 지원합니다.

- **Drag & Drop 편집**: 고객사별 창고 레이아웃(구역·랙)을 자유롭게 배치
- **실시간 적재율 색상**: 랙별 사용률에 따라 색상이 자동 변경되어 포화 구역 즉시 식별
- **품번 검색 강조**: 품번 입력 시 해당 상품이 적치된 랙이 평면도에서 즉시 강조 표시
- **랙 라벨 출력**: 출력 PDF 자동 생성으로 현장 부착 즉시 가능

---

### 3.4 실시간 대시보드 & 통계

**🎬 대시보드**

<div align="center">
  <img src="https://github.com/user-attachments/assets/ccbcd8ad-f5c3-4109-8e22-31f40245b917" width="720" alt="시연 영상" />
</div>

운영자가 입고·출고·이동·반품 작업 현황과 재고 부족 이슈를 한 화면에서 확인할 수 있도록 구성한 대시보드입니다.

- **KPI 카드**: 신규 주문, 신규 발주, 오늘 입고, 재고 부족, 출고 불가 수주 등 핵심 지표 제공
- **오늘 처리 현황**: 입고·출고·이동·기타 작업의 진행/대기 상태를 집계하여 작업 흐름 표시
- **처리 필요 지시서**: 지연, 오늘 마감, 진행 중, 승인 대기 지시서를 우선순위 기준으로 노출
- **Kafka Streams 집계**: 시간별 처리량, 진행 중 작업 수, 반품 비율을 스트림 기반으로 집계
- **통계 차트**: 최근 7일 입·출고 추이, 지난 24시간 시간별 처리량, 오늘 반품 비율 시각화
- **알림 연동**: 재고 부족 및 출고 불가 상태를 WebSocket/STOMP 알림으로 관리자에게 전달
---

### 3.5 AI 챗봇

**🎬 AI 챗봇 — 업무 범위 밖 질문 차단**

<div align="center">
  <img src="https://github.com/user-attachments/assets/26a9cba5-24b1-438a-a040-3e02b0544ff9" width="720" alt="시연 영상" />
</div>

WMS AI 챗봇은 사용자의 자연어 질문을 분석해 업무 데이터 조회, 운영 매뉴얼 안내, 민감정보 차단 흐름으로 분기하는 업무 보조 어시스턴트입니다.

- **질문 분류 기반 워크플로우**
  - 사용자 질문을 `일반 대화`, `업무 데이터 조회`, `RAG 문서 답변`, `민감정보 차단`으로 분류합니다.
  - “마우스 재고 어디 있어?”, “오늘 처리할 작업 뭐야?”, “불량 사진 어디서 봐?”처럼 구어체 질문도 처리할 수 있습니다.

- **업무 데이터 조회**
  - `stock-service`, `master-service`, `account-service`의 내부 API를 호출해 현재 업무 데이터를 조회합니다.
  - 재고 위치, 입고/출고 현황, 피킹 작업, 부족 재고, 상품/창고/로케이션, 사용자 역할/권한 정보를 자연어로 안내합니다.

- **RAG 기반 운영 가이드 응답**
  - Spring AI와 PostgreSQL pgvector를 활용해 운영 매뉴얼, 화면 사용 가이드, 상태값 정의, 예외 처리 기준을 검색합니다.
  - “출고 지시서 생성이 왜 실패해?”, “불량 증빙 사진 어디서 확인해?” 같은 절차/원인/화면 경로 질문에 문서 기반으로 답변합니다.

- **민감정보 차단**
  - 전화번호, 이메일, 로그인 ID, 비밀번호, 토큰, 주민등록번호 등 개인정보 또는 인증정보 요청을 사전에 차단합니다.
  - 차단 대상 질문은 업무 API 호출이나 RAG 검색 없이 고정 문구로 응답합니다.

- **자동 지식 문서 반영**
  - ai-service 기동 시 `knowledge/*.txt` 문서를 자동으로 pgvector에 upsert합니다.
  - 배포 후 개발자가 Postman으로 문서를 수동 등록하지 않아도 RAG 지식이 최신화됩니다.

- **사용 기술**
  - Spring Boot, Spring Cloud OpenFeign, Spring AI, OpenAI API, PostgreSQL pgvector, Redis


---

## 4. 분석 및 설계

<details>
<summary><b>📋 WBS</b></summary>

<br>

- [WBS (Google Excel)](https://docs.google.com/spreadsheets/d/1ba527ZFP-LdppqA_cePZKZwfe6hjzHWkB06XtscqAlI/edit?gid=1480431342#gid=1480431342)

</details>

<details>
<summary><b>📑 요구사항 명세서</b></summary>

<br>

- [지능형 유통물류관리시스템 요구사항명세서 (Google Excel)](https://docs.google.com/spreadsheets/d/1ba527ZFP-LdppqA_cePZKZwfe6hjzHWkB06XtscqAlI/edit?gid=1528350653#gid=1528350653)

</details>

<details>
<summary><b>🗂️ ERD</b></summary>

<br>

<img width="5840" height="3782" alt="ERD" src="https://github.com/user-attachments/assets/631f0964-480b-4b9f-9009-f950f7646124" />

- [ERD (ERDCloud)](https://www.erdcloud.com/d/9okuYbfrEta7QAzrr)

</details>

<details>
<summary><b>📡 API 명세서 / 단위테스트결과서 (Postman)</b></summary>

<br>

- [Postman Documentation](https://documenter.getpostman.com/view/51368756/2sBXqDrNds#e34248e2-9a3b-42ef-a37b-f937fd112078)

</details>

<details>
<summary><b>🖥️ 화면 설계서 (Figma)</b></summary>

<br>

- [Figma](https://www.figma.com/design/pGYEcbs5fObYfpz73gkS6w/WMS-%EC%B0%BD%EA%B3%A0%EA%B4%80%EB%A6%AC%EC%8B%9C%EC%8A%A4%ED%85%9C-?node-id=0-1&t=absTrKly2dr7GkdK-1)

</details>

---

## 5. 기술스택

<h3 align="center">Backend</h3>
<p align="center">
  <img src="https://img.shields.io/badge/Java_17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Boot_3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Cloud_2024.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/Spring_AI_1.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white"/>
  <img src="https://img.shields.io/badge/WebSocket_STOMP-010101?style=for-the-badge&logo=socketdotio&logoColor=white"/>
  <img src="https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white"/>
  <img src="https://img.shields.io/badge/QueryDSL-0769AD?style=for-the-badge&logo=hibernate&logoColor=white"/>
  <img src="https://img.shields.io/badge/Lombok-BC4521?style=for-the-badge&logo=lombok&logoColor=white"/>
  <img src="https://img.shields.io/badge/Thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white"/>
  <img src="https://img.shields.io/badge/OpenPDF-CC0000?style=for-the-badge&logo=adobeacrobatreader&logoColor=white"/>
  <img src="https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white"/>
</p>

<h3 align="center">Frontend</h3>
<p align="center">
  <img src="https://img.shields.io/badge/React_18-61DAFB?style=for-the-badge&logo=react&logoColor=black"/>
  <img src="https://img.shields.io/badge/TypeScript-3178C6?style=for-the-badge&logo=typescript&logoColor=white"/>
  <img src="https://img.shields.io/badge/Vite-646CFF?style=for-the-badge&logo=vite&logoColor=white"/>
  <img src="https://img.shields.io/badge/React_Router-CA4245?style=for-the-badge&logo=reactrouter&logoColor=white"/>
  <img src="https://img.shields.io/badge/Ant_Design-0170FE?style=for-the-badge&logo=antdesign&logoColor=white"/>
  <img src="https://img.shields.io/badge/TanStack_Query-FF4154?style=for-the-badge&logo=reactquery&logoColor=white"/>
  <img src="https://img.shields.io/badge/Zustand-2D2D2D?style=for-the-badge&logo=react&logoColor=white"/>
  <img src="https://img.shields.io/badge/Recharts-22B5BF?style=for-the-badge&logo=chartdotjs&logoColor=white"/>
  <img src="https://img.shields.io/badge/Konva-0D83CD?style=for-the-badge&logo=react&logoColor=white"/>
  <img src="https://img.shields.io/badge/Axios-5A29E4?style=for-the-badge&logo=axios&logoColor=white"/>
  <img src="https://img.shields.io/badge/STOMP.js-010101?style=for-the-badge&logo=socketdotio&logoColor=white"/>
</p>

<h3 align="center">App</h3>
<p align="center">
  <img src="https://img.shields.io/badge/React_Native_0.81-61DAFB?style=for-the-badge&logo=react&logoColor=black"/>
  <img src="https://img.shields.io/badge/Expo_SDK_54-000020?style=for-the-badge&logo=expo&logoColor=white"/>
  <img src="https://img.shields.io/badge/expo--camera-000020?style=for-the-badge&logo=expo&logoColor=white"/>
</p>

<h3 align="center">Infra & Cloud</h3>
<p align="center">
  <img src="https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kubernetes-326CE5?style=for-the-badge&logo=kubernetes&logoColor=white"/>
  <img src="https://img.shields.io/badge/Nginx_Ingress-009639?style=for-the-badge&logo=nginx&logoColor=white"/>
  <img src="https://img.shields.io/badge/Apache_Kafka_3.7-231F20?style=for-the-badge&logo=apachekafka&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kafka_Streams-231F20?style=for-the-badge&logo=apachekafka&logoColor=white"/>
  <img src="https://img.shields.io/badge/Zookeeper-D22128?style=for-the-badge&logo=apache&logoColor=white"/>
  <img src="https://img.shields.io/badge/Elasticsearch_9.3-005571?style=for-the-badge&logo=elasticsearch&logoColor=white"/>
  <img src="https://img.shields.io/badge/Kibana-005571?style=for-the-badge&logo=kibana&logoColor=white"/>
  <img src="https://img.shields.io/badge/MySQL-4479A1?style=for-the-badge&logo=mysql&logoColor=white"/>
  <img src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white"/>
  <img src="https://img.shields.io/badge/PostgreSQL_pgvector-4169E1?style=for-the-badge&logo=postgresql&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS_EKS-FF9900?style=for-the-badge&logo=amazoneks&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS_EC2-FF9900?style=for-the-badge&logo=amazonec2&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS_RDS-527FFF?style=for-the-badge&logo=amazonrds&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS_S3-569A31?style=for-the-badge&logo=amazons3&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS_ECR-FF9900?style=for-the-badge&logo=amazonaws&logoColor=white"/>
  <img src="https://img.shields.io/badge/AWS_CloudFront-8C4FFF?style=for-the-badge&logo=amazoncloudfront&logoColor=white"/>
  <img src="https://img.shields.io/badge/GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white"/>
</p>

<h3 align="center">Test & Docs</h3>
<p align="center">
  <img src="https://img.shields.io/badge/Postman-FF6C37?style=for-the-badge&logo=postman&logoColor=white"/>
  <img src="https://img.shields.io/badge/Figma-F24E1E?style=for-the-badge&logo=figma&logoColor=white"/>
  <img src="https://img.shields.io/badge/ERDCloud-4B8BBE?style=for-the-badge&logo=databricks&logoColor=white"/>
  <img src="https://img.shields.io/badge/Google_Sheets-0F9D58?style=for-the-badge&logo=googlesheets&logoColor=white"/>
</p>

<h3 align="center">Tools & Collaboration</h3>
<p align="center">
  <img src="https://img.shields.io/badge/IntelliJ_IDEA-000000?style=for-the-badge&logo=intellijidea&logoColor=white"/>
  <img src="https://img.shields.io/badge/VS_Code-007ACC?style=for-the-badge&logo=visualstudiocode&logoColor=white"/>
  <img src="https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white"/>
  <img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white"/>
  <img src="https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white"/>
</p>

<br>


## 6. 시스템 아키텍처

<div align="center">
  <img width="1886" height="1001" alt="시스템 아키텍처" src="https://github.com/user-attachments/assets/71c93181-e188-45d8-8870-1bde963316c5" />
</div>

---


## 7. 상세 기능

<details>
<summary><strong>7.1 마스터 관리</strong></summary>

### 7.1.1 입고처 관리

**🎬 입고처 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/39e42690-3d10-402c-b1d9-4ecd56fafe04" width="720" alt="시연 영상" />
</div>

- **기본 CRUD**: 협력사명·사업자번호·대표자·연락처·주소 입력·조회·수정
- **협력사 코드 중복 검증**: 회사 단위로 코드 유니크 자동 검증
- **활성화 관리**: 비활성화(soft-delete) 및 재활성화 토글
- **ESG 정보 관리**: ESG 등급·친환경 인증 여부·관리자 메모 관리
- **상품 매핑**: 협력사별 판매 SKU·단가·리드타임·MOQ 관리

### 7.1.2 출고처 관리

**🎬 출고처 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/124c3d2f-1c9b-47f5-af1b-c537b7e47ca6" width="720" alt="시연 영상" />
</div>

- **출고처(Store) CRUD**: 출고처명·사업자번호·대표자·연락처·주소 생성·조회·수정
- **출고처 주소 관리**: 출고처별 다중 배송지 등록 + 수령자 정보 관리
- **활성화 관리**: 비활성화 토글
- **자동 웨이브 토글**: 출고처별 자동 웨이브 처리 대상 여부 설정
- 입고처는 별도 엔티티 없이 협력사(Supplier)로 통합 관리

### 7.1.3 상품 관리

**🎬 상품 추가**

<div align="center">
  <img src="https://github.com/user-attachments/assets/51f8f19f-36b4-4518-9347-91db406be06d" width="720" alt="시연 영상" />
</div>

- **상품 CRUD**: SKU·바코드·국영문 상품명·설명·단위·박스 포장·표준가 입력·조회·수정·비활성화
- **SKU 자동 제안**: 카테고리 기준 PREFIX-SEQ 형식 자동 계산 (저장 시 편집 가능)
- **협력사 매핑**: 상품 생성·수정 시 협력사 ID 매핑
- **카테고리 매핑**: 상품 → 상품그룹 → 카테고리 계층 연결
- **다양한 검색**: 키워드(상품명·SKU·바코드) + 멀티필터(브랜드·카테고리·옵션·활성화 상태)

### 7.1.4 상품군 / 카테고리 관리

**🎬 카테고리 추가**

<div align="center">
  <img src="https://github.com/user-attachments/assets/6f636d23-67b8-4cb0-869b-34717eebd224" width="720" alt="시연 영상" />
</div>

- **카테고리 계층**: 최대 3단계 트리 구조 지원, 자동 depth 계산
- **계층 무결성**: 비활성 상위 아래 신규 생성 차단, 활성화 시 상위 활성 필수
- **Soft-delete RESTRICT**: 활성 하위 카테고리·상품그룹이 있으면 비활성화 거부
- **상품그룹 CRUD**: 그룹명·브랜드·설명·카테고리 매핑
- **SKU 그룹 제안**: 카테고리별 그룹 개수 기반 자동 코드 제안
- **그룹 활성화 무결성**: 그룹 참조 활성 상품이 있으면 비활성화 거부, 카테고리 비활성이면 활성화 거부

### 7.1.5 안전재고 관리

**🎬 안전재고 설정**

<div align="center">
  <img src="https://github.com/user-attachments/assets/5cdaf18d-fe0c-4d47-bfc9-71c91a55f295" width="720" alt="시연 영상" />
</div>

- 기준 재고 설정: 상품 × 창고 조합별 안전재고 등록
- 조건 조회: 상품, 창고, SKU, 상품명 기준 검색
- 일괄 적용: 동일 상품 안전재고를 여러 창고에 한 번에 반영
- 수정 / 삭제: 기존 기준 재고 유지보수
- 알림 연동: 재고 부족 알림 기준값으로 사용

### 7.1.6 옵션 타입 / 옵션값 관리

**🎬 상품 옵션 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/3b15b79a-4862-47bd-89af-7eef83ca8283" width="720" alt="시연 영상" />
</div>

- **옵션 타입 생성**: 회사 단위 격리 + 타입 코드 중복 검증
- **옵션값 등록**: 같은 옵션 타입 내 코드 유니크, clientId 일치 검증
- **정렬 순서 관리**: 옵션값 `sortOrder` 지원

</details>

<details>
<summary><strong>7.2 창고 관리</strong></summary>

### 7.2.1 창고 등록 및 기본정보 관리
- **창고 CRUD**: 창고명·주소·매니저명·전화·관리 메모 생성·조회·수정
- **자동 채번**: `WH-지역코드-타입-순번` 형식 자동 생성
- **창고 타입**: NORMAL / RETURN / DISPOSAL 등 분류 지원
- **활성화 관리**: 비활성화 및 재활성화 토글
- **타입별 필터**: 창고 목록 조회 시 타입 필터링 지원

### 7.2.2 구역 / 랙 / 로케이션 관리
- **구역 계층**: 최대 3단계 트리 구조, 자동 depth 계산
- **카테고리 매핑 구역**: 구역 단위로 카테고리 매핑 지원 (적치 추천에 활용)
- **협력사 전용 랙**: 랙 생성 시 협력사 ID 선택적 지정 (적치 추천 1순위)
- **자동 로케이션 생성**: 랙 생성 시 레벨 수만큼 층별 로케이션 자동 생성
- **수용량 관리**: 로케이션·랙별 `maxCapacity` 설정, null이면 무제한
- **물리 정보**: 랙의 가로·세로·높이 mm 단위 치수 + 층별 가이드 JSON

### 7.2.3 랙 라벨 출력
- (코드 미확인) 랙 라벨 PDF/QR 발행 로직 구현 미발견 — 향후 구현 또는 별도 모듈 가능성

</details>

<details>
<summary><strong>7.3 창고 모니터링</strong></summary>

### 7.3.1 창고 현황 조회
- **대시보드 KPI**: 오늘 입고·출고 건수, 재고 부족 수, 미완료 건수, 승인 대기 건수 통합 제공
- **단계별 진행 현황**: 입고·출고·이동 진행 중/대기 상태 구분, 오늘 완료 건수 집계
- **지연 패널**: 마감일 경과 미완료 건수를 한 화면에서 확인

### 7.3.2 위치별 재고 모니터링
- **랙 단위 재고 조회**: 창고 내 활성 로케이션 전체 조회 후 재고 집계
- **상품별 로케이션 조회**: 위치(Location) 단위 재고 현황 조회
- **N+1 최적화**: Feign 배치 호출로 위치/상품 정보 일괄 조회

### 7.3.3 수용량 / 사용률 확인
- **로케이션 용량 검증**: 현재 보관량이 새 수용량을 초과하면 변경 거부
- **랙 일괄 용량 변경**: 모든 로케이션 변경 원자성 보장, 초과 위치 코드 식별 후 응답
- (코드 미확인) 사용률 % 계산·시각화 로직은 별도 미발견

</details>

<details>
<summary><strong>7.4 반품 / 이동 / 기타입출고 관리</strong></summary>

### 7.4.1 반품 입고 관리

**🎬 반품 입고**

<div align="center">
  <img src="https://github.com/user-attachments/assets/7f44dfe0-fb39-4646-9967-3cddb93b90ab" width="720" alt="시연 영상" />
</div>

- **생성 트리거**: 출고지시서 기반 (`InboundController.createFromReturn`)
- **수량 검증**: 출고 수량 초과 방지 + 기존 반품 누적 수량 포함 중복 방지
- **자동 채번**: 입고지시서 번호 자동 생성
- **상태 머신**: draft → approved → received → 적치 완료 (취소 가능)
- **Elasticsearch 색인**: 생성 즉시 인덱싱으로 통합 검색 지원
- **WebSocket 알림**: `CREATED_RETURN` 타입 메시지로 같은 회사 관리자에 push
- **출처 추적**: 원본 출고지시서·출고처 정보 보존하여 반품 출처 명확화

### 7.4.2 반품 출고 관리

**🎬 반품 출고**

<div align="center">
  <img src="https://github.com/user-attachments/assets/e5ba791c-3f3f-4cba-a793-2710d5bf5201" width="720" alt="시연 영상" />
</div>

- **생성 트리거**: 입고지시서 기반 (`OutboundController.createReturnOutbound`)
- **원본 매칭 검증**: 원본 입고처(`supplierId`) 일치 + 검수 완료 수량(`receivedQty`) 범위 내에서만 반품 가능
- **누적 수량 차단**: 기 반품 출고분 포함 누적값이 검수 수량 초과 시 차단
- **반품 사유 필수**: `returnReason` 자유 텍스트 필수 입력
- **출고 대상**: `destinationType=supplier`, `storeId=null` (협력사로 직접 반품)
- **상태 머신**: draft → approved → in_progress → 출고 확정
- **이벤트 발행**: 생성·승인·완료 단계별 알림 push

### 7.4.3 창고 간 이동 관리

**🎬 이동 지시서**

<div align="center">
  <img src="https://github.com/user-attachments/assets/ba60bb67-0b19-45ac-9ae0-d4bc5bfac90f" width="720" alt="시연 영상" />
</div>

- 라이프사이클: 초안 → 승인 → 작업 진행 → 완료 / 부분 완료 / 취소, 자동 번호 부여 + 승인 시 작업자 자동 배정
- 출발/도착 검증: 동일 위치 차단, 위치 유효성 사전 검증
- 모바일 2단계 흐름 (픽업 / 적치): 출발지 픽업 → 도착지 적치 분리 처리, 웹은 한 번에 통합 완료 가능
- 수량 무결성 3중 검증: 지시 / 픽업 / 적치 단계마다 초과 차단
- 정상/불량 분기 적치: 정상은 가용 영역, 불량은 격리 영역 자동 분기
- 알림 + 실행 이력: 단계별 비동기 이벤트 + 실시간 알림 + 작업자·시점·수량 자동 기록 (감사·운영 분석)

### 7.4.4 기타입고 관리

**🎬 기타 입고**

<div align="center">
  <img src="https://github.com/user-attachments/assets/ad7128e3-5e28-460b-8cf4-c231931fabc7" width="720" alt="시연 영상" />
</div>

- 4가지 입고 유형: 샘플 / 재고 조정 / 재발주 / 폐기
- 검증 + 상태 머신: 폐기 입고는 폐기 전용 창고 한정, 초안 → 승인 → 완료 / 취소
- 자동 채번 + 작업자 자동 배정 + 지시서 자동 발행
- 모바일 통합 처리: 정상/불량 분리 + 적치 위치 한 번에 입력, 모든 품목 처리 시 자동 완료 전환
- 불량 적치 default 안내: 해당 창고의 불량 전용 구역 첫 위치를 응답에 박아 작업자 안내
- 재고 반영 + 알림: 정상은 가용 영역, 불량은 격리 영역, 비동기 이벤트 + 목록/상세/작업자 1:1 실시간 알림

### 7.4.5 기타출고 관리

**🎬 기타 출고**

<div align="center">
  <img src="https://github.com/user-attachments/assets/5f4714df-0faa-418d-8233-843ddd35d195" width="720" alt="시연 영상" />
</div>

- 4가지 출고 유형: 샘플 / 재고 조정 / 재발주 입고 요청 / 폐기
- 필수 검증: 출고처 / 사유 / 폐기창고 등 유형별 분기
- 누적 가용재고 검증: 등록 시점부터 부족 차단, 부족 시 부족 품목 페이로드 응답
- 재고 기준 분기: 폐기 출고는 격리 재고 기준, 나머지는 가용재고 기준
- 출고 통째 취소 흐름: 후보 출고 조회 → 통째 취소 → 재발주 추적 링크 저장
- 자동 link 저장: 등록 시 취소된 출고 ID 들 받으면 link 자동 생성 (메일 본문 정합성 보장)
- 입고 요청 메일 자동 발송: 외부 협력사로 자동 발송, 미리보기 / 발송 / 이력 3개 흐름
- 메일 본문 자동 구성: 실제 취소된 출고의 품목을 합산하여 부족 품목 본문 자동 작성, 운영자가 폼에서 자유 편집 가능
- 메일 이력 저장: 발송 전 사전 저장 → 시도 → 결과 (성공 시 메시지 ID, 실패 시 사유) 업데이트
- 모바일 픽업 처리: 실제 픽업 수량 입력, 부족 시 해당 품목만 부족 표기, 지시서 자체는 완료 마감
- 재고 반영: 가용 차감 / 폐기 출고는 격리 영역 차감

</details>

<details>
<summary><strong>7.5 지시서 목록 및 문서/증빙 관리</strong></summary>

### 7.5.1 통합 지시서 목록 조회
- 통합 조회: 입고, 출고, 이동 지시서를 한 화면에서 확인
- 조건 필터: 유형, 상태, 우선순위, 지연 여부, 오늘 처리 대상 기준 조회
- 대시보드 연동: 처리 필요 지시서에서 바로 이어서 관리 가능
- 상세 이동: 행 선택 시 해당 지시서 상세 화면으로 이동

### 7.5.2 공식 문서 관리

**🎬 공식 문서함**

<div align="center">
  <img src="https://github.com/user-attachments/assets/465dd139-9d76-4b7b-86ca-f3e51e7462f7" width="720" alt="시연 영상" />
</div>

- **다조건 검색**: docType·sourceId·status·발행일자 범위 필터링 (`InstructionDocumentController.search`)
- **버전 관리**: 동일 거래의 재발행 시 version 자동 증가(v1, v2, …) — 발행 이력 추적
- **SHA-256 멱등성**: 직전 발행본과 내용 동일하면 새 버전 생성 없이 기존 버전 재사용
- **S3 저장**: `{clientId}/{docType}/{yyyy-MM}/{sourceId}_v{version}.pdf` 키 구조
- **Presigned URL**: 브라우저가 S3에 직접 다운로드, 백엔드 대역폭 0
- **상태 추적**: GENERATING → READY (성공) 또는 FAILED (오류 메시지 기록)
- **요약 통계**: 사이드바용 카운트 API 제공 (`summary()`)

### 7.5.3 증빙 자료 관리
- **불량 사진 업로드**: multipart로 서버 경유 S3 업로드 (`DefectEvidenceController.upload`)
- **메타 저장 분리**: 사진 자체는 S3, 메타(sourceType·sourceId·reasonCode·reasonText·sha256·업로더·시간)는 DB
- **다형 참조**: `(source_type, source_id)` 조합으로 입고전표품목·출고품목 등 다양한 도메인 연결 가능
- **사유 분류**: 정형 사유 코드(`DefectReasonCode`) + 자유 텍스트 메모 동시 지원
- **다운로드**: Presigned GET URL로 브라우저가 S3에서 직접 다운로드
- **삭제**: S3 객체 + DB 행 동시 삭제
- **조회**: `sourceType`·`sourceId`로 필터링하여 관련 사진만 목록 조회

### 7.5.4 지시서 문서 발행 / 조회
- 문서 출력: 입고지시서, 입고전표, 출고지시서, 출고전표, 피킹리스트, 이동지시서, 재고조사지시서 발행
- 상세 화면 중심: 각 작업 상세 페이지에서 바로 출력 및 조회
- PDF 생성: 지시서 유형별 템플릿 기반 PDF 발행
- 상태 확인: 생성 중 / 완료 / 실패 상태 조회
- 버전 관리: 재발행 이력, 최신본 여부 확인
- 원본 연결: 발행 문서에서 다시 작업 상세 화면으로 이동 가능

</details>

<details>
<summary><strong>7.6 재고 관리</strong></summary>

### 7.6.1 재고 현황 조회

**🎬 재고 현황**

<div align="center">
  <img src="https://github.com/user-attachments/assets/7c48c663-cf55-400d-98d9-a87cad270180" width="720" alt="시연 영상" />
</div>

- 현재 / 기준일 조회: 실시간 재고 또는 특정 시점 재고 확인
- 조건 검색: 창고, 구역, 상품 기준 조회
- 수량 구분: 가용, 예약, 불량, 검수중, 입고예정, 총재고 확인
- 이력 확인: 항목 선택 시 재고 변동 이력 조회
- 위치 연동: 로케이션 클릭 시 창고 모니터링 화면 이동

### 7.6.2 재고 조사 생성 및 관리

**🎬 재고 실사 (웹)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/68e9061a-d34c-445a-a855-3b7828004f8b" width="720" alt="시연 영상" />
</div>

- 조사 지시 생성: 창고 선택 후 실사 대상 상품 지정
- 위치 기준 분리: 같은 상품이 여러 위치에 있으면 위치별 조사 항목 생성
- 상태 관리: 초안, 진행중, 완료, 취소 상태로 운영
- 시작 / 취소 제어: 초안 상태에서 조사 시작 또는 취소 가능

### 7.6.3 재고 조사 상세 확인
- 실사 입력: 시스템 수량과 실제 수량 비교 입력
- 항목별 저장: 실사 수량, 비고, 상태값 관리
- 완료 조건: 모든 항목 입력 후 실사 완료 가능
- 자동 조정: 차이 수량 발생 시 재고 자동 반영

### 7.6.4 위치 기반 재고 확인

**🎬 재고 위치 조회**

<div align="center">
  <img src="https://github.com/user-attachments/assets/1f337d48-f994-4ee3-8ec1-4e6bdd6a5d1c" width="720" alt="시연 영상" />
</div>

**🎬 재고 위치 조회 (상품 검색)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/559000b9-4bba-4e75-b610-e82d47d38920" width="720" alt="시연 영상" />
</div>

- 위치 중심 관리: 창고-구역-랙-로케이션 기준 재고 확인
- 위치 재검증: 재고 조사 상세에서 특정 위치 바로 확인
- 모니터링 연계: 창고 모니터링 화면과 연결해 실제 보관 위치 검증

</details>

<details>
<summary><strong>7.7 통계</strong></summary>

### 7.7.1 회전율 분석
- 회전율 계산: 출고 수량과 평균 재고 기준 분석
- 월별 추이 확인: 재고 활용도 높은 시점과 낮은 시점 비교
- 운영 활용: 적정 재고 및 보관 효율 검토

### 7.7.2 랭킹 분석
- 출고량 순위 조회: 특정 기간 동안 가장 많이 출고된 상품을 순위 형태로 확인
- 조건 설정: 조회 기간과 Top N 범위를 지정하여 원하는 기준으로 조회 가능
- 시각화 제공: 차트와 표를 통해 주요 출고 상품을 한눈에 비교 가능

</details>

<details>
<summary><strong>7.8 공통 관리</strong></summary>

### 7.8.1 알림 관리

**🎬 실시간 알림 (WebSocket)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/3cfc7045-9f35-4df8-82bc-e0524c78717c" width="720" alt="시연 영상" />
</div>

- 통합 알림함: 운영 중 발생한 주요 이벤트를 시간순 조회
- 이벤트 중심: 출고 불가, 재고 부족, 부족 해소 등 확인
- 빠른 이동: 관련 상세 화면으로 바로 연결

### 7.8.2 재고 부족 품목 관리
- 부족 품목 조회: 안전재고 이하 상품을 창고별로 확인
- 상태 구분: 재고없음, 부족, 주의 단계로 표시
- 탭 분류: 전체 / 창고별 기준으로 부족 품목 확인
- 즉시 대응: 입고 생성 또는 안전재고 수정으로 바로 연결
- 기준 연동: 안전재고 설정값을 기준으로 운영

### 7.8.3 감사 로그 조회

**🎬 감사 로그**

<div align="center">
  <img src="https://github.com/user-attachments/assets/61a4f673-3988-462b-8f13-9c5ec39d7fc6" width="720" alt="시연 영상" />
</div>

- **Elasticsearch 전문 검색**: 4개 마이크로서비스의 감사 로그를 단일 인덱스에서 통합 검색 (`AuditLogSearchController.search`)
- **다중 필터**: keyword(전체 텍스트) + userId·action(포함/제외 리스트)·HTTP 메서드·entityName·responseStatus·처리시간·날짜 범위
- **자동 기록 필드**: 사용자명·서비스명·HTTP 메서드·요청 URI·응답 상태·실행 시간(ms)·IP·작업 종류 자동 캡처
- **CQRS 파이프라인**: `@AuditLog` AOP가 Kafka `audit.created` 발행 → search-service가 `audit-logs-v2` 인덱스에 비동기 색인
- **회사 격리**: `clientId`로 멀티테넌시 자동 적용
- **자동완성**: 사용자·서비스·작업·대상·경로·IP별 키워드 제안 지원
- **시간·상태 코드 범위**: from-to 날짜 + 2xx/3xx/4xx/5xx 응답 코드 범위 필터링

### 7.8.4 웨이브 스케줄러 관리
- 스케줄러 실행: 승인된 출고지시서를 대상으로 자동 웨이브 생성 작업을 실행
- 실행 이력 조회: 실행 시각, 성공 여부, 처리 건수, 소요 시간 확인
- 결과 상세 확인: 어떤 출고지시서와 피킹리스트가 처리되었는지 조회
- 운영 점검: 자동 웨이브 스케줄러가 정상 수행되었는지 확인

### 7.8.5 자동 웨이브 대상 관리
- 대상 설정: 출고처별 자동 웨이브 사용 여부를 설정
- 자동 / 수동 구분: 자동 처리할 출고처와 수동 처리할 출고처를 분리 운영
- 스케줄러 반영: 활성화된 출고처의 승인된 출고지시서만 자동 웨이브 생성 대상에 포함
- 운영 제어: 출고처별 정책에 따라 자동화 범위를 조정

</details>

<details>
<summary><strong>7.9 설정</strong></summary>

### 7.9.1 사용자 관리

**🎬 admin 로그인**

<div align="center">
  <img src="https://github.com/user-attachments/assets/37f538f8-2db9-44c4-b237-4d7a7bc13e73" width="720" alt="시연 영상" />
</div>

**🎬 admin 계정 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/08863f3d-1b72-4dea-b93b-aa671431329d" width="720" alt="시연 영상" />
</div>

- **계정 생성**: 관리자가 로그인ID·이름·이메일·전화번호 + 역할 부여하여 사용자 생성, 동일 회사 내 역할만 부여 가능
- **계정 조회 및 상세**: 관리자가 소속 회사의 활성 사용자 목록 + 개별 상세 조회
- **계정 수정**: 사용자 정보(이름·이메일·전화번호)와 역할 변경 가능, 역할 변경 시 권한 캐시 자동 무효화
- **계정 비활성화**: 관리자가 사용자 비활성화 가능, 본인 계정은 비활성화 불가
- **ADMIN 역할 보호**: 일반 관리자는 ADMIN 역할을 다른 사용자에게 부여하거나 부여받을 수 없음

### 7.9.2 권한 관리

**🎬 admin 역할 생성**

<div align="center">
  <img src="https://github.com/user-attachments/assets/d54e840d-8cb3-426f-8552-d308bda51150" width="720" alt="시연 영상" />
</div>

- **동적 역할 기반 권한**: 권한 = 리소스(입고/출고/이동/재고/실사/기타입출고/마스터/통계) × 작업(생성/조회/수정/삭제/승인) 조합으로 정의
- **회사별 커스텀 역할**: 관리자가 회사 전용 역할 정의 가능, 권한 조합 자유롭게 매핑
- **역할 수정 및 권한 재할당**: 역할 이름·설명 수정 + 권한 변경 시 해당 역할 사용 사용자의 권한 캐시 자동 무효화
- **시스템 기본 역할 보호**: ADMIN/MANAGER/OPERATOR는 시스템 예약 코드로 삭제 불가
- **권한 캐시**: `PermissionCacheService`로 권한 캐시 관리, 로그인 후 권한은 캐시에서 조회

### 7.9.3 내 정보 관리
- **프로필 조회**: 본인 정보(이름·로그인ID·이메일·전화번호·역할·권한 목록) 확인
- **프로필 수정**: 이메일·전화번호 수정 가능 (이름·로그인ID는 수정 불가)

### 7.9.4 비밀번호 변경

**🎬 admin 비밀번호 변경**

<div align="center">
  <img src="https://github.com/user-attachments/assets/8f956df1-162a-42d2-83f7-4d16b6da5646" width="720" alt="시연 영상" />
</div>

- **자가 변경**: 현재 비밀번호 검증 후 새 비밀번호로 변경
- **비밀번호 검증**: 입력한 현재 비밀번호가 DB의 암호화 값과 일치하지 않으면 거부
- (코드 미확인) 관리자 강제 비밀번호 변경, 이메일/SMS 검증, 비밀번호 복잡성 규칙

</details>

<details>
<summary><strong>7.10 개발자 전용 기능</strong></summary>

### 7.10.1 고객사 관리

**🎬 개발자 회사 등록**

<div align="center">
  <img src="https://github.com/user-attachments/assets/cd707ba3-6145-443a-94cf-00e5d03427f5" width="720" alt="시연 영상" />
</div>

- **고객사 생성**: 개발자가 회사명·사업자번호 + 마스터 관리자 계정(로그인ID·비밀번호·이메일)을 한 번에 생성
- **초기 역할·권한 자동 생성**: 고객사 생성 시 회사 전용 표준 역할 3종(ADMIN, MANAGER, OPERATOR) 자동 생성 + 권한 자동 할당
- **고객사 목록 조회**: 활성 고객사만 조회
- **고객사 상세 조회**: 고객사 상세 + 소속 사용자 목록 조회
- **고객사 비활성화**: 고객사 + 소속 모든 사용자 일괄 비활성화

### 7.10.2 초기 운영 환경 구성
- **권한 시드 데이터**: 애플리케이션 시작 시 8개 리소스 × 5개 작업 조합으로 고정 권한 38개 자동 생성
- **개발자 계정 자동 생성**: 시스템 첫 시작 시 로그인ID `developer` 계정 자동 생성
- **더미 고객사 자동 생성**: 테스트 환경용 "테스트회사" + 표준 역할 3종 + 사용자 21명(관리자 1·매니저 5·오퍼레이터 15) 자동 생성
- **발표용 고객사 별도 생성**: 데모 환경 분리용 별도 고객사 + 관리자·매니저·오퍼레이터 각 1명 자동 생성
- **조건부 초기화**: `app.seed.initial-data-load.enabled=true` 설정 시에만 시드 로드 수행

</details>

<details>
<summary><strong>7.11 모바일 앱 (작업자용)</strong></summary>

### 7.11.1 로그인

**🎬 로그인 (앱)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/68c37145-3c44-460f-a313-2ba146ef93c3" width="360" alt="시연 영상" />
</div>

- 토큰 기반 로그인: 토큰 발급 후 모든 요청에 자동 첨부
- 게이트웨이 검증: 토큰 검증 후 다운스트림 라우팅
- 헤더 자동 주입: 회사 식별자 + 사용자 식별자 자동 주입, 서비스 레이어 권한 검증
- 단기 알림 토큰: 실시간 알림 연결용 5분짜리 토큰 별도 발급 (장기 토큰 노출 방지)
- 권한 검증: 모든 기능 권한 등급 검증, 작업자급 (오퍼레이터/매니저) 만 접근

### 7.11.2 이동지시서

**🎬 이동 (앱)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/c4faa3f6-5f08-4998-94be-d6ddd176eff8" width="360" alt="시연 영상" />
</div>

- 본인 작업 목록: 픽업 작업 + 적치 작업 분리, 위치 정렬 반환
- 픽업 단계: 출발지 차감, 진행중 상태 전환
- 적치 단계: 도착지 적치, 정상/불량 분기
- 수량 무결성 3중 검증: 지시 / 픽업 / 적치 단계마다 초과 차단
- 단계별 비동기 이벤트: 픽업 시점 + 적치 시점 분리 발행 (부분 실패 격리)

### 7.11.3 기타입출고

**🎬 기타입출고 (앱)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/0a96adb2-466c-436d-9513-9056cc47a8ed" width="360" alt="시연 영상" />
</div>

- 본인 배정 목록: 자동 배정된 작업자에게만 노출 (입고/출고 통합)
- 상세 + 품목 정보: 위치 / 랙 / 구역 코드 + 입고 시 default 불량 적치 위치 함께 응답
- 품목 1건 처리: 입고는 정상/불량/위치/사유 한 번에, 출고는 픽업 수량만
- 자동 완료 전환: 모든 품목 처리 시 지시서 자동 완료 + 실시간 알림
- 작업자 1:1 알림 채널: 본인 배정 변동 실시간 인지

### 7.11.4 재고 실사

**🎬 재고 실사 (앱)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/1c2ec1a1-8ebe-4e50-8196-33bb04de6a3b" width="360" alt="시연 영상" />
</div>

- 목록 정책: 진행 중 실사는 누구나 + 본인이 카운트한 적 있는 실사 (이력 회상 + 새 실사 진입 동시 지원)
- 풍부한 응답: 상품명/SKU + 위치/층 + 랙/구역 정보 함께 응답
- 위치 QR 스캔: 단일 위치 품목 빠른 조회
- 랙 QR 스캔: 랙 안 모든 위치 품목 한 번에
- 수량 + 비고 입력: 음수 거부, 0 허용, 재카운트 가능 (진행 중일 때)
- 완료 처리: 미입력 1건이라도 있으면 거부, 차이 발생 품목은 자동 재고 조정 (수용량 검증 우회)

### 7.11.5 재고 조회

**🎬 재고 조회 (앱)**

<div align="center">
  <img src="https://github.com/user-attachments/assets/c756bbe5-be29-423d-9baa-5fad96f26e11" width="360" alt="시연 영상" />
</div>

- 랙 QR 스캔 재고 조회: 랙 안 모든 위치 재고 4종 수량 (가용 / 예약 / 불량 / 검수중) 동시 표시
- 상품 키워드 검색: 상품 코드 / 이름 기반 검색
- 풍부한 응답: 위치 / 랙 / 구역 코드 + 상품명 함께 제공 (사람이 읽을 정보만 노출)

</details>

---

## 8. 기술 문서

### 🛠 환경 설정
- [시드 UUID 가이드](docs/SEED_UUID_GUIDE.md)

### 🔧 시스템 구조
- [시스템 기술 명세서](docs/TECH_SPEC.md)
- [공통 모듈 명세서](docs/COMMON_MODULE_SPEC.md)
- [2D 창고 레이아웃 명세서](docs/2D_LAYOUT_SPEC.md)
- [모바일 앱 기술 명세서](docs/MOBILE_APP_SPEC.md)

### 📡 메시징·검색·실시간
- [Kafka 이벤트 파이프라인 명세서](docs/KAFKA_SPEC.md)
- [Kafka Streams 실시간 집계 명세서](docs/KAFKA_STREAMS_SPEC.md)
- [Elastic Search 통합 명세서](docs/Elastic%20Search%20통합%20명세서.md)
- [WebSocket·Redis Pub/Sub 명세서](docs/WEBSOCKET_TOPIC_SPEC.md)
- [알림·감사 로그 명세서](docs/ALERT_AUDIT_SPEC.md)

### ☁️ 파일·문서
- [AWS S3 통합 명세서](docs/S3_INTEGRATION_SPEC.md)
- [PDF 발행 시스템 명세서](docs/PDF_SYSTEM_SPEC.md)

### 🤖 AI
- [챗봇 기술문서](docs/SETUP.md)

  
### 📐 비즈니스 정책
- [재고 흐름](docs/재고흐름.md)
- [적치 위치 추천 정책](docs/PLACEMENT_SUGGESTION_SPEC.md)
- [피킹 위치 추천 정책](docs/PICKING_LOCATION_SPEC.md)

## 9. 트러블 슈팅

<details>
<summary>챗봇 개발 트러블슈팅</summary>

### 1. 질문 라우팅 오분류 개선

사용자 질문이 재고 조회, 업무 절차, 오류 원인, 일반 대화 중 어디에 해당하는지 정확히 분류하는 과정에서 문제가 발생했다.

예를 들어 `출고지시서 어디서 만들어?`처럼 화면 경로를 묻는 질문이 업무 데이터 조회나 일반 질문으로 잘못 분류되면 올바른 답변을 생성할 수 없었다.

이를 해결하기 위해 챗봇 라우팅 기준을 `WORK_QUERY`, `RAG`, `GENERAL`, `BLOCKED`로 명확히 분리하고, 절차, 화면 경로, 실패 원인, 오류코드 질문은 RAG 흐름으로 처리되도록 개선했다.

### 2. RAG 검색 정확도 및 근거 부족 문제

운영 가이드 문서가 벡터 DB에 저장되어 있어도 사용자 질문 표현과 문서 표현이 다르면 유사도 점수가 낮아져 답변이 실패하는 문제가 있었다.

예를 들어 `출고지시서 어디서 생성해?` 같은 질문이 문서에는 존재하지만 검색 점수가 기준치보다 낮아 `처리할 수 없습니다`로 응답하는 경우가 있었다.

이를 해결하기 위해 문서 내용을 관리자 관점의 질문 표현으로 보강하고, 질문 재작성 및 검색 질의 보강을 적용해 화면 경로, 메뉴명, 오류코드, 처리 기준이 더 잘 검색되도록 개선했다.

### 3. 답변 품질 및 예외 응답 개선

초기 답변은 조회 결과를 그대로 말하거나 어색한 표현을 생성하는 문제가 있었다.

예를 들어 `100개가 가용합니다`, `필요한 작업을 진행해야 합니다`처럼 실제 사용자에게 부자연스러운 문장이 출력되었다.

이를 해결하기 위해 답변 생성 프롬프트에 업무 용어, 금지 표현, 수량 표현 기준을 강화했고, RAG 근거를 찾은 뒤 LLM 응답 생성이 실패하는 경우에도 오류코드 문서를 기반으로 최소한의 안내가 가능하도록 예외 응답 흐름을 보강했다.

</details>

<details>
<summary>앱 개발 트러블슈팅</summary>

### 1. QR prefix 포함으로 UUID 파싱 실패

**문제 상황**

QR 스캔 후 API 호출 시 `UUID string too large` 에러가 발생했다.

**원인**

QR 값에 `inbound:{UUID}` 형식으로 prefix가 붙어 있었지만, 앱에서 해당 값을 그대로 URL에 삽입하고 있었다.

```text
실제 전달값: inbound:019d7f46-e9d3-7e8f-9149-b789bb80ec03
기대값:      019d7f46-e9d3-7e8f-9149-b789bb80ec03


