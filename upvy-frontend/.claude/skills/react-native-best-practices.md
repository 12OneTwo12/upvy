# React Native + Expo 베스트 프랙티스 가이드 (2025)

이 문서는 Expo를 사용하는 React Native 프로젝트의 아키텍처, 구조, 컨벤션, 그리고 베스트 프랙티스를 정의합니다.

---

## 1. 프로젝트 구조 (Project Structure)

### 권장 폴더 구조

```
upvy-frontend/
├── src/
│   ├── api/              # API 클라이언트 및 엔드포인트
│   │   ├── client.ts     # Axios 인스턴스 설정
│   │   └── *.api.ts      # 기능별 API 함수 (auth.api.ts, user.api.ts)
│   │
│   ├── components/       # 재사용 가능한 컴포넌트
│   │   ├── common/       # 공통 UI 컴포넌트 (Button, Input, Modal)
│   │   ├── feature/      # 기능별 컴포넌트 (profile/, feed/, upload/)
│   │   └── index.ts      # 배럴 익스포트
│   │
│   ├── constants/        # 상수 정의
│   │   ├── api.ts        # API 엔드포인트
│   │   ├── colors.ts     # 색상 상수
│   │   └── config.ts     # 앱 설정
│   │
│   ├── hooks/            # 커스텀 React Hooks
│   │   ├── useAuth.ts
│   │   ├── useInfiniteScroll.ts
│   │   └── useDebounce.ts
│   │
│   ├── navigation/       # 네비게이션 설정
│   │   ├── RootNavigator.tsx
│   │   ├── AuthNavigator.tsx
│   │   └── MainNavigator.tsx
│   │
│   ├── screens/          # 화면 컴포넌트
│   │   ├── auth/         # 인증 화면
│   │   ├── profile/      # 프로필 화면
│   │   └── feed/         # 피드 화면
│   │
│   ├── services/         # 비즈니스 로직 (선택사항)
│   │   ├── analytics.ts
│   │   └── notification.ts
│   │
│   ├── stores/           # 상태 관리 (Zustand)
│   │   ├── authStore.ts
│   │   └── uiStore.ts
│   │
│   ├── theme/            # 디자인 시스템
│   │   ├── index.ts
│   │   ├── colors.ts
│   │   └── typography.ts
│   │
│   ├── types/            # TypeScript 타입 정의
│   │   ├── auth.types.ts
│   │   ├── navigation.types.ts
│   │   └── api.types.ts
│   │
│   └── utils/            # 유틸리티 함수
│       ├── storage.ts    # AsyncStorage 래퍼
│       ├── validation.ts # 검증 함수
│       └── formatting.ts # 포맷팅 함수
│
├── assets/               # 정적 리소스
│   ├── images/
│   ├── fonts/
│   └── icons/
│
├── __tests__/            # 테스트 파일
│   ├── components/
│   └── utils/
│
├── app.json              # Expo 설정
├── package.json
└── tsconfig.json
```

### 구조화 원칙

1. **기능별 분리 (Feature-Based)**: 큰 프로젝트에서는 기능별로 폴더를 구성
2. **Atomic Design**: components를 atoms → molecules → organisms로 구조화
3. **배럴 익스포트**: index.ts를 사용하여 깔끔한 import 경로 유지

```typescript
// ❌ Bad
import { Button } from '../../../components/common/Button';
import { Input } from '../../../components/common/Input';

// ✅ Good
import { Button, Input } from '@/components/common';
```

---

## 2. 아키텍처 패턴 (Architecture Patterns)

### 권장 아키텍처: **MVVM + Container/Presentational**

#### MVVM (Model-View-ViewModel)
- **Model**: API 호출, 데이터 관리 (api/, stores/)
- **View**: UI 컴포넌트 (components/, screens/)
- **ViewModel**: 비즈니스 로직 (hooks/, stores/)

#### Container/Presentational 패턴

```typescript
// ❌ Bad: 로직과 UI가 섞임
export function UserProfile() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchUser().then(setUser).finally(() => setLoading(false));
  }, []);

  if (loading) return <ActivityIndicator />;

  return (
    <View>
      <Text>{user.name}</Text>
      {/* UI 코드 */}
    </View>
  );
}

// ✅ Good: Container와 Presentational 분리
// Container (로직)
export function UserProfileContainer() {
  const { user, loading } = useUser();

  if (loading) return <LoadingSpinner />;

  return <UserProfileView user={user} />;
}

// Presentational (UI)
interface UserProfileViewProps {
  user: User;
}

export function UserProfileView({ user }: UserProfileViewProps) {
  return (
    <View>
      <Text>{user.name}</Text>
      {/* UI 코드만 */}
    </View>
  );
}
```

---

## 3. 상태 관리 (State Management)

### 계층화된 상태 관리 전략

```typescript
// 1. Server State: TanStack Query
import { useQuery, useMutation } from '@tanstack/react-query';

export function useUserProfile(userId: string) {
  return useQuery({
    queryKey: ['user', userId],
    queryFn: () => fetchUser(userId),
    staleTime: 5 * 60 * 1000, // 5분
  });
}

// 2. Global Client State: Zustand
import { create } from 'zustand';

interface AuthStore {
  user: User | null;
  isAuthenticated: boolean;
  login: (credentials: Credentials) => Promise<void>;
  logout: () => void;
}

export const useAuthStore = create<AuthStore>((set) => ({
  user: null,
  isAuthenticated: false,
  login: async (credentials) => {
    const user = await loginAPI(credentials);
    set({ user, isAuthenticated: true });
  },
  logout: () => set({ user: null, isAuthenticated: false }),
}));

// 3. Local Component State: useState
function MyComponent() {
  const [isOpen, setIsOpen] = useState(false);
  // 컴포넌트 내부에서만 사용되는 상태
}
```

### 상태 관리 가이드라인

| 상태 유형 | 도구 | 사용 예시 |
|----------|------|----------|
| **Server State** | TanStack Query | API 데이터, 캐싱, 동기화 |
| **Global Client State** | Zustand | 인증, 테마, 언어 설정 |
| **Local State** | useState/useReducer | 모달 상태, 폼 입력, 토글 |
| **Form State** | React Hook Form | 복잡한 폼 관리 |
| **URL State** | React Navigation | 네비게이션 파라미터 |

---

## 4. TypeScript 베스트 프랙티스

### 타입 정의

```typescript
// ✅ Good: 명확한 타입 정의
export interface User {
  id: string;
  name: string;
  email: string;
  profileImage?: string;
}

export interface ApiResponse<T> {
  data: T;
  message: string;
  status: number;
}

// ✅ Good: 유니온 타입 활용
export type UserRole = 'user' | 'creator' | 'admin';

// ✅ Good: Pick, Omit 활용
export type UserCreateInput = Omit<User, 'id'>;
export type UserUpdateInput = Partial<Pick<User, 'name' | 'profileImage'>>;
```

### API 클라이언트 타입 안전성

```typescript
// api/client.ts
import axios from 'axios';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
});

// api/user.api.ts
export async function fetchUser(userId: string): Promise<User> {
  const { data } = await apiClient.get<ApiResponse<User>>(`/users/${userId}`);
  return data.data;
}

export async function updateUser(
  userId: string,
  input: UserUpdateInput
): Promise<User> {
  const { data } = await apiClient.patch<ApiResponse<User>>(
    `/users/${userId}`,
    input
  );
  return data.data;
}
```

---

## 5. 성능 최적화 (Performance Optimization)

### 5.1 Hermes JavaScript Engine 활성화

Expo에서 Hermes는 기본적으로 활성화되어 있습니다. (Expo SDK 42+)

```json
// app.json
{
  "expo": {
    "jsEngine": "hermes"
  }
}
```

### 5.2 불필요한 리렌더링 방지

```typescript
// ✅ React.memo 사용
export const UserCard = React.memo<UserCardProps>(({ user }) => {
  return (
    <View>
      <Text>{user.name}</Text>
    </View>
  );
}, (prevProps, nextProps) => {
  // 커스텀 비교 함수 (선택)
  return prevProps.user.id === nextProps.user.id;
});

// ✅ useMemo로 비용이 큰 계산 메모이제이션
function FilteredList({ items, filter }) {
  const filteredItems = useMemo(() => {
    return items.filter(item => item.category === filter);
  }, [items, filter]); // items나 filter가 변경될 때만 재계산

  return <FlatList data={filteredItems} />;
}

// ✅ useCallback으로 함수 메모이제이션
function ParentComponent() {
  const handlePress = useCallback(() => {
    console.log('Pressed');
  }, []); // 의존성이 없으면 함수는 재생성되지 않음

  return <ChildComponent onPress={handlePress} />;
}
```

### 5.3 리스트 최적화

```typescript
// ✅ FlatList 최적화
<FlatList
  data={items}
  renderItem={({ item }) => <ItemComponent item={item} />}
  keyExtractor={(item) => item.id}

  // 성능 최적화 props
  removeClippedSubviews={true}
  maxToRenderPerBatch={10}
  updateCellsBatchingPeriod={50}
  initialNumToRender={10}
  windowSize={10}

  // getItemLayout으로 높이를 미리 계산 (고정 높이일 때)
  getItemLayout={(data, index) => ({
    length: ITEM_HEIGHT,
    offset: ITEM_HEIGHT * index,
    index,
  })}
/>

// 🚀 Better: FlashList 사용 (성능이 더 좋음)
import { FlashList } from '@shopify/flash-list';

<FlashList
  data={items}
  renderItem={({ item }) => <ItemComponent item={item} />}
  estimatedItemSize={100}
/>
```

### 5.4 이미지 최적화

```typescript
// ✅ Expo Image 사용 (react-native Image보다 빠름)
import { Image } from 'expo-image';

<Image
  source={{ uri: user.profileImage }}
  placeholder={require('../assets/placeholder.png')}
  contentFit="cover"
  transition={200}
  cachePolicy="memory-disk" // 캐싱 전략
/>
```

### 5.5 애니메이션 최적화

```typescript
// ✅ useNativeDriver 사용
import { Animated } from 'react-native';

const fadeAnim = useRef(new Animated.Value(0)).current;

Animated.timing(fadeAnim, {
  toValue: 1,
  duration: 300,
  useNativeDriver: true, // 네이티브 스레드에서 실행
}).start();

// 🚀 Better: react-native-reanimated 사용
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming
} from 'react-native-reanimated';

function MyComponent() {
  const opacity = useSharedValue(0);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
  }));

  useEffect(() => {
    opacity.value = withTiming(1, { duration: 300 });
  }, []);

  return <Animated.View style={animatedStyle} />;
}
```

---

## 6. 컴포넌트 설계 원칙

### 6.1 단일 책임 원칙 (Single Responsibility)

```typescript
// ❌ Bad: 하나의 컴포넌트가 너무 많은 역할
function UserDashboard() {
  const [user, setUser] = useState(null);
  const [posts, setPosts] = useState([]);
  const [followers, setFollowers] = useState([]);
  // ... 복잡한 로직

  return (
    <View>
      {/* 프로필 UI */}
      {/* 포스트 리스트 UI */}
      {/* 팔로워 리스트 UI */}
    </View>
  );
}

// ✅ Good: 책임을 분리
function UserDashboard() {
  return (
    <View>
      <UserProfile />
      <UserPosts />
      <UserFollowers />
    </View>
  );
}
```

### 6.2 Props Drilling 방지

```typescript
// ❌ Bad: Props Drilling
<GrandParent>
  <Parent theme={theme}>
    <Child theme={theme}>
      <GrandChild theme={theme} />
    </Child>
  </Parent>
</GrandParent>

// ✅ Good: Context 또는 Zustand 사용
const ThemeContext = createContext();

function App() {
  return (
    <ThemeContext.Provider value={theme}>
      <GrandParent>
        <Parent>
          <Child>
            <GrandChild />
          </Child>
        </Parent>
      </GrandParent>
    </ThemeContext.Provider>
  );
}

function GrandChild() {
  const theme = useContext(ThemeContext);
  // theme 사용
}
```

### 6.3 재사용 가능한 컴포넌트 설계

```typescript
// ✅ Good: 유연하고 재사용 가능한 Button 컴포넌트
interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'outline';
  size?: 'small' | 'medium' | 'large';
  loading?: boolean;
  disabled?: boolean;
  onPress: () => void;
  children: ReactNode;
}

export function Button({
  variant = 'primary',
  size = 'medium',
  loading = false,
  disabled = false,
  onPress,
  children,
}: ButtonProps) {
  const styles = getButtonStyles(variant, size);

  return (
    <TouchableOpacity
      style={[styles.button, disabled && styles.disabled]}
      onPress={onPress}
      disabled={disabled || loading}
    >
      {loading ? (
        <ActivityIndicator color={styles.text.color} />
      ) : (
        <Text style={styles.text}>{children}</Text>
      )}
    </TouchableOpacity>
  );
}
```

---

## 7. Hooks 베스트 프랙티스

### 7.1 커스텀 훅 네이밍

```typescript
// ✅ 항상 'use'로 시작
export function useDebounce<T>(value: T, delay: number): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const handler = setTimeout(() => setDebouncedValue(value), delay);
    return () => clearTimeout(handler);
  }, [value, delay]);

  return debouncedValue;
}
```

### 7.2 useEffect 의존성 관리

```typescript
// ❌ Bad: 의존성 배열 누락
useEffect(() => {
  fetchUser(userId);
}, []); // userId가 변경되어도 실행 안됨

// ✅ Good: 모든 의존성 포함
useEffect(() => {
  fetchUser(userId);
}, [userId]);

// ✅ Good: 의존성이 없으면 빈 배열
useEffect(() => {
  const subscription = subscribeToEvents();
  return () => subscription.unsubscribe();
}, []);
```

### 7.3 커스텀 훅으로 로직 재사용

```typescript
// ✅ API 호출 로직을 훅으로 추상화
export function useUser(userId: string) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;

    fetchUser(userId)
      .then((data) => {
        if (!cancelled) setUser(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => { cancelled = true; };
  }, [userId]);

  return { user, loading, error };
}

// 사용
function UserProfile({ userId }: { userId: string }) {
  const { user, loading, error } = useUser(userId);

  if (loading) return <LoadingSpinner />;
  if (error) return <ErrorMessage error={error} />;

  return <UserProfileView user={user} />;
}
```

---

## 8. 테스팅 (Testing)

### 8.1 단위 테스트

```typescript
// __tests__/components/Button.test.tsx
import { render, fireEvent } from '@testing-library/react-native';
import { Button } from '../Button';

describe('Button', () => {
  it('renders correctly', () => {
    const { getByText } = render(<Button onPress={() => {}}>Click me</Button>);
    expect(getByText('Click me')).toBeTruthy();
  });

  it('calls onPress when pressed', () => {
    const onPressMock = jest.fn();
    const { getByText } = render(<Button onPress={onPressMock}>Click me</Button>);

    fireEvent.press(getByText('Click me'));
    expect(onPressMock).toHaveBeenCalledTimes(1);
  });

  it('shows loading state', () => {
    const { getByTestId } = render(
      <Button loading onPress={() => {}}>Click me</Button>
    );
    expect(getByTestId('loading-spinner')).toBeTruthy();
  });
});
```

### 8.2 Integration 테스트

```typescript
// __tests__/screens/LoginScreen.test.tsx
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { LoginScreen } from '../LoginScreen';

jest.mock('../../api/auth.api', () => ({
  login: jest.fn(),
}));

describe('LoginScreen', () => {
  it('performs login on submit', async () => {
    const { getByPlaceholder, getByText } = render(<LoginScreen />);

    fireEvent.changeText(getByPlaceholder('Email'), 'test@example.com');
    fireEvent.changeText(getByPlaceholder('Password'), 'password123');
    fireEvent.press(getByText('Login'));

    await waitFor(() => {
      expect(require('../../api/auth.api').login).toHaveBeenCalledWith({
        email: 'test@example.com',
        password: 'password123',
      });
    });
  });
});
```

---

## 9. 에러 핸들링 (Error Handling)

### 9.1 Error Boundary

```typescript
// components/common/ErrorBoundary.tsx
import React, { Component, ReactNode } from 'react';
import { View, Text, Button } from 'react-native';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: React.ErrorInfo) {
    console.error('Error caught by boundary:', error, errorInfo);
    // 에러 로깅 서비스에 전송 (Sentry 등)
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return (
        <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
          <Text>Something went wrong</Text>
          <Button title="Try again" onPress={this.handleReset} />
        </View>
      );
    }

    return this.props.children;
  }
}
```

### 9.2 API 에러 핸들링

```typescript
// utils/errorHandler.ts
export class ApiError extends Error {
  constructor(
    message: string,
    public statusCode: number,
    public data?: any
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// api/client.ts
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      throw new ApiError(
        error.response.data.message || 'An error occurred',
        error.response.status,
        error.response.data
      );
    }
    throw error;
  }
);

// 사용
try {
  await loginAPI(credentials);
} catch (error) {
  if (error instanceof ApiError) {
    if (error.statusCode === 401) {
      Alert.alert('Invalid credentials');
    } else if (error.statusCode === 500) {
      Alert.alert('Server error');
    }
  }
}
```

---

## 10. 코딩 컨벤션

### 10.1 네이밍 컨벤션

```typescript
// 컴포넌트: PascalCase
export function UserProfile() {}

// 함수/변수: camelCase
const fetchUserData = async () => {};
const isLoading = false;

// 상수: UPPER_SNAKE_CASE
const API_BASE_URL = 'https://api.example.com';
const MAX_RETRY_COUNT = 3;

// 타입/인터페이스: PascalCase
interface User {}
type UserRole = 'admin' | 'user';

// Private 변수: _camelCase (선택)
const _privateHelper = () => {};
```

### 10.2 파일 네이밍

```
UserProfile.tsx         # 컴포넌트
userProfile.utils.ts    # 유틸리티
user.api.ts            # API
user.types.ts          # 타입
useUser.ts             # 훅
authStore.ts           # 스토어
UserProfile.test.tsx   # 테스트
```

### 10.3 Import 순서

```typescript
// 1. React 및 React Native
import React, { useState, useEffect } from 'react';
import { View, Text, StyleSheet } from 'react-native';

// 2. 외부 라이브러리
import { useNavigation } from '@react-navigation/native';
import { useQuery } from '@tanstack/react-query';

// 3. 내부 모듈 (절대 경로)
import { Button } from '@/components/common';
import { useAuthStore } from '@/stores/authStore';
import { fetchUser } from '@/api/user.api';

// 4. 타입
import type { User } from '@/types/user.types';

// 5. 상대 경로 (같은 폴더)
import { ProfileHeader } from './ProfileHeader';
import { styles } from './styles';
```

---

## 11. Expo 특화 베스트 프랙티스

### 11.1 EAS Build 설정

```json
// eas.json
{
  "cli": {
    "version": ">= 3.0.0"
  },
  "build": {
    "development": {
      "developmentClient": true,
      "distribution": "internal",
      "ios": {
        "simulator": true
      }
    },
    "preview": {
      "distribution": "internal"
    },
    "production": {
      "autoIncrement": true
    }
  }
}
```

### 11.2 환경 변수 관리

```typescript
// app.config.ts
import { ExpoConfig, ConfigContext } from '@expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: process.env.APP_NAME || 'Upvy',
  slug: 'upvy',
  extra: {
    apiUrl: process.env.API_URL,
    environment: process.env.ENVIRONMENT || 'development',
  },
});

// 사용
import Constants from 'expo-constants';

const API_URL = Constants.expoConfig?.extra?.apiUrl;
```

### 11.3 OTA 업데이트 (Over-The-Air Updates)

```typescript
// App.tsx
import * as Updates from 'expo-updates';

useEffect(() => {
  async function checkForUpdates() {
    if (!__DEV__) {
      try {
        const update = await Updates.checkForUpdateAsync();
        if (update.isAvailable) {
          await Updates.fetchUpdateAsync();
          await Updates.reloadAsync();
        }
      } catch (e) {
        console.error('Update check failed:', e);
      }
    }
  }

  checkForUpdates();
}, []);
```

---

## 12. 보안 (Security)

### 12.1 토큰 저장

```typescript
// ✅ expo-secure-store 사용 (민감한 데이터)
import * as SecureStore from 'expo-secure-store';

export async function saveToken(token: string) {
  await SecureStore.setItemAsync('authToken', token);
}

export async function getToken() {
  return await SecureStore.getItemAsync('authToken');
}

// ❌ AsyncStorage는 민감하지 않은 데이터만
import AsyncStorage from '@react-native-async-storage/async-storage';

await AsyncStorage.setItem('theme', 'dark');
```

### 12.2 환경 변수 보호

```typescript
// ❌ Bad: API 키를 코드에 하드코딩
const API_KEY = 'sk_live_abc123';

// ✅ Good: 환경 변수 사용
const API_KEY = Constants.expoConfig?.extra?.apiKey;

// .env (gitignore에 추가)
API_KEY=sk_live_abc123
```

---

## 13. 접근성 (Accessibility)

```typescript
// ✅ 접근성 속성 추가
<TouchableOpacity
  accessible={true}
  accessibilityLabel="Profile settings"
  accessibilityHint="Opens profile settings screen"
  accessibilityRole="button"
  onPress={handlePress}
>
  <Text>Settings</Text>
</TouchableOpacity>

// ✅ 텍스트 크기 조절 지원
<Text adjustsFontSizeToFit numberOfLines={1}>
  Long text that adjusts
</Text>

// ✅ 다크 모드 지원
import { useColorScheme } from 'react-native';

function MyComponent() {
  const colorScheme = useColorScheme();
  const isDark = colorScheme === 'dark';

  return (
    <View style={{ backgroundColor: isDark ? '#000' : '#fff' }}>
      <Text style={{ color: isDark ? '#fff' : '#000' }}>
        Adapts to theme
      </Text>
    </View>
  );
}
```

---

## 14. 체크리스트

코드 작성 시 다음 항목을 확인하세요:

### 컴포넌트 작성 시
- [ ] TypeScript 타입이 명확하게 정의되어 있는가?
- [ ] Props에 기본값이 필요한 경우 설정되어 있는가?
- [ ] 재사용 가능하도록 설계되어 있는가?
- [ ] 불필요한 리렌더링이 발생하지 않는가? (React.memo, useMemo, useCallback)
- [ ] 접근성 속성이 추가되어 있는가?

### 성능
- [ ] FlatList에서 keyExtractor가 설정되어 있는가?
- [ ] 이미지에 적절한 크기와 캐싱이 설정되어 있는가?
- [ ] 애니메이션에 useNativeDriver가 true로 설정되어 있는가?
- [ ] 불필요한 useEffect가 없는가?

### 상태 관리
- [ ] 상태가 적절한 레벨(로컬/전역)에 저장되어 있는가?
- [ ] TanStack Query를 사용하여 서버 상태를 관리하는가?
- [ ] Zustand의 선택자를 사용하여 필요한 상태만 구독하는가?

### 에러 핸들링
- [ ] API 호출에 try-catch가 있는가?
- [ ] 에러 메시지가 사용자 친화적인가?
- [ ] ErrorBoundary로 감싸져 있는가?

### 테스트
- [ ] 주요 비즈니스 로직에 대한 테스트가 작성되어 있는가?
- [ ] 컴포넌트가 예상대로 렌더링되는가?

---

## 15. 참고 자료

- [React Native 공식 문서](https://reactnative.dev/)
- [Expo 공식 문서](https://docs.expo.dev/)
- [React Navigation](https://reactnavigation.org/)
- [TanStack Query](https://tanstack.com/query/latest)
- [Zustand](https://github.com/pmndrs/zustand)
- [React Hook Form](https://react-hook-form.com/)
- [NativeWind](https://www.nativewind.dev/)

---

**이 가이드는 2025년 최신 베스트 프랙티스를 반영하여 작성되었습니다.**
