# 📱 모바일 앱 기술 명세서

**프로젝트명**: WBS Mobile (Warehouse Worker Mobile Client)  
**문서 버전**: 1.0  
**작성일**: 2026-05-12

---

## 1. 개요

본 모바일 앱은 WBS 멀티테넌트 WMS 플랫폼의 창고 작업자용 모바일 클라이언트로, **React Native + Expo** 기반으로 개발되었다. 입고 검수·적치·피킹·출고·이동·재고 실사·기타입출고 전 공정을 QR 스캔 흐름으로 처리하며, 산업용 PDA 장비 없이 작업자 본인의 스마트폰을 단말로 사용할 수 있도록 설계됐다.

| 항목 | 값 |
|---|---|
| 아키텍처 스타일 | React Native 단일 코드베이스 (Cross-platform) |
| 통신 프로토콜 | REST/JSON over HTTPS, JWT Bearer |
| 배포 단위 | Android APK (사내 직접 배포) |
| 주요 외부 인프라 | Expo EAS Build · AWS ALB · Spring Cloud Gateway · S3 |
| 앱 패키지명 | `com.dldmsrud.be23fin3teamWBSfeapp` |

---

## 2. 시스템 아키텍처

### 2.1 빌드 / 배포 흐름

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│              📦 GitHub (코드 저장 / 협업)                 │
│                  ▲                                       │
│                  │ git push                              │
│                  │                                       │
│             👤 Developer                                 │
│                  │                                       │
│                  │ eas build -p android --profile preview│
│                  ▼                                       │
│             ☁️ Expo EAS Cloud Build                       │
│             (Ubuntu + Android SDK + JDK 17)              │
│                  │                                       │
│                  │ APK 산출 + 서명                       │
│                  ▼                                       │
│             📱 APK 다운로드 URL                          │
│                  │                                       │
│                  │ 다운로드 + 설치                       │
│                  ▼                                       │
│             👷 작업자 디바이스                           │
│             (React Native 앱 실행)                       │
└──────────────────────────────────────────────────────────┘
```

### 2.2 런타임 통신 흐름

```
┌──────────────────────────────────────────────────────────┐
│  📱 React Native 앱                                       │
│                                                          │
│  화면(InboundScreen 등)                                  │
│       │                                                  │
│       ▼                                                  │
│  src/api/{domain}.js  (도메인별 API 함수)                │
│       │                                                  │
│       ▼                                                  │
│  src/api/client.js (axios 인스턴스)                      │
│       ├─ Request: Authorization 헤더 자동 첨부           │
│       ├─ baseURL: __DEV__ ? localhost:8080 : 운영        │
│       └─ Response: 401 → 자동 로그아웃                   │
└──────────────────────┬───────────────────────────────────┘
                       │ HTTPS + JWT Bearer
                       ▼
┌──────────────────────────────────────────────────────────┐
│  ☁️ AWS                                                   │
│  Route 53 → ALB (ACM 인증서) → Spring Cloud Gateway      │
│       │                                                  │
│       │ JWT 검증 + X-User-Id / X-Client-Id 헤더 주입     │
│       ▼                                                  │
│  account · stock · transfer · upload (Spring Boot MS)    │
│       │              │                                   │
│       ▼              ▼                                   │
│   RDS (MySQL)     S3 (불량 사진)                         │
└──────────────────────────────────────────────────────────┘
```

→ **핵심**: 앱 코드는 APK 안에 포함되어 사용자 디바이스에 영구 보관되므로, 웹과 달리 별도의 정적 호스팅(S3/CloudFront)이 필요하지 않다.

---

## 3. 기술 스택 — 계층별 명세

### 3.1 언어 & 프레임워크

| 기술 | 버전 | 적용 범위 | 선택 이유 |
|---|---|---|---|
| **JavaScript (ES2022)** | - | 전 모듈 | 빠른 프로토타이핑, TypeScript 미사용 (간결성) |
| **React** | 19.1.0 | 전 모듈 | 함수형 컴포넌트 + Hooks |
| **React Native** | 0.81.5 | 전 화면 | iOS·Android 단일 코드베이스 |
| **Expo SDK** | 54.0.34 | 전 모듈 | 네이티브 모듈 사전 통합, EAS Build 지원 |

### 3.2 Expo 모듈 (네이티브 통합)

| 모듈 | 버전 | 역할 |
|---|---|---|
| **expo-camera** | 17.0.10 | QR/바코드 자동 인식, 불량 사진 촬영 |
| **expo-navigation-bar** | 5.0.10 | 안드로이드 시스템 네비바 색·위치 동적 제어 |
| **expo-status-bar** | 3.0.9 | 상단 상태바 스타일 (라이트/다크) |
| **expo-font** | 14.0.11 | 커스텀 폰트 (Inter, Raleway) 등록 |

### 3.3 통신 & 데이터

| 라이브러리 | 버전 | 역할 |
|---|---|---|
| **axios** | 1.7.0 | HTTPS API 호출 + Interceptor 기반 JWT 자동 첨부 |
| **react-native-svg** | 15.12.1 | 모든 아이콘 (벡터 그래픽) |

### 3.4 빌드 & 배포

| 도구 | 버전 | 역할 |
|---|---|---|
| **EAS CLI** | ≥ 18.11.0 | 클라우드 빌드 명령 인터페이스 |
| **EAS Build** | (서비스) | Ubuntu 빌드 머신에서 APK 빌드 + 서명 |
| **Metro Bundler** | (Expo 내장) | JS 번들링 (`index.android.bundle`) |
| **Hermes** | (Expo 내장) | 안드로이드 JS 엔진 (콜드 스타트 최적화) |
| **Gradle** | 8.x | Android 네이티브 빌드 (EAS가 자동 실행) |

### 3.5 상태 관리

| 기술 | 사용 여부 | 비고 |
|---|---|---|
| `useState` / `useEffect` | ✅ | 화면별 로컬 상태만 사용 |
| `useCallback` / `useRef` | ✅ | 메모이제이션 및 비제어 ref |
| Redux / Zustand / Recoil | ❌ | 단일 사용자 단말 → 글로벌 상태 불필요 |
| React Navigation | ❌ | `useState` 기반 step 전환으로 대체 (의존성 절약) |

→ 의도적으로 라이브러리를 최소화하여 번들 사이즈와 학습 비용을 낮춤.

---

## 4. 화면 명세

### 4.1 화면 목록

| 카테고리 | 화면 | 핵심 기능 |
|---|---|---|
| 로그인 | `LoginScreen` | 로그인 ID/비밀번호 입력, JWT 발급, 본인 정보 조회 |
| 홈 | `HomeScreen` | 4개 주요 작업 카드, 햄버거 사이드 메뉴, 처리 대기 건수 뱃지 |
| 입고 검수 | `InboundScreen` | 지시서 검색·QR 스캔 → 품목별 정상/불량 수량 입력 → 일괄 검수 완료 |
| 입고 완료 | `InCompleteScreen` | 적치 지시서 진입 → 랙 QR 스캔 → 품목별 적치 + 파손 분리 처리 |
| 출고 검수 | `OutboundScreen` | 피킹 배치 진입 → 랙 QR 매칭 → 실제 픽업 수량 입력 (부분 출고 허용) |
| 출고 완료 | `CompleteScreen` | 출고 지시서 확인 후 출고 확정 |
| 이동지시서 | `TransferScreen` | 지시서 QR 스캔 → PICK 랙에서 픽업 → PLACE 랙에 정상/불량 분리 적치 |
| 재고 실사 | `AuditScreen` | 본인 창고 진행중 실사 진입 → 랙 QR 스캔 → 실사 수량 입력 → 자동 마감 |
| 기타입출고 | `EtcInoutScreen` | 본인 배정 작업 진입 → 입고/출고 방향별 처리 |
| 재고 조회 | `StockScreen` | 랙 코드 입력 또는 QR 스캔으로 해당 랙 품목/수량/위치 즉시 조회 |
| 설정 | `SettingsScreen` | 다크/라이트 테마 전환, 로그아웃, 버전 정보 |

### 4.2 화면 전환 패턴

각 화면은 `useState`로 step을 관리하며, 조건부 렌더링으로 하위 단계를 표현:

```js
const [step, setStep] = useState('list');
// step: 'list' → 'qr-order' → 'detail' → 'qr-rack' → 'rack-work'

if (step === 'qr-order') return <QrScanner ... />;
if (step === 'detail') return <DetailView ... />;
```

→ React Navigation 같은 라우터 라이브러리 없이도 깊은 화면 흐름을 표현 가능.

### 4.3 탭 유지 (state 보존) 패턴

`SubScreen`은 모든 하위 화면을 미리 마운트하고 `display: none` 으로 토글:

```jsx
<View style={menuKey === 'inbound' ? styles.screenVisible : styles.screenHidden}>
  <InboundScreen active={menuKey === 'inbound'} />
</View>
```

→ 탭 전환해도 컴포넌트가 언마운트되지 않아 입력 중인 데이터·진행 상태 모두 보존.

---

## 5. 백엔드 통신

### 5.1 통신 프로토콜

| 항목 | 값 |
|---|---|
| 프로토콜 | HTTPS (TLS 1.2 이상) |
| 인증 | JWT Bearer Token (HS512) |
| 콘텐츠 타입 | `application/json` (사진은 `multipart/form-data`) |
| 베이스 URL (운영) | `https://server.wbs.asia` |
| 베이스 URL (개발) | `http://{Metro호스트}:8080` (자동 감지) |

### 5.2 환경 자동 분기

`src/api/client.js` 의 `getAutoBaseUrl()` 이 `__DEV__` 플래그로 분기:

- **개발 (expo start)**: Expo 디버거 호스트의 8080 포트 → 로컬 백엔드
- **APK 빌드**: `app.json` 의 `extra.apiBaseUrl` 또는 운영 URL → AWS 백엔드

→ 빌드 시점에 환경이 자동 결정되므로 배포 실수가 원천 차단됨.

### 5.3 axios Interceptor

| Interceptor | 동작 |
|---|---|
| Request | 모든 요청에 `Authorization: Bearer <token>` 자동 첨부 |
| Response (200~399) | 정상 반환 |
| Response (401) | `onUnauthorized()` 콜백 호출 → `App.js`가 `setIsLoggedIn(false)` |
| Response (기타 에러) | 한국어 메시지로 변환 후 reject → 화면이 토스트로 표시 |

### 5.4 API 엔드포인트

| 화면 | API 파일 | 주요 엔드포인트 |
|---|---|---|
| 로그인 | `auth.js` | `POST /account-service/user/doLogin` |
| 입고 검수 | `inbound.js` | `GET /mobile/inbound/list`<br>`POST /mobile/inbound/{id}/receive` |
| 입고 완료 | `placement.js` | `GET /mobile/placement/list`<br>`POST /mobile/placement/{id}/items/{itemId}/complete` |
| 출고 검수 | `picking.js` | `GET /mobile/picking/list`<br>`POST /mobile/picking/{id}/items/{itemId}/pick` |
| 출고 완료 | `outbound.js` | `GET /mobile/outbound/list`<br>`POST /mobile/outbound/{id}/dispatch` |
| 이동지시서 | `transfer.js` | `GET /mobile/transfer/{id}/tasks`<br>`POST /mobile/transfer/items/{itemId}/pick`<br>`POST /mobile/transfer/items/{itemId}/place` |
| 재고 실사 | `stockCount.js` | `GET /mobile/stock-count`<br>`PATCH /mobile/stock-count/{id}/items/{itemId}/count`<br>`PATCH /mobile/stock-count/{id}/complete` |
| 기타입출고 | `etcInout.js` | `GET /mobile/etc-inout/my-list`<br>`POST /mobile/etc-inout/{orderId}/items/{itemId}/process` |
| 재고 조회 | `stock.js` | `GET /mobile/stock/by-rack/{rackId}` |
| 불량 사진 | `defectEvidence.js` | `POST /defect-evidence` (multipart) |

→ 모든 엔드포인트는 stock-service 경유 (Gateway 라우팅).

---

## 6. 핵심 기능 구현

### 6.1 QR 스캐너 (`components/QrScanner.js`)

| 기능 | 구현 |
|---|---|
| 자동 인식 | `<CameraView onBarcodeScanned={...}>` — 모든 QR/바코드 타입 자동 인식 |
| JSON 파싱 | `{"type":"inbound","id":"<UUID>"}` 형태의 페이로드를 자동 디코딩 |
| 권한 자동 요청 | `useCameraPermissions()` — 마운트 시 OS 권한 요청 |
| 권한 거부 시 fallback | 텍스트 입력창 자동 표시 → 수동 ID 입력 가능 |
| AppState 복귀 처리 | 백그라운드 → 포그라운드 시 권한 상태 자동 재확인 |
| 시스템 바 복원 | 카메라 종료 시 `expo-navigation-bar`로 앱 색상 복원 |
| 중복 스캔 방지 | `scannedRef`로 1.5초 디바운스 |

### 6.2 불량 사진 촬영 (`components/DefectPhotoCapture.js`)

| 기능 | 구현 |
|---|---|
| 풀스크린 카메라 | `Modal` 안에 `<CameraView mute>` 렌더 |
| 즉시 업로드 | `takePictureAsync({ shutterSound: false })` → `POST /defect-evidence` |
| S3 저장 | 백엔드가 회사별 폴더 구조(`{clientId}/{sourceType}/{yyyy-MM}/...`)로 저장 |
| 재진입 복원 | 마운트 시 `GET /defect-evidence?sourceType=&sourceId=` 로 기존 사진 복원 |
| 썸네일 표시 | 로컬 캐시 URI 또는 백엔드 presigned URL (TTL 300s) 사용 |
| 사진 수 제한 | 기본 3장 (`maxPhotos` prop으로 제어) |

### 6.3 상태 기반 작업 차단

각 화면은 진입 시 지시서의 `status`를 검증하여 잘못된 작업을 차단:

| 상태 | 동작 |
|---|---|
| `draft` | 진입 차단 + 토스트 "아직 승인되지 않은 지시서입니다" |
| `approved` / `in_progress` | 작업 가능 |
| `received` / `completed` / `partial` | 조회 전용 (입력 비활성) |
| `cancelled` | 진입 차단 |

→ 데이터 오염 및 잘못된 처리로 인한 재작업 비용 원천 차단.

### 6.4 Optimistic Update

작업 완료 직후 백엔드 응답을 기다리지 않고 로컬 상태를 먼저 갱신:

```js
await receiveInbound(currentOrder.id, rows);
// 백엔드가 status 전환을 늦게 반영해도 즉시 'received'로 표시
setOrderList(prev => prev.map(o =>
  o.id === submittedId ? { ...o, status: 'received' } : o
));
```

→ 사용자 체감 응답 속도 향상 + 백엔드 일관성 이슈 회피.

---

## 7. 안드로이드 시스템 통합

### 7.1 `app.json` 설정

```json
{
  "android": {
    "package": "com.dldmsrud.be23fin3teamWBSfeapp",
    "edgeToEdgeEnabled": false,
    "permissions": [
      "android.permission.CAMERA",
      "android.permission.RECORD_AUDIO"
    ]
  },
  "plugins": [
    "expo-font",
    ["expo-navigation-bar", {
      "position": "relative",
      "behavior": "inset-touch",
      "backgroundColor": "#141E30"
    }],
    ["expo-camera", {
      "cameraPermission": "QR/바코드 스캔을 위해 카메라 권한이 필요합니다."
    }]
  ]
}
```

### 7.2 시스템 네비바 동적 제어 (`App.js`)

```js
useEffect(() => {
  if (Platform.OS !== 'android') return;
  const applyNavBar = async () => {
    await NavigationBar.setPositionAsync('relative');
    await NavigationBar.setBehaviorAsync('inset-touch');
    await NavigationBar.setBackgroundColorAsync('#141E30');
  };
  applyNavBar();
  // 백그라운드 복귀 시마다 재적용 (카메라 사용 후 OS가 설정 초기화함)
  const sub = AppState.addEventListener('change', state => {
    if (state === 'active') applyNavBar();
  });
  return () => sub.remove();
}, []);
```

→ 카메라 풀스크린 → 앱 복귀 시 시스템 바가 깨지는 안드로이드 이슈 해결.

---

## 8. 빌드 / 배포

### 8.1 EAS 빌드 프로필 (`eas.json`)

```json
{
  "build": {
    "development": { "developmentClient": true, "distribution": "internal" },
    "preview":     { "distribution": "internal" },
    "production":  { "autoIncrement": true }
  }
}
```

| 프로필 | 용도 | 결과물 |
|---|---|---|
| `development` | 개발용 (Dev Client 포함) | APK + 디버그 도구 |
| `preview` | 사내 테스트/실배포 | APK (직접 설치) |
| `production` | 스토어 출시 (예정) | AAB |

### 8.2 빌드 명령

```bash
# 빌드 (10~25분)
eas build -p android --profile preview

# 최신 빌드를 연결된 폰/에뮬레이터에 자동 설치
eas build:run -p android --latest
```

### 8.3 빌드 흐름 상세

| Step | 동작 | 위치 | 소요 시간 |
|---|---|---|---|
| 1 | 프로젝트 압축 후 EAS Cloud로 업로드 | PC → EAS | 30초~2분 |
| 2 | 큐 대기 (무료 플랜) | EAS Cloud | 0~15분 |
| 3 | `npm ci` 의존성 설치 | 빌드 머신 | 2~3분 |
| 4 | `expo prebuild` (android/ 폴더 자동 생성) | 빌드 머신 | 1~2분 |
| 5 | Metro 번들링 (`index.android.bundle`) | 빌드 머신 | 1~3분 |
| 6 | Gradle 빌드 (`assembleRelease`) | 빌드 머신 | 8~15분 |
| 7 | APK 서명 (EAS 관리 키스토어) | 빌드 머신 | 30초 |
| 8 | S3 업로드 + 다운로드 URL 발급 | EAS Cloud | 30초~1분 |

### 8.4 APK 내부 구조

```
app-release.apk (zip)
├── AndroidManifest.xml          # 권한·액티비티 (Expo 자동 생성)
├── classes.dex                  # Java/Kotlin 컴파일 결과
├── lib/                         # 네이티브 라이브러리
│   └── arm64-v8a/
│       ├── libreactnativejni.so # React Native 엔진
│       ├── libhermes.so         # JS 엔진
│       └── libexpo-camera.so    # 카메라 네이티브 모듈
├── assets/
│   ├── index.android.bundle     # 🎯 React Native JS 코드 전체
│   ├── fonts/                   # 커스텀 폰트
│   └── images/                  # 이미지 자산
└── META-INF/                    # 서명 정보
```

---

## 9. 운영 환경 구성

| 환경 | 백엔드 URL | 빌드 방식 | 배포 방식 |
|---|---|---|---|
| 로컬 개발 | `http://{Metro호스트}:8080` | Expo Go (빌드 없음) | QR 스캔으로 즉시 반영 (Hot Reload) |
| 사내 테스트 | `https://server.wbs.asia` | EAS Build `preview` | APK 직접 설치 |
| 운영 (예정) | `https://server.wbs.asia` | EAS Build `production` | Play Console 또는 사내 |

---

## 10. 비기능 요구사항 매핑

| NFR 항목 | 적용 기술 |
|---|---|
| **확장성(Scalability)** | 단일 코드베이스로 iOS·Android 동시 지원 가능 (현재는 Android만 빌드) |
| **가용성(Availability)** | 백엔드 의존이므로 백엔드 가용성 = 앱 가용성. APK는 한 번 설치되면 호스팅 의존 0 |
| **성능(Performance)** | Hermes 엔진 + Metro 최적화로 콜드 스타트 2초 이내 |
| **보안(Security)** | HTTPS + JWT + APK 키스토어 클라우드 관리. 401 자동 로그아웃 |
| **관측 가능성(Observability)** | 개발: `console.log` → Metro / 운영: `adb logcat` 또는 Sentry(예정) |
| **유지보수성(Maintainability)** | `useState` 기반 단순 패턴, 화면당 평균 500줄 이하 |
| **추적 가능성(Traceability)** | 백엔드의 `@AuditLog`로 모든 모바일 API 호출이 ES 인덱스에 기록됨 |

---

## 11. 보안

| 항목 | 적용 |
|---|---|
| 통신 암호화 | HTTPS (TLS 1.2+) 강제, AWS ACM 인증서 |
| 인증 토큰 | JWT Bearer (HS512) — Gateway에서 검증 후 `X-User-Id`/`X-Client-Id` 헤더 주입 |
| 401 자동 처리 | axios Interceptor → 자동 로그아웃 |
| 멀티테넌시 격리 | JWT의 `clientId` 클레임을 백엔드가 모든 쿼리에 강제 적용 |
| 권한 체크 | 백엔드의 `@CheckPermission(Resource, Action)` AOP가 처리 (앱 측 권한 분기 없음) |
| 카메라 권한 | OS 권한 요청 + 거부 시 텍스트 입력 fallback |
| Android 9+ HTTPS 강제 | 평문 HTTP 차단 정책 준수 (`https://server.wbs.asia`) |
| APK 서명 | EAS가 관리하는 키스토어 (분실 위험 0, 업데이트 호환 보장) |

---

## 12. 버전 매트릭스 (요약)

```
React Native                 0.81.5
React                        19.1.0
Expo SDK                     54.0.34
expo-camera                  17.0.10
expo-navigation-bar          5.0.10
expo-status-bar              3.0.9
expo-font                    14.0.11
axios                        1.7.0
react-native-svg             15.12.1
EAS CLI                      ≥ 18.11.0
Hermes Engine                (Expo SDK 내장)
Metro Bundler                (Expo SDK 내장)
Android SDK (EAS)            API 34
Java                         17 (EAS 빌드 머신)
Gradle                       8.x (EAS 빌드 머신)
```

---

## 주요 파일 위치

| 파일 | 위치 |
|---|---|
| 앱 진입점 | `App.js` |
| Expo 설정 | `app.json` |
| EAS 빌드 프로필 | `eas.json` |
| 의존성 명세 | `package.json` |
| HTTP 클라이언트 | `src/api/client.js` |
| 화면 컴포넌트 | `src/screens/*.js` |
| 공용 컴포넌트 | `src/components/{QrScanner,DefectPhotoCapture}.js` |

---

**문서 끝** — 본 명세서는 `package.json`, `app.json`, `eas.json`, `src/` 코드 베이스를 기반으로 작성되었으며, 변경 시 갱신이 필요하다.
