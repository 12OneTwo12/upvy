# Firebase Analytics Integration

Upvy 앱에서 사용자 행동 분석과 다운로드 추적을 위한 Firebase Analytics 통합 가이드입니다.

## 📊 개요

Firebase Analytics를 사용하여 다음을 추적합니다:
- 📱 앱 설치 및 다운로드
- 👤 사용자 행동 및 인게이지먼트
- 🎯 전환 및 리텐션
- 📈 비즈니스 메트릭 (DAU, MAU, 세션 시간 등)

## 🏗️ 아키텍처 (Best Practices)

### 관심사의 분리 (Separation of Concerns)

```
┌─────────────────────────────────────────┐
│           UI Layer (Components)         │
│  - 사용자 인터랙션 처리                    │
│  - Custom Hooks 사용                     │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│      Business Logic (Custom Hooks)      │
│  - React Query mutation/query            │
│  - Analytics 로깅 (onSuccess)            │
│  - 캐시 무효화                            │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│        API Layer (API Functions)        │
│  - 순수 HTTP 통신만 담당                  │
│  - Analytics 로직 없음 ✅                │
└──────────────┬──────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────┐
│    Analytics Service (Fire-and-Forget)  │
│  - 비동기 로깅 (await 없음)               │
│  - 에러 무시 (앱 동작에 영향 없음)          │
└─────────────────────────────────────────┘
```

### ✅ 올바른 구현 (Best Practice)

```typescript
// ✅ Custom Hook: 비즈니스 로직 + Analytics
import { useMutation } from '@tanstack/react-query';
import { createLike } from '@/api/like.api';
import { Analytics } from '@/utils/analytics';

export const useLike = (contentId: string) => {
  const mutation = useMutation({
    mutationFn: () => createLike(contentId),
    onSuccess: () => {
      Analytics.logLike(contentId, 'video'); // ✅ No await
    },
  });

  return { like: mutation.mutate, isLiking: mutation.isPending };
};

// ✅ API: 순수 HTTP 통신만
export const createLike = async (contentId: string) => {
  const response = await apiClient.post(...);
  return response.data; // ✅ Analytics 호출 없음
};

// ✅ Component: Hook 사용
const { like, isLiking } = useLike(contentId);
<Button onPress={like} loading={isLiking} />
```

### ❌ 잘못된 구현 (Anti-Pattern)

```typescript
// ❌ API 레이어에서 Analytics 호출
export const createLike = async (contentId: string) => {
  const response = await apiClient.post(...);
  await Analytics.logLike(...); // ❌ 관심사 분리 위반
  return response.data;
};

// ❌ await 사용 (성능 저하 50-100ms)
Analytics.logLike(...); // ✅ 올바름
await Analytics.logLike(...); // ❌ 성능 저하
```

## 🚀 설치 및 설정

### 1. Firebase 프로젝트 설정

Firebase Console에서 다음 파일을 다운로드하여 프로젝트 루트에 배치:
- `google-services.json` (Android)
- `GoogleService-Info.plist` (iOS)

### 2. 패키지 설치

```bash
npm install @react-native-firebase/app @react-native-firebase/analytics
```

### 3. app.config.js 설정

```javascript
module.exports = {
  expo: {
    plugins: [
      '@react-native-firebase/app',
      // ... other plugins
    ],
    extra: {
      firebaseAnalyticsEnabled: process.env.FIREBASE_ANALYTICS_ENABLED !== 'false',
      environment: process.env.APP_ENV || 'development',
    },
  },
};
```

### 4. Analytics 초기화 (App.tsx)

```typescript
import { Analytics } from '@/utils/analytics';

// 앱 시작 시 초기화
Analytics.initialize();
```

## 📝 사용법

### 1. 화면 추적 (자동)

React Navigation과 통합되어 화면 전환이 자동으로 추적됩니다 (RootNavigator.tsx).

### 2. Custom Hooks (권장)

#### useLike - 좋아요/좋아요 취소

```typescript
import { useLike } from '@/hooks/useLike';

const MyComponent = ({ contentId }: { contentId: string }) => {
  const { like, unlike, isLiking } = useLike(contentId);

  return (
    <Button
      onPress={isLiked ? unlike : like}
      loading={isLiking}
    >
      {isLiked ? '좋아요 취소' : '좋아요'}
    </Button>
  );
};
```

#### useSave - 저장/저장 취소

```typescript
import { useSave } from '@/hooks/useSave';

const { save, unsave, isSaving } = useSave(contentId);

<Button onPress={isSaved ? unsave : save} loading={isSaving} />
```

#### useFollow - 팔로우/언팔로우

```typescript
import { useFollow } from '@/hooks/useFollow';

const { follow, unfollow, isFollowing: isLoading } = useFollow(userId);

<Button onPress={isFollowed ? unfollow : follow} loading={isLoading} />
```

#### useCreateComment - 댓글 작성

```typescript
import { useCreateComment } from '@/hooks/useCreateComment';

const { comment, isCommenting } = useCreateComment(contentId, {
  onSuccess: () => {
    console.log('댓글 작성 완료!');
  },
});

<Button
  onPress={() => comment({ content: '댓글 내용' })}
  loading={isCommenting}
/>
```

### 3. 검색 (Analytics 포함)

```typescript
import { searchContentsWithAnalytics } from '@/api/searchWithAnalytics';

// React Query와 함께 사용
const { data } = useQuery({
  queryKey: ['search', query],
  queryFn: () => searchContentsWithAnalytics({ q: query }),
});
```

### 4. 직접 이벤트 로깅 (고급)

#### 인증 이벤트

```typescript
// 로그인
Analytics.logLogin('google' | 'apple' | 'email');

// 회원가입
Analytics.logSignUp('google' | 'apple' | 'email');

// 로그아웃
Analytics.logLogout();

// 사용자 ID 설정
Analytics.setUserId(userId);
```

#### 콘텐츠 상호작용

```typescript
// 콘텐츠 조회
Analytics.logContentView(contentId, 'video', {
  category: 'programming',
  creatorId: 'user123',
  duration: 60,
});

// 좋아요/좋아요 취소
Analytics.logLike(contentId, 'video', creatorId);
Analytics.logUnlike(contentId, 'video');

// 저장/저장 취소
Analytics.logSave(contentId, 'video');
Analytics.logUnsave(contentId, 'video');

// 댓글
Analytics.logComment(contentId, commentLength);

// 공유
Analytics.logShare(contentId, 'video', 'link' | 'social' | 'message');
```

#### 사용자 행동

```typescript
// 검색
Analytics.logSearch(searchTerm, resultCount, category);

// 프로필 조회
Analytics.logProfileView(userId, isSelf);

// 팔로우/언팔로우
Analytics.logFollow(userId);
Analytics.logUnfollow(userId);
```

#### 콘텐츠 업로드

```typescript
// 업로드 시작
Analytics.logContentUploadStart('video', 'programming');

// 업로드 완료
Analytics.logContentUploadComplete('video', 'programming', {
  duration: 120,
  fileSize: 1024000,
});

// 업로드 실패
Analytics.logContentUploadFailed('Network error');
```

#### 비디오 재생

```typescript
// 재생
Analytics.logVideoPlay(contentId, position, duration);

// 일시정지
Analytics.logVideoPause(contentId, position);

// 완료
Analytics.logVideoComplete(contentId, watchTime, duration);
```

#### 에러 추적

```typescript
Analytics.logError('Error message', {
  errorCode: 'AUTH_001',
  screenName: 'Login',
  fatal: false,
});
```

#### 사용자 속성 설정

```typescript
Analytics.setUserProperties({
  account_type: 'premium',
  preferred_language: 'ko',
  content_preference: ['programming', 'design'],
  total_followers: 100,
});
```

## 🎯 자동 추적 이벤트

다음 이벤트는 **Custom Hooks** 또는 **기존 useMutation onSuccess 콜백**을 통해 자동으로 추적됩니다:

### 인증 흐름
- ✅ 로그인/회원가입 (Google, Apple) - `useGoogleAuth.ts`, `useAppleAuth.ts`
- ✅ 로그아웃 - `authStore.ts`
- ✅ 사용자 ID 설정 - 자동

### 콘텐츠 상호작용
- ✅ 좋아요/좋아요 취소 - `ContentViewerScreen.tsx` (onSuccess 콜백)
- ✅ 저장/저장 취소 - `ContentViewerScreen.tsx` (onSuccess 콜백)
- ✅ 댓글 작성 - `CommentModal.tsx` (onSuccess 콜백)
- ✅ 공유 - `ContentViewerScreen.tsx` (onSuccess 콜백)

### 사용자 행동
- ✅ 팔로우/언팔로우 - `ContentViewerScreen.tsx` (onSuccess 콜백)
- ✅ 검색 - `searchContentsWithAnalytics()` / `searchUsersWithAnalytics()`

### 화면 추적
- ✅ 모든 화면 전환 - `RootNavigator.tsx` (자동)

## 📦 파일 구조

```
src/
├── api/
│   ├── like.api.ts                    # 순수 HTTP 통신 (Analytics 없음)
│   ├── save.api.ts                    # 순수 HTTP 통신 (Analytics 없음)
│   ├── follow.api.ts                  # 순수 HTTP 통신 (Analytics 없음)
│   ├── comment.api.ts                 # 순수 HTTP 통신 (Analytics 없음)
│   ├── search.api.ts                  # 순수 HTTP 통신 (Analytics 없음)
│   └── searchWithAnalytics.ts         # 검색 API Analytics wrapper
├── hooks/
│   ├── useLike.ts                     # Like Hook (Analytics 포함)
│   ├── useSave.ts                     # Save Hook (Analytics 포함)
│   ├── useFollow.ts                   # Follow Hook (Analytics 포함)
│   ├── useCreateComment.ts            # Comment Hook (Analytics 포함)
│   ├── useGoogleAuth.ts               # Google Auth (Analytics 포함)
│   └── useAppleAuth.ts                # Apple Auth (Analytics 포함)
├── stores/
│   └── authStore.ts                   # Auth Store (Analytics 포함)
├── screens/
│   └── content/
│       └── ContentViewerScreen.tsx    # Analytics in onSuccess callbacks
├── components/
│   └── comment/
│       └── CommentModal.tsx           # Analytics in onSuccess callbacks
├── utils/analytics/
│   ├── Analytics.ts                   # Analytics 서비스 (Fire-and-Forget)
│   ├── types.ts                       # 타입 정의 및 이벤트 상수
│   ├── index.ts                       # Export 모듈
│   └── README.md                      # 이 문서
└── navigation/
    └── RootNavigator.tsx              # 화면 추적 (Analytics 포함)
```

## 🔍 디버깅

### Firebase DebugView 활성화

**iOS:**
1. Xcode에서 Scheme 편집
2. Arguments Passed On Launch에 추가:
   ```
   -FIRAnalyticsDebugEnabled
   ```

**Android:**
```bash
adb shell setprop debug.firebase.analytics.app com.upvy.app
```

### 콘솔 로그

개발 모드(`__DEV__`)에서는 모든 Analytics 이벤트가 콘솔에 로그됩니다:

```
[Analytics] Content view: { content_id: '123', content_type: 'video', category: 'programming' }
[Analytics] Like: { content_id: '123', content_type: 'video' }
```

## 📊 Firebase Console

분석 데이터 확인:
1. [Firebase Console](https://console.firebase.google.com/) 접속
2. 프로젝트 선택
3. Analytics > 대시보드

### 주요 메트릭

- **사용자**: DAU, MAU, 신규 사용자
- **참여도**: 세션 시간, 화면별 조회수
- **전환**: 회원가입, 콘텐츠 업로드
- **이벤트**: 모든 커스텀 이벤트

## 🎨 이벤트 명명 규칙

Firebase Analytics 제약사항:
- 최대 500개의 고유 이벤트 타입
- 이벤트당 최대 25개 파라미터
- 파라미터 값은 100자 제한

### 네이밍 컨벤션

- **이벤트**: `snake_case` (예: `content_view`, `sign_up`)
- **파라미터**: `snake_case` (예: `content_id`, `search_term`)
- **파라미터 값**: 가능한 짧게 (예: `'google'`)

## 🔐 개인정보 보호

### GDPR/개인정보보호법 준수

1. **사용자 동의**: 사용자에게 분석 데이터 수집 동의 받기 (향후 구현 필요)
2. **데이터 최소화**: 필요한 데이터만 수집
3. **익명화**: 개인 식별 정보(PII) 수집 금지

### iOS App Tracking Transparency (ATT)

iOS 14.5 이상에서는 앱 추적 투명성 프레임워크 준수 필요 (향후 구현 예정)

## ⚠️ 주의사항

1. **프로덕션에서만 활성화**: 개발 환경에서는 자동으로 비활성화됨
2. **에러 처리**: Analytics 실패가 앱 기능에 영향을 주지 않도록 모든 메서드에서 try-catch 처리
3. **성능**: Analytics 호출은 비동기이며 UI를 블로킹하지 않음
4. **데이터 반영**: Firebase Console에 데이터가 나타나기까지 최대 24시간 소요
5. **Fire-and-Forget**: Analytics 메서드는 절대 await 없이 호출 (성능 최적화)

## 🛠️ 개발 가이드

### 새 이벤트 추가

1. **타입 정의** (`types.ts`):
```typescript
export const AnalyticsEvents = {
  MY_NEW_EVENT: 'my_new_event',
};

export interface MyNewEventParams {
  param1: string;
  param2: number;
}
```

2. **Analytics 메서드 추가** (`Analytics.ts`):
```typescript
logMyNewEvent(param1: string, param2: number): void {
  if (!this.enabled) return;

  try {
    const params: MyNewEventParams = { param1, param2 };
    analytics().logEvent(AnalyticsEvents.MY_NEW_EVENT, params);

    if (__DEV__) {
      console.log('[Analytics] My new event:', params);
    }
  } catch (error) {
    console.error('[Analytics] Failed to log my new event:', error);
  }
}
```

3. **사용**:
```typescript
Analytics.logMyNewEvent('value1', 123);
```

## 📚 참고 자료

- [Firebase Analytics for React Native](https://rnfirebase.io/analytics/usage)
- [Google Analytics 4 Events](https://developers.google.com/analytics/devguides/collection/ga4/reference/events)
- [Firebase Best Practices](https://firebase.google.com/docs/analytics/best-practices)
- [React Navigation Screen Tracking](https://reactnavigation.org/docs/screen-tracking/)

## 🤝 기여

Analytics 이벤트 추가나 개선 사항이 있다면 GitHub Issue를 생성해주세요.

## 📄 라이선스

이 프로젝트는 Upvy의 일부입니다.
