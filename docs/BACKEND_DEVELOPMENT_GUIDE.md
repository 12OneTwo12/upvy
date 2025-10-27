# GrowSnap Backend 개발 가이드

> 개발자를 위한 컨벤션 및 개발 가이드

## 📚 목차

1. [개발 프로세스](#개발-프로세스)
2. [핵심 원칙](#핵심-원칙)
3. [계층별 역할 (MVC)](#계층별-역할-mvc)
4. [테스트 작성 가이드](#테스트-작성-가이드)
5. [REST API 설계](#rest-api-설계)
6. [데이터베이스 규칙](#데이터베이스-규칙)
7. [코드 스타일](#코드-스타일)
8. [Spring Event 패턴](#spring-event-패턴)

---

## 개발 프로세스

모든 기능 개발은 다음 순서를 따릅니다:

```
1. 테스트 코드 작성 (TDD)
   ↓
2. 테스트 통과하는 최소 코드 작성
   ↓
3. 리팩토링 (SOLID 원칙 적용)
   ↓
4. KDoc + REST Docs 작성
   ↓
5. 빌드 및 테스트 (모두 통과 필수)
   ↓
6. 커밋 (Git Convention 준수)
```

## 핵심 원칙

### 1. TDD (Test-Driven Development)

**항상 테스트를 먼저 작성합니다.**

- Red → Green → Refactor 사이클
- 모든 public 메서드는 테스트 필수
- Given-When-Then 주석 필수
- DisplayName은 한글로 명확하게 (예: "유효한 요청으로 비디오 생성 시, 201과 비디오 정보를 반환한다")

### 2. SOLID 원칙

- **S**ingle Responsibility: 한 클래스는 한 가지 책임
- **O**pen-Closed: 확장에 열려있고 수정에 닫혀있게
- **L**iskov Substitution: 인터페이스 구현체는 상호 교체 가능하게
- **I**nterface Segregation: 큰 인터페이스를 작은 것들로 분리
- **D**ependency Inversion: 구현체가 아닌 추상화에 의존

### 3. 문서화

- **모든 public 클래스/함수**: KDoc 작성
- **모든 API 엔드포인트**: REST Docs 작성
- AsciiDoc 작성

### 4. Audit Trail (데이터 감사)

**모든 엔티티는 5가지 필드 필수:**

1. `createdAt`: 생성 시각
2. `createdBy`: 생성한 사용자 ID
3. `updatedAt`: 최종 수정 시각
4. `updatedBy`: 최종 수정한 사용자 ID
5. `deletedAt`: 삭제 시각 (Soft Delete)

**물리적 삭제 금지, Soft Delete만 허용**

## 계층별 역할 (MVC)

### Controller (HTTP 처리만)

**역할:**
- HTTP 요청/응답 처리
- Bean Validation
- Service 호출

**금지:**
- ❌ 비즈니스 로직
- ❌ 데이터베이스 접근
- ❌ 복잡한 데이터 처리

**Principal 추출 (WebFlux):**

```kotlin
@PostMapping("/views")
fun trackViewEvent(
    principal: Mono<Principal>,  // ✅ WebFlux 패턴
    @RequestBody request: ViewEventRequest
): Mono<Void> {
    return principal
        .toUserId()
        .flatMap { userId ->
            analyticsService.trackViewEvent(userId, request)
        }
}
```

### Service (비즈니스 로직)

**역할:**
- 비즈니스 로직 구현
- 트랜잭션 관리
- Repository 호출
- Mono/Flux 변환

**금지:**
- ❌ HTTP 처리
- ❌ DSLContext 직접 사용

### Repository (데이터베이스)

**역할:**
- JOOQ 쿼리 실행
- 순수 타입 반환 (Entity, List, Boolean)

**금지:**
- ❌ Mono/Flux 반환 (Service에서 변환)
- ❌ 비즈니스 로직

## 테스트 작성 가이드

### 필수 테스트 계층

**모든 기능은 3가지 계층 테스트 필수:**

1. Controller 테스트 (HTTP, Validation, REST Docs)
2. Service 테스트 (비즈니스 로직, 예외 처리)
3. Repository 테스트 (데이터베이스 CRUD, Soft Delete)

### Controller 테스트

```kotlin
@WebFluxTest(VideoController::class)
@Import(TestSecurityConfig::class, RestDocsConfiguration::class)
class VideoControllerTest {

    @Test
    @DisplayName("유효한 요청으로 생성 시, 201과 비디오 정보를 반환한다")
    fun createVideo_WithValidRequest_ReturnsCreatedVideo() {
        // Given: 테스트 데이터 준비
        val userId = UUID.randomUUID()
        val request = VideoCreateRequest(...)
        every { videoService.createVideo(userId, any()) } returns Mono.just(expected)

        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(userId))  // ✅ mockUser() 사용
            .post()
            .uri("/api/v1/videos")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
    }
}
```

### Service 테스트

```kotlin
@ExtendWith(MockKExtension::class)
class VideoServiceImplTest {

    @MockK
    private lateinit var videoRepository: VideoRepository

    @InjectMockKs
    private lateinit var videoService: VideoServiceImpl

    @Test
    @DisplayName("유효한 요청으로 생성 시, 비디오를 저장하고 응답을 반환한다")
    fun createVideo_WithValidRequest_SavesAndReturnsVideo() {
        // Given
        every { videoRepository.save(any()) } returns Mono.just(savedVideo)

        // When
        val result = videoService.createVideo(request)

        // Then
        StepVerifier.create(result)
            .assertNext { response ->
                assertThat(response.id).isEqualTo(savedVideo.id)
            }
            .verifyComplete()
    }
}
```

### Repository 테스트 (통합 테스트)

```kotlin
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ContentInteractionRepositoryTest {

    @Autowired
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @Test
    @DisplayName("조회수를 1 증가시킨다")
    fun incrementViewCount_IncreasesCountByOne() {
        // Given
        val initialCount = getViewCount(testContentId)

        // When
        contentInteractionRepository.incrementViewCount(testContentId).block()

        // Then
        val updatedCount = getViewCount(testContentId)
        assertEquals(initialCount + 1, updatedCount)
    }
}
```

## REST API 설계

### URL 패턴

✅ **올바른 예시:**
```
GET    /api/v1/videos              # 목록 조회
POST   /api/v1/videos              # 생성
GET    /api/v1/videos/{id}         # 상세 조회
PUT    /api/v1/videos/{id}         # 전체 수정
DELETE /api/v1/videos/{id}         # 삭제
```

❌ **잘못된 예시:**
```
GET    /api/v1/getAllVideos        # 동사 사용 금지
POST   /api/v1/createVideo         # 동사 사용 금지
```

### HTTP 상태 코드

| 상황 | 상태 코드 |
|-----|---------|
| 조회 성공 | 200 OK |
| 생성 성공 | 201 Created |
| 삭제 성공 | 204 No Content |
| 잘못된 요청 | 400 Bad Request |
| 인증 실패 | 401 Unauthorized |
| 권한 없음 | 403 Forbidden |
| 리소스 없음 | 404 Not Found |
| 중복/충돌 | 409 Conflict |

### WebFlux 반환 타입

**권장 패턴: `Mono<ResponseEntity<T>>`**

```kotlin
@GetMapping("/{id}")
fun getUser(@PathVariable id: UUID): Mono<ResponseEntity<UserResponse>> {
    return userService.findById(id)
        .map { ResponseEntity.ok(it) }
        .defaultIfEmpty(ResponseEntity.notFound().build())
}
```

## 데이터베이스 규칙

### SELECT asterisk (*) 절대 금지

❌ **잘못된 예시:**
```kotlin
dslContext.select(CONTENTS.asterisk()).from(CONTENTS)
```

✅ **올바른 예시:**
```kotlin
dslContext
    .select(
        CONTENTS.ID,
        CONTENTS.TITLE,
        CONTENTS.URL
    )
    .from(CONTENTS)
```

### Soft Delete 필수

❌ **물리적 삭제 금지:**
```kotlin
dslContext.deleteFrom(USER).where(USER.ID.eq(userId))
```

✅ **Soft Delete 사용:**
```kotlin
dslContext
    .update(USER)
    .set(USER.DELETED_AT, LocalDateTime.now())
    .set(USER.UPDATED_BY, deletedBy)
    .where(USER.ID.eq(userId))
```

### 조회 시 Soft Delete 조건

```kotlin
dslContext
    .select(USER.ID, USER.EMAIL)
    .from(USER)
    .where(USER.DELETED_AT.isNull)  // ✅ 필수
```

## 코드 스타일

### 절대 금지 사항

#### 1. println 사용 금지

❌ **금지:**
```kotlin
println("Redis started")
```

✅ **SLF4J Logger 사용:**
```kotlin
logger.info("Redis started on port {}", port)
```

#### 2. 이모티콘 사용 금지

코드, 주석, 로그에서 이모티콘 사용 금지 (문서 파일만 허용)

#### 3. FQCN 사용 금지

❌ **금지:**
```kotlin
org.springframework.data.redis.connection.ReturnType.INTEGER
```

✅ **import 사용:**
```kotlin
import org.springframework.data.redis.connection.ReturnType

ReturnType.INTEGER
```

### 네이밍 규칙

```kotlin
// 클래스: PascalCase
class VideoService

// 함수/변수: camelCase
fun createVideo()
val userId = UUID.randomUUID()

// 상수: UPPER_SNAKE_CASE
const val MAX_VIDEO_SIZE = 100_000_000

// 패키지: lowercase
package me.onetwo.growsnap.domain.video
```

### Kotlin 특성 활용

```kotlin
// data class
data class VideoResponse(val id: String, val title: String)

// null safety
val title = video?.title ?: "기본 제목"

// 확장 함수
fun Video.toResponse(): VideoResponse = VideoResponse(id, title)

// when 표현식
val type = when (category) {
    VideoCategory.PROGRAMMING -> "기술"
    else -> "기타"
}
```

## Spring Event 패턴

### 언제 사용하는가?

- 메인 트랜잭션과 독립적인 작업
- 실패해도 메인 요청에 영향 없어야 하는 작업
- 도메인 간 결합도를 낮추고 싶을 때

### 구현 패턴

#### 1. 이벤트 클래스

```kotlin
data class UserInteractionEvent(
    val userId: UUID,
    val contentId: UUID,
    val interactionType: InteractionType
)
```

#### 2. 이벤트 발행

```kotlin
return incrementCounter.doOnSuccess {
    applicationEventPublisher.publishEvent(
        UserInteractionEvent(userId, contentId, type)
    )
}.then()
```

#### 3. 이벤트 리스너

```kotlin
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handleEvent(event: UserInteractionEvent) {
    try {
        // 이벤트 처리 로직
    } catch (e: Exception) {
        logger.error("Failed to handle event", e)
        // 예외를 삼켜서 메인 트랜잭션에 영향 없음
    }
}
```

## 체크리스트

### 개발 시작 전

- [ ] 요구사항 명확히 이해
- [ ] 테스트 먼저 작성할 준비
- [ ] 계층별 역할 설계 (Controller-Service-Repository)
- [ ] API URL, HTTP 상태 코드 결정
- [ ] 데이터베이스 스키마에 Audit Trail 포함

### PR 전

- [ ] 모든 테스트 통과
- [ ] ./gradlew build 성공
- [ ] Given-When-Then 주석 작성
- [ ] DisplayName 한글 설명
- [ ] KDoc 작성
- [ ] REST Docs 작성
- [ ] Soft Delete 사용
- [ ] SELECT asterisk 사용 안 함
- [ ] println 사용 안 함
- [ ] 이모티콘 사용 안 함
- [ ] FQCN 사용 안 함

---

**더 자세한 내용은 `.claude/skills/` 디렉토리의 Skill 파일을 참조하세요.**
