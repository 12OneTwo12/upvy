---
description: GrowSnap 개발 가이드 및 코딩 컨벤션
---

# GrowSnap Development Guide

당신은 GrowSnap 프로젝트의 개발 가이드를 따라 코드를 작성합니다.

## Expo SDK 관리 규칙 (중요!)

### ⚠️ 패키지 설치 원칙

**절대 규칙: Expo SDK 호환성을 항상 유지해야 합니다!**

1. **Expo 제공 패키지 우선 사용**
   - Expo가 제공하는 패키지가 있으면 반드시 그것을 사용
   - 예: `expo-auth-session`, `expo-web-browser`, `expo-crypto`

2. **npm/yarn 직접 설치 금지**
   - ❌ `npm install react-native-reanimated`
   - ✅ `npx expo install react-native-reanimated`
   - Expo CLI가 SDK 버전에 맞는 호환 버전을 자동으로 선택

3. **호환성 문제 발생 시**
   ```bash
   # 1. 호환성 체크
   npx expo-doctor

   # 2. 자동 수정
   npx expo install --fix

   # 3. 깨끗한 재설치
   rm -rf node_modules package-lock.json
   npm install --legacy-peer-deps
   ```

4. **react-native-reanimated 같은 네이티브 라이브러리**
   - Expo SDK 54 기준으로 일부 라이브러리는 선택적
   - 애니메이션이 필수가 아니면 제거하는 것이 안정적
   - 필요하면 반드시 `npx expo install`로 설치

5. **package.json 수동 편집 시**
   - 버전은 `~x.x.x` 형태 사용 (틸드: 마이너 버전 고정)
   - 수동 편집 후 반드시 `npx expo install --check` 실행

### 현재 프로젝트 상태
- Expo SDK: 54.0.20
- React Native: 0.81.5
- React: 19.1.0
- **react-native-reanimated 제거됨** (빌드 이슈로 인해)
- NativeWind 사용 (Tailwind CSS)

## 프로젝트 구조

```
src/
├── theme/              # 디자인 토큰
├── utils/              # 유틸리티 (responsive, errorHandler, storage)
├── components/common/  # 공통 컴포넌트 (Button, Input, etc)
├── screens/            # 화면 컴포넌트
├── navigation/         # 네비게이션
├── hooks/              # 커스텀 훅
├── stores/             # Zustand 상태 관리
├── api/                # API 클라이언트
├── types/              # TypeScript 타입
└── constants/          # 상수
```

## 코딩 컨벤션

### TypeScript
- `any` 사용 금지
- 모든 함수에 타입 정의 필수
- Props는 interface로 정의
- Enum 대신 Union 타입 사용

### 파일 명명
- 컴포넌트: `PascalCase.tsx`
- 유틸리티: `camelCase.ts`
- 타입: `*.types.ts`
- 테스트: `*.test.ts(x)`

### 컴포넌트 구조

```typescript
// 1. Imports
import React from 'react';
import { View, StyleSheet } from 'react-native';
import { theme } from '@/theme';

// 2. Types
interface MyComponentProps {
  title: string;
}

// 3. Component
export const MyComponent: React.FC<MyComponentProps> = ({ title }) => {
  return <View style={styles.container}>{/* ... */}</View>;
};

// 4. Styles (always use StyleSheet)
const styles = StyleSheet.create({
  container: {
    padding: theme.spacing[4],
  },
});
```

## 스타일링 규칙

### ✅ 올바른 방법
```typescript
// Theme 사용
backgroundColor: theme.colors.primary[500]
fontSize: theme.typography.fontSize.lg
padding: theme.spacing[4]

// 반응형
fontSize: responsive({ xs: 24, md: 28, default: 28 })
width: scaleWidth(100)
```

### ❌ 잘못된 방법
```typescript
// 하드코딩
backgroundColor: '#22c55e'
fontSize: 18
padding: 16

// 인라인 스타일
<View style={{ padding: 16 }}>
```

## API 통신 패턴

### 기본 사용
```typescript
import apiClient from '@/api/client';
import { API_ENDPOINTS } from '@/constants/api';

const user = await apiClient.get<User>(API_ENDPOINTS.USER.ME);
```

### 에러 핸들링
```typescript
import { withErrorHandling, showErrorAlert } from '@/utils/errorHandler';

// 자동 에러 처리
const result = await withErrorHandling(
  async () => await apiCall(),
  {
    showAlert: true,
    alertTitle: 'API 호출 실패',
    logContext: 'ComponentName.functionName',
  }
);

// 수동 에러 처리
try {
  await apiCall();
} catch (error) {
  showErrorAlert(error, '실패 제목');
}
```

## 상태 관리

### Zustand Store
```typescript
export const useMyStore = create<MyStore>((set, get) => ({
  // State
  data: null,

  // Actions
  loadData: async () => {
    const data = await fetchData();
    set({ data });
  },
}));
```

### React Query (서버 상태)
```typescript
const { data, isLoading } = useQuery({
  queryKey: ['key'],
  queryFn: fetchData,
});
```

## 에러 처리

### ErrorBoundary
모든 주요 섹션에 ErrorBoundary 적용:
```typescript
<ErrorBoundary onError={(e) => logError(e, 'Context')}>
  <YourComponent />
</ErrorBoundary>
```

### Try-Catch 패턴
```typescript
try {
  setIsLoading(true);
  const result = await apiCall();
  setData(result);
} catch (error) {
  showErrorAlert(error, '작업 실패');
} finally {
  setIsLoading(false);
}
```

## 테스트 작성

```typescript
import { render, fireEvent } from '@testing-library/react-native';

describe('MyComponent', () => {
  it('should render', () => {
    const { getByText } = render(<MyComponent title="Test" />);
    expect(getByText('Test')).toBeTruthy();
  });

  it('should call handler', () => {
    const onPress = jest.fn();
    const { getByText } = render(<Button onPress={onPress}>Click</Button>);
    fireEvent.press(getByText('Click'));
    expect(onPress).toHaveBeenCalled();
  });
});
```

## 성능 최적화

### 리스트
```typescript
<FlatList
  data={items}
  keyExtractor={(item) => item.id}
  renderItem={({ item }) => <Item item={item} />}
  windowSize={10}
  maxToRenderPerBatch={10}
/>
```

### 메모이제이션
```typescript
const value = useMemo(() => compute(data), [data]);
const handler = useCallback(() => action(), []);
```

## 보안

### 토큰 저장
```typescript
import { setAccessToken, getAccessToken } from '@/utils/storage';

// AsyncStorage 사용 (암호화됨)
await setAccessToken(token);
const token = await getAccessToken();
```

### 민감 정보
- 프로덕션에서 토큰 로그 금지
- API 키는 환경 변수 사용
- HTTPS만 사용

## 커밋 메시지

```
feat: 새 기능 추가
fix: 버그 수정
refactor: 리팩토링
style: 스타일 변경
docs: 문서 수정
test: 테스트 추가/수정
chore: 빌드/설정 변경
```

## 유용한 명령어

```bash
npm start              # 개발 서버
npm run android        # Android 실행
npm test               # 테스트
npx tsc --noEmit       # 타입 체크 (필수!)
```

## 체크리스트

코드 작성 후 반드시 확인:
- [ ] TypeScript 에러 없음 (`npx tsc --noEmit`)
- [ ] Theme 사용 (하드코딩 없음)
- [ ] 반응형 적용
- [ ] 에러 핸들링 있음
- [ ] 컴포넌트 Props 타입 정의
- [ ] 인라인 스타일 없음 (StyleSheet 사용)
