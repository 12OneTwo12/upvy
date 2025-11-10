# GrowSnap Frontend

> React Native (Expo) 기반 크로스 플랫폼 모바일 앱

## 📱 기술 스택

- **프레임워크**: Expo SDK 54
- **언어**: TypeScript
- **네비게이션**: React Navigation v6
- **상태 관리**:
  - React Query (서버 상태)
  - Zustand (클라이언트 상태)
- **HTTP 클라이언트**: Axios
- **스타일링**: NativeWind (Tailwind CSS)
- **애니메이션**: React Native Reanimated
- **인증**: Expo Auth Session (Google OAuth)

---

## 🚀 시작하기

### 사전 요구사항

```bash
Node.js >= 18
npm >= 9
Xcode >= 15 (iOS 개발용)
CocoaPods (iOS)
```

### 설치

```bash
cd grow-snap-frontend

# 의존성 설치 (네이티브 모듈 자동 등록 포함)
npm install --legacy-peer-deps

# iOS: CocoaPods 의존성 설치
cd ios && pod install && cd ..
```

### 개발 서버 실행

```bash
# Expo 개발 서버 시작
npm start

# iOS 시뮬레이터 (권장)
npm run ios

# Android 에뮬레이터
npm run android

# 웹 브라우저
npm run web
```

### ⚠️ 중요: 비디오 트리밍 기능 설정

이 프로젝트는 **커스텀 네이티브 모듈**을 사용합니다. 자세한 설정 방법은:

📖 **[비디오 트리밍 설정 가이드](./docs/VIDEO_TRIMMING_SETUP.md)** 참고

---

## 📁 프로젝트 구조

```
grow-snap-frontend/
├── App.tsx                 # 앱 진입점
├── app.json                # Expo 설정
├── babel.config.js         # Babel 설정 (NativeWind, Reanimated)
├── tailwind.config.js      # TailwindCSS 설정
├── tsconfig.json           # TypeScript 설정
├── src/
│   ├── api/                # API 클라이언트
│   │   └── client.ts       # Axios 인스턴스 (JWT 인터셉터)
│   ├── components/         # 재사용 컴포넌트
│   ├── constants/          # 상수
│   │   └── api.ts          # API URL & Endpoints
│   ├── hooks/              # 커스텀 훅
│   ├── navigation/         # 네비게이션 구조
│   │   ├── RootNavigator.tsx
│   │   ├── AuthNavigator.tsx
│   │   └── MainNavigator.tsx
│   ├── screens/            # 화면
│   │   ├── auth/          # 인증 (로그인)
│   │   ├── feed/          # 피드
│   │   ├── search/        # 검색
│   │   ├── upload/        # 업로드
│   │   └── profile/       # 프로필
│   ├── stores/             # Zustand 상태
│   ├── types/              # TypeScript 타입
│   └── utils/              # 유틸리티
│       └── storage.ts      # AsyncStorage 래퍼
└── assets/                 # 이미지, 폰트
```

---

## 🔧 환경 변수

`app.json`의 `extra` 필드에서 API URL을 설정합니다:

```json
{
  "expo": {
    "extra": {
      "apiUrl": "http://localhost:8080/api/v1"
    }
  }
}
```

---

## 🌐 백엔드 연동

### API 클라이언트 구조

```typescript
// src/api/client.ts
// JWT 토큰 자동 추가 & Refresh Token 자동 갱신
import apiClient from '@/api/client';

const response = await apiClient.get('/users/me');
```

### API Endpoints

모든 엔드포인트는 `src/constants/api.ts`에 정의되어 있습니다:

```typescript
import { API_ENDPOINTS } from '@/constants/api';

// 사용 예시
apiClient.get(API_ENDPOINTS.USER.ME);
apiClient.post(API_ENDPOINTS.FOLLOW.FOLLOW(userId));
```

---

## 📚 주요 라이브러리

| 라이브러리 | 버전 | 용도 |
|-----------|------|------|
| expo | ~54.0 | 프레임워크 |
| react-navigation | ^6.x | 네비게이션 |
| @tanstack/react-query | latest | 서버 상태 관리 |
| zustand | latest | 클라이언트 상태 |
| axios | latest | HTTP 클라이언트 |
| nativewind | latest | 스타일링 |
| react-native-reanimated | latest | 애니메이션 |
| expo-auth-session | latest | OAuth 인증 |

---

## 🧪 개발 가이드

### TypeScript 경로 Alias

`@/*`로 `src/` 폴더에 접근할 수 있습니다:

```typescript
import apiClient from '@/api/client';
import { API_ENDPOINTS } from '@/constants/api';
```

### 코드 품질

```bash
# ESLint
npm run lint

# TypeScript 타입 체크
npx tsc --noEmit

# Prettier 포맷팅
npm run format
```

---

## 🔗 관련 링크

- [백엔드 저장소](../grow-snap-backend)
- [요구사항 명세서](../docs/요구사항명세서.md)
- [Expo 문서](https://docs.expo.dev/)
- [React Navigation 문서](https://reactnavigation.org/)

---

## 📝 다음 작업

- [ ] Issue #15: Google OAuth 로그인 구현
- [ ] Issue #16: 프로필 관리 UI 구현
- [ ] Issue #17: 스마트 피드 UI 구현
- [ ] Issue #18: 인터랙션 UI 구현

---

**GrowSnap - 스크롤 시간을 성장 시간으로** 🌱
