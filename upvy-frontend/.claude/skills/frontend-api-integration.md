# 프론트엔드 API 통합 가이드

> **⚠️ 중요**: 프론트엔드 개발 시 반드시 백엔드 API 스펙을 먼저 확인하고, 정확히 일치하도록 개발해야 합니다.

## 📋 필수 확인 사항

### 1. API 스펙 확인 순서

프론트엔드 타입이나 API 클라이언트를 작성하기 전에 **반드시** 다음을 확인하세요:

1. **백엔드 Controller** 확인
   - 엔드포인트 경로 (GET, POST, PUT, PATCH, DELETE)
   - 경로 변수 (Path Variable) 및 쿼리 파라미터
   - HTTP 메소드

2. **백엔드 DTO** 확인
   - 요청 DTO (Request)의 모든 필드명, 타입, 필수 여부
   - 응답 DTO (Response)의 모든 필드명, 타입, 구조

3. **특수 케이스** 확인
   - OAuth 콜백 응답 형식
   - 에러 응답 형식
   - 페이지네이션 형식

### 2. 백엔드 DTO 위치

```
upvy-backend/src/main/kotlin/me/onetwo/upvy/domain/
├── auth/dto/AuthDto.kt
├── user/dto/UserDto.kt
├── user/dto/UserProfileDto.kt
├── content/dto/ContentDto.kt
└── ...
```

### 3. 백엔드 Controller 위치

```
upvy-backend/src/main/kotlin/me/onetwo/upvy/domain/
├── auth/controller/AuthController.kt
├── user/controller/UserController.kt
├── user/controller/UserProfileController.kt
├── user/controller/FollowController.kt
└── ...
```

## 🔍 실제 확인 예시

### 예시 1: 닉네임 중복 확인 API

#### ❌ 잘못된 방법
프론트엔드를 먼저 작성하고 추측으로 타입을 정의:

```typescript
// ❌ 백엔드 확인 없이 추측으로 작성
export interface CheckNicknameResponse {
  available: boolean;  // 추측
  message?: string;    // 추측
}
```

#### ✅ 올바른 방법
백엔드 DTO를 먼저 확인:

```kotlin
// 백엔드: UserProfileDto.kt
data class NicknameCheckResponse(
    val nickname: String,
    val isDuplicated: Boolean
)
```

백엔드 Controller 확인:

```kotlin
// 백엔드: UserProfileController.kt
@GetMapping("/check/nickname/{nickname}")
fun checkNickname(
    @PathVariable nickname: String
): Mono<ResponseEntity<NicknameCheckResponse>>
```

프론트엔드 타입을 **정확히** 맞춰서 작성:

```typescript
// ✅ 백엔드 스펙과 정확히 일치
export interface CheckNicknameResponse {
  nickname: string;
  isDuplicated: boolean;
}

// API 호출도 백엔드 스펙과 일치
export const checkNickname = async (nickname: string) => {
  const response = await apiClient.get<CheckNicknameResponse>(
    `/profiles/check/nickname/${nickname}`  // GET 메소드, Path Variable
  );
  return response.data;
};
```

### 예시 2: 프로필 생성 API

#### ✅ 백엔드 확인

```kotlin
// 백엔드: UpdateProfileRequest (PATCH로 업데이트)
data class UpdateProfileRequest(
    val nickname: String? = null,
    val profileImageUrl: String? = null,
    val bio: String? = null
)

// 백엔드: UserProfileResponse (직접 반환)
data class UserProfileResponse(
    val id: Long,
    val userId: UUID,
    val nickname: String,
    val profileImageUrl: String?,
    val bio: String?,
    val followerCount: Int,
    val followingCount: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

// Controller
@PatchMapping
fun updateProfile(
    principal: Mono<Principal>,
    @Valid @RequestBody request: UpdateProfileRequest
): Mono<ResponseEntity<UserProfileResponse>>
```

#### ✅ 프론트엔드 구현

```typescript
// 요청 타입
export interface CreateProfileRequest {
  nickname: string;
  profileImageUrl?: string;  // 백엔드에 있음
  bio?: string;
}

// 응답 타입 (백엔드는 UserProfileResponse를 직접 반환)
export type CreateProfileResponse = UserProfile;

// API 호출
export const createProfile = async (data: CreateProfileRequest) => {
  const response = await apiClient.patch<CreateProfileResponse>(
    '/profiles',  // PATCH 메소드
    data
  );
  return response.data;  // 직접 반환 (래핑 없음)
};
```

## 🚨 흔한 실수들

### 1. HTTP 메소드 불일치

```typescript
// ❌ 백엔드는 GET인데 POST로 호출
export const checkNickname = async (nickname: string) => {
  return await apiClient.post('/profiles/check-nickname', { nickname });
};

// ✅ 백엔드 스펙대로 GET으로 호출
export const checkNickname = async (nickname: string) => {
  return await apiClient.get(`/profiles/check/nickname/${nickname}`);
};
```

### 2. 경로 불일치

```typescript
// ❌ 백엔드: /profiles/{userId}
export const getProfile = (userId: string) =>
  apiClient.get(`/profiles/user/${userId}`);

// ✅ 백엔드 스펙과 일치
export const getProfile = (userId: string) =>
  apiClient.get(`/profiles/${userId}`);
```

### 3. 응답 구조 불일치

```typescript
// ❌ 백엔드는 UserProfileResponse를 직접 반환
export interface CreateProfileResponse {
  profile: UserProfile;  // 래핑되어 있음
}

// ✅ 백엔드는 직접 반환
export type CreateProfileResponse = UserProfile;
```

### 4. 필드명 불일치

```typescript
// ❌ 백엔드: isDuplicated
export interface CheckNicknameResponse {
  available: boolean;
}

// ✅ 백엔드와 정확히 일치
export interface CheckNicknameResponse {
  isDuplicated: boolean;
}
```

### 5. 불필요한 필드

```typescript
// ❌ 백엔드에 없는 필드
export interface User {
  id: string;
  email: string;
  providerId: string;  // 백엔드에 없음!
}

// ✅ 백엔드에 있는 필드만
export interface User {
  id: string;
  email: string;
  provider: 'GOOGLE' | 'NAVER' | 'KAKAO';
  role: 'USER' | 'CREATOR' | 'ADMIN';
}
```

## 📝 체크리스트

프론트엔드 API 관련 코드 작성 전:

- [ ] 백엔드 Controller에서 엔드포인트 경로 확인
- [ ] HTTP 메소드 (GET/POST/PUT/PATCH/DELETE) 확인
- [ ] 백엔드 DTO에서 요청 파라미터 모든 필드 확인
- [ ] 백엔드 DTO에서 응답 구조 확인
- [ ] Path Variable vs Query Parameter vs Request Body 구분
- [ ] 필수 필드 vs 선택 필드 구분
- [ ] 프론트엔드 타입 정의가 백엔드 DTO와 **100% 일치**하는지 확인

## 🔧 디버깅 팁

API 호출이 실패하거나 데이터가 올바르지 않을 때:

1. **백엔드 로그 확인**
   - 요청이 제대로 들어왔는지
   - 파라미터가 올바르게 파싱되었는지
   - 어떤 값이 반환되었는지

2. **네트워크 탭 확인**
   - 실제 HTTP 메소드가 백엔드 스펙과 일치하는지
   - URL 경로가 정확한지
   - Request Body 구조가 올바른지
   - Response 구조 확인

3. **타입 확인**
   - 프론트엔드 타입과 백엔드 DTO를 나란히 놓고 비교
   - 필드명, 타입, 필수/선택 여부가 모두 일치하는지

## 💡 Best Practices

1. **백엔드 우선 접근**
   - 항상 백엔드 코드를 먼저 확인
   - 추측하지 말고 실제 코드 확인

2. **타입 동기화**
   - 백엔드 DTO가 변경되면 프론트엔드 타입도 즉시 업데이트
   - 주석으로 백엔드 DTO 위치 명시

3. **문서화**
   - API 클라이언트 함수에 백엔드 Controller 위치 주석 추가
   - 특이사항이나 주의사항 기록

4. **검증**
   - 새로운 API 통합 시 실제 호출 테스트 필수
   - 백엔드 로그와 프론트엔드 네트워크 탭을 함께 확인

## 🔌 로컬 환경 설정 (Android 에뮬레이터)

### ADB Reverse 설정 (필수)

**⚠️ 중요**: Android 에뮬레이터에서 로컬 백엔드 API를 테스트할 때는 **반드시** ADB reverse 설정이 필요합니다.

#### 설정 방법

```bash
# 에뮬레이터의 localhost를 호스트 머신의 localhost로 포워딩
adb reverse tcp:8080 tcp:8080
```

#### 설정 이유

- Android 에뮬레이터의 `localhost`는 에뮬레이터 자체를 가리킴
- 호스트 머신의 백엔드 서버(localhost:8080)에 접근하려면 포트 포워딩 필요
- `10.0.2.2`를 사용할 수도 있지만, `localhost`를 그대로 사용하는 것이 더 간편함

#### 설정 확인

```bash
# 포트 포워딩 목록 확인
adb reverse --list

# 출력 예시:
# (reverse) tcp:8080 tcp:8080
```

#### 해제 방법

```bash
# 특정 포트 포워딩 해제
adb reverse --remove tcp:8080

# 모든 포트 포워딩 해제
adb reverse --remove-all
```

#### app.json 설정

```json
{
  "expo": {
    "extra": {
      "apiUrl": "http://localhost:8080"  // ✅ adb reverse 설정 시 localhost 사용 가능
    }
  }
}
```

#### 트러블슈팅

1. **"cannot connect to daemon" 에러**
   ```bash
   adb kill-server
   adb start-server
   adb reverse tcp:8080 tcp:8080
   ```

2. **에뮬레이터가 여러 개인 경우**
   ```bash
   # 연결된 디바이스 확인
   adb devices

   # 특정 디바이스에 설정
   adb -s emulator-5554 reverse tcp:8080 tcp:8080
   ```

3. **백엔드 서버가 다른 포트를 사용하는 경우**
   ```bash
   # 예: 백엔드가 3000번 포트 사용
   adb reverse tcp:3000 tcp:3000
   ```

#### 실제 디바이스에서 테스트하는 경우

실제 Android 디바이스에서는 `adb reverse` 대신:

1. **같은 Wi-Fi 네트워크 사용**
2. **호스트 머신의 IP 주소 사용**
   ```json
   {
     "expo": {
       "extra": {
         "apiUrl": "http://192.168.0.10:8080"  // 호스트 머신 IP
       }
     }
   }
   ```

#### 참고

- 이 설정은 **로컬 개발 환경 전용**입니다
- 프로덕션 빌드에서는 실제 서버 URL을 사용해야 합니다
- 에뮬레이터 재시작 시 `adb reverse` 설정이 초기화되므로 다시 실행 필요

---

## 📌 참고 자료

- 백엔드 API 설계: `.claude/skills/api-design.md`
- 백엔드 MVC 레이어: `.claude/skills/mvc-layers.md`
- 백엔드 코드: `upvy-backend/src/main/kotlin/me/onetwo/upvy/`
