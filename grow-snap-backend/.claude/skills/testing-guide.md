# GrowSnap Backend 테스트 작성 가이드

> Controller, Service, Repository 테스트 템플릿과 규칙을 제공합니다.

## 테스트 작성 필수 계층

**모든 기능 구현 시, 다음 3가지 계층의 테스트를 반드시 작성합니다:**

1. **Controller 테스트** - HTTP 요청/응답, Validation, REST Docs
2. **Service 테스트** - 비즈니스 로직, 예외 처리, 트랜잭션
3. **Repository 테스트** - 데이터베이스 CRUD, 쿼리, Soft Delete, Audit Trail

**❌ Controller + Service 테스트만 작성하고 Repository 테스트를 생략하지 마세요!**
**✅ Repository 테스트가 없으면 데이터베이스 레벨의 버그를 놓칠 수 있습니다!**

## Controller 테스트 인증 모킹 (OAuth2 Resource Server)

**중요**: 이 프로젝트는 Spring Security OAuth2 Resource Server를 사용하므로, 컨트롤러 테스트에서 인증된 사용자를 모킹할 때는 반드시 `mockUser()` helper function을 사용해야 합니다.

### mockUser() Helper Function 사용법

**위치**: `src/test/kotlin/me/onetwo/growsnap/util/WebTestClientExtensions.kt`

```kotlin
import me.onetwo.growsnap.util.mockUser

// 사용 예시
webTestClient
    .mutateWith(mockUser(userId))  // 인증된 사용자 모킹
    .post()
    .uri("/api/v1/videos")
    .bodyValue(request)
    .exchange()
    .expectStatus().isCreated
```

**규칙**:
- ✅ **항상 mockUser() 사용**: 컨트롤러 테스트에서 인증이 필요한 API는 `mockUser(userId)` 사용
- ✅ **mutateWith() 위치**: HTTP 메서드(post, get 등) 호출 전에 `.mutateWith(mockUser(userId))` 호출
- ❌ **하드코딩 금지**: `.header(HttpHeaders.AUTHORIZATION, "Bearer ...")` 하드코딩 금지
- ❌ **순서 주의**: `.post().mutateWith(...)` 순서는 작동하지 않음

### ❌ 잘못된 예시

```kotlin
// ❌ BAD: Authorization 헤더 하드코딩
webTestClient.post()
    .uri("/api/v1/videos")
    .header(HttpHeaders.AUTHORIZATION, "Bearer test-token-$userId")
    .exchange()

// ❌ BAD: mutateWith() 호출 순서 틀림
webTestClient.post()
    .uri("/api/v1/videos")
    .mutateWith(mockUser(userId))  // post() 이후에 호출하면 작동하지 않음
    .exchange()
```

### ✅ 올바른 예시

```kotlin
// ✅ GOOD: mockUser() helper function 사용
val userId = UUID.randomUUID()

webTestClient
    .mutateWith(mockUser(userId))  // HTTP 메서드 전에 호출
    .post()
    .uri("/api/v1/videos")
    .bodyValue(request)
    .exchange()
    .expectStatus().isCreated
```

## Controller 테스트 개요

**Controller는 단위 테스트(Unit Test)와 통합 테스트(Integration Test) 모두 작성해야 합니다.**

### 단위 테스트 vs 통합 테스트

| 구분 | 단위 테스트 | 통합 테스트 |
|------|-----------|-----------|
| **목적** | HTTP 요청/응답, Validation 검증 | 전체 스택 통합 검증 (Controller → Service → Repository → DB) |
| **어노테이션** | `@WebFluxTest` | `@SpringBootTest` |
| **Service 처리** | `@MockkBean`으로 모킹 | 실제 Service 사용 |
| **데이터베이스** | 사용 안 함 | 실제 H2 DB 사용 |
| **REST Docs** | ✅ **작성 필수** | ❌ 작성 안 함 |
| **테스트 속도** | 빠름 | 상대적으로 느림 |
| **검증 범위** | Controller 계층만 | Controller → Repository 전체 플로우 |

### 왜 둘 다 작성하는가?

1. **단위 테스트**: API 스펙, Validation, HTTP 상태 코드, REST Docs 생성
2. **통합 테스트**: 실제 DB 연동, 트랜잭션, 전체 플로우 검증, 에러 핸들링

**중요**:
- ✅ **동일한 테스트 케이스**를 단위/통합 테스트 모두 작성 (테스트 시나리오 일관성)
- ✅ **Spring REST Docs는 단위 테스트에만 작성** (빠른 문서 생성, 통합 테스트는 속도 저하)
- ✅ **통합 테스트는 실제 DB를 사용**하여 전체 플로우 검증

## Controller 단위 테스트 템플릿

```kotlin
@WebFluxTest(VideoController::class)
@Import(TestSecurityConfig::class, RestDocsConfiguration::class)
@ActiveProfiles("test")
@AutoConfigureRestDocs
@DisplayName("비디오 Controller 테스트")
class VideoControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var videoService: VideoService

    @Nested
    @DisplayName("POST /api/v1/videos - 비디오 생성")
    inner class CreateVideo {

        @Test
        @DisplayName("유효한 요청으로 생성 시, 201 Created와 비디오 정보를 반환한다")
        fun createVideo_WithValidRequest_ReturnsCreatedVideo() {
            // Given: 테스트 데이터 준비
            val userId = UUID.randomUUID()
            val request = VideoCreateRequest(/* ... */)
            val expected = VideoResponse(/* ... */)
            every { videoService.createVideo(userId, any()) } returns Mono.just(expected)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))  // 인증된 사용자 모킹
                .post()
                .uri("/api/v1/videos")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody<VideoResponse>()
                .isEqualTo(expected)
                .consumeWith(
                    document("video-create",
                        requestFields(/* ... */),
                        responseFields(/* ... */)
                    )
                )

            verify(exactly = 1) { videoService.createVideo(userId, request) }
        }

        @Test
        @DisplayName("제목이 비어있는 경우, 400 Bad Request를 반환한다")
        fun createVideo_WithEmptyTitle_ReturnsBadRequest() {
            // Given: 잘못된 요청
            val userId = UUID.randomUUID()

            // When & Then: 400 응답 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("/api/v1/videos")
                .bodyValue(mapOf("title" to ""))
                .exchange()
                .expectStatus().isBadRequest
        }
    }
}
```

### Controller 단위 테스트 체크리스트

- [ ] **@WebFluxTest**: 특정 Controller만 로드
- [ ] **mockUser() 사용**: 인증된 사용자 모킹
- [ ] **Service Mocking**: @MockkBean으로 Service 모킹
- [ ] **Given-When-Then**: 명확한 테스트 구조
- [ ] **DisplayName**: 한글로 명확한 시나리오 설명
- [ ] **REST Docs**: document()로 API 문서 생성
- [ ] **Validation 테스트**: 잘못된 요청에 대한 400 응답 검증

## Controller 통합 테스트 템플릿

**Controller 통합 테스트는 실제 데이터베이스를 사용하여 전체 플로우를 검증합니다.**

### 템플릿 코드

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class, ContentControllerIntegrationTest.TestConfig::class)
@ActiveProfiles("test")
@DisplayName("콘텐츠 Controller 통합 테스트")
class ContentControllerIntegrationTest {

    @TestConfiguration
    class TestConfig {
        /**
         * 테스트용 S3Client Mock (이 테스트 클래스에서만 사용)
         */
        @Bean
        @Primary
        fun s3Client(): S3Client {
            val mockClient = mockk<S3Client>(relaxed = true)
            every { mockClient.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } returns
                HeadObjectResponse.builder().build()
            return mockClient
        }
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Nested
    @DisplayName("POST /api/v1/contents - 콘텐츠 생성")
    inner class CreateContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 생성 시, 201과 콘텐츠 정보를 반환한다")
        fun createContent_WithValidRequest_Returns201AndContentInfo() {
            // Given: 사용자 생성 (실제 DB에 저장)
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            // 콘텐츠 생성 요청
            val request = ContentCreateRequest(
                contentId = UUID.randomUUID().toString(),
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test", "video"),
                language = "ko",
                thumbnailUrl = "https://example.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080
            )

            // When & Then: 콘텐츠 생성 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri(ApiPaths.API_V1_CONTENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isNotEmpty
                .jsonPath("$.creatorId").isEqualTo(user.id!!.toString())
                .jsonPath("$.title").isEqualTo("Test Video")
                .jsonPath("$.contentType").isEqualTo("VIDEO")
                .jsonPath("$.status").isEqualTo("PUBLISHED")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId} - 콘텐츠 조회")
    inner class GetContent {

        @Test
        @DisplayName("존재하는 콘텐츠 조회 시, 200과 콘텐츠 정보를 반환한다")
        fun getContent_WhenContentExists_Returns200AndContentInfo() {
            // Given: 사용자와 콘텐츠 생성 (실제 DB)
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(content.id!!.toString())
                .jsonPath("$.creatorId").isEqualTo(user.id!!.toString())
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 조회 시, 404를 반환한다")
        fun getContent_WhenContentNotExists_Returns404() {
            // Given: 존재하지 않는 contentId
            val nonExistentId = UUID.randomUUID()

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", nonExistentId.toString())
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId} - 콘텐츠 삭제")
    inner class DeleteContent {

        @Test
        @DisplayName("본인이 작성한 콘텐츠 삭제 시, 204를 반환한다")
        fun deleteContent_WhenOwner_Returns204() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isNoContent

            // Then: 소프트 삭제 확인 (findById는 삭제된 콘텐츠를 반환하지 않음)
            val deletedContent = contentRepository.findById(content.id!!).block()
            assertThat(deletedContent).isNull()
        }

        @Test
        @DisplayName("다른 사용자의 콘텐츠 삭제 시, 403을 반환한다")
        fun deleteContent_WhenNotOwner_Returns403() {
            // Given: 두 명의 사용자 생성
            val (contentOwner, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "owner@example.com",
                providerId = "google-owner"
            )

            val (otherUser, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "other@example.com",
                providerId = "google-other"
            )

            // 첫 번째 사용자가 콘텐츠 생성
            val content = createContent(
                contentRepository,
                creatorId = contentOwner.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: 다른 사용자가 콘텐츠 삭제 시도
            webTestClient
                .mutateWith(mockUser(otherUser.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isForbidden
        }
    }
}
```

### Controller 통합 테스트 핵심 원칙

#### 1. SpringBootTest 사용

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
```

- `@SpringBootTest`: 전체 Spring 컨텍스트 로드 (실제 Service, Repository 사용)
- `webEnvironment = RANDOM_PORT`: 랜덤 포트로 실제 서버 시작
- `@AutoConfigureWebTestClient`: WebTestClient 자동 구성
- `@DirtiesContext`: 각 테스트 후 컨텍스트 초기화 (테스트 격리)

#### 2. 실제 데이터베이스 사용

```kotlin
// ✅ GOOD: 실제 Repository 주입 및 사용
@Autowired
private lateinit var userRepository: UserRepository

@Autowired
private lateinit var contentRepository: ContentRepository

// Given: 실제 DB에 데이터 저장
val user = userRepository.save(testUser).block()!!
val content = contentRepository.save(testContent).block()!!
```

**주의**:
- ❌ Service 모킹 금지 (`@MockkBean` 사용 안 함)
- ✅ 실제 Repository 사용하여 데이터 준비
- ✅ 전체 플로우 검증 (Controller → Service → Repository → DB)

#### 3. REST Docs 작성 금지

```kotlin
// ❌ BAD: 통합 테스트에서 REST Docs 작성 금지
webTestClient
    .post()
    .uri("/api/v1/contents")
    .exchange()
    .expectStatus().isCreated
    .consumeWith(
        document("content-create")  // ❌ 통합 테스트에서는 작성하지 않음
    )

// ✅ GOOD: 통합 테스트는 응답 검증만
webTestClient
    .post()
    .uri("/api/v1/contents")
    .exchange()
    .expectStatus().isCreated
    .expectBody()
    .jsonPath("$.id").isNotEmpty
```

**왜 통합 테스트에서는 REST Docs를 작성하지 않는가?**
- 통합 테스트는 실제 DB를 사용하므로 속도가 느림
- REST Docs는 단위 테스트에서 빠르게 생성하는 것이 효율적
- 중복 문서 생성 방지

#### 4. 테스트 데이터 준비 헬퍼 함수

```kotlin
// ✅ GOOD: 헬퍼 함수로 테스트 데이터 생성
val (user, profile) = createUserWithProfile(
    userRepository,
    userProfileRepository,
    email = "test@example.com",
    providerId = "google-123"
)

val content = createContent(
    contentRepository,
    creatorId = user.id!!,
    contentInteractionRepository = contentInteractionRepository
)
```

**위치**: `src/test/kotlin/me/onetwo/growsnap/util/TestDataHelpers.kt`

#### 5. 동일한 테스트 케이스 작성

**단위 테스트 (ContentControllerTest.kt)**와 **통합 테스트 (ContentControllerIntegrationTest.kt)**는 동일한 테스트 케이스를 포함해야 합니다.

| 테스트 케이스 | 단위 테스트 | 통합 테스트 |
|--------------|-----------|-----------|
| POST /api/v1/contents - 유효한 요청 | ✅ | ✅ |
| GET /api/v1/contents/{id} - 존재하는 콘텐츠 | ✅ | ✅ |
| GET /api/v1/contents/{id} - 존재하지 않는 콘텐츠 | ✅ | ✅ |
| DELETE /api/v1/contents/{id} - 본인 삭제 | ✅ | ✅ |
| DELETE /api/v1/contents/{id} - 타인 삭제 (403) | ✅ | ✅ |

**예시**:

**단위 테스트 (ContentControllerTest.kt)**:
```kotlin
@Test
@DisplayName("존재하는 콘텐츠 조회 시, 200과 콘텐츠 정보를 반환한다")
fun getContent_WhenContentExists_Returns200AndContentInfo() {
    // Given: Service 모킹
    val contentId = UUID.randomUUID()
    val response = ContentResponse(/* ... */)
    every { contentService.getContent(contentId) } returns Mono.just(response)

    // When & Then: API 호출 및 REST Docs 생성
    webTestClient
        .get()
        .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", contentId)
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(contentId.toString())
        .consumeWith(
            document("content-get",
                pathParameters(
                    parameterWithName("contentId").description("콘텐츠 ID")
                ),
                responseFields(/* ... */)
            )
        )
}
```

**통합 테스트 (ContentControllerIntegrationTest.kt)**:
```kotlin
@Test
@DisplayName("존재하는 콘텐츠 조회 시, 200과 콘텐츠 정보를 반환한다")
fun getContent_WhenContentExists_Returns200AndContentInfo() {
    // Given: 실제 DB에 데이터 생성
    val (user, _) = createUserWithProfile(
        userRepository,
        userProfileRepository,
        email = "test@example.com",
        providerId = "google-123"
    )

    val content = createContent(
        contentRepository,
        creatorId = user.id!!,
        contentInteractionRepository = contentInteractionRepository
    )

    // When & Then: API 호출 및 검증 (REST Docs 없음)
    webTestClient
        .get()
        .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(content.id!!.toString())
        .jsonPath("$.creatorId").isEqualTo(user.id!!.toString())
}
```

**차이점**:
- **단위 테스트**: Service 모킹, REST Docs 생성
- **통합 테스트**: 실제 DB 사용, REST Docs 없음
- **공통점**: 동일한 테스트 시나리오, 동일한 DisplayName

### Controller 통합 테스트 체크리스트

**모든 Controller는 반드시 다음을 통합 테스트해야 합니다:**

- [ ] **@SpringBootTest**: 전체 Spring 컨텍스트 로드
- [ ] **실제 DB 사용**: Service, Repository 모킹 금지
- [ ] **@DirtiesContext**: 각 테스트 후 컨텍스트 초기화
- [ ] **mockUser() 사용**: 인증된 사용자 모킹
- [ ] **Given-When-Then**: 명확한 테스트 구조
- [ ] **DisplayName**: 한글로 명확한 시나리오 설명
- [ ] **REST Docs 작성 금지**: 단위 테스트에서만 작성
- [ ] **동일한 테스트 케이스**: 단위 테스트와 동일한 시나리오
- [ ] **헬퍼 함수 활용**: createUserWithProfile(), createContent() 등 사용
- [ ] **전체 플로우 검증**: Controller → Service → Repository → DB

## Service 테스트 템플릿 (Reactive)

```kotlin
@ExtendWith(MockKExtension::class)
@DisplayName("비디오 Service 테스트")
class VideoServiceImplTest {

    @MockK
    private lateinit var videoRepository: VideoRepository

    @MockK
    private lateinit var s3Service: S3Service

    @InjectMockKs
    private lateinit var videoService: VideoServiceImpl

    @Nested
    @DisplayName("createVideo - 비디오 생성")
    inner class CreateVideo {

        @Test
        @DisplayName("유효한 요청으로 생성 시, 비디오를 저장하고 응답을 반환한다")
        fun createVideo_WithValidRequest_SavesAndReturnsVideo() {
            // Given: 테스트 데이터
            val userId = UUID.randomUUID()
            val request = VideoCreateRequest(/* ... */)
            val savedVideo = Video(/* ... */)

            // ✅ GOOD: Repository 모킹은 반드시 Mono/Flux를 반환
            every { videoRepository.save(any()) } returns Mono.just(savedVideo)
            every { s3Service.generateUploadUrl(any()) } returns Mono.just("url")

            // When: 메서드 실행
            val result = videoService.createVideo(userId, request)

            // Then: StepVerifier로 Reactive 타입 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(savedVideo.id)
                    assertThat(response.title).isEqualTo(savedVideo.title)
                }
                .verifyComplete()

            // MockK verify로 호출 검증
            verify(exactly = 1) { videoRepository.save(any()) }
            verify(exactly = 1) { s3Service.generateUploadUrl(any()) }
        }

        @Test
        @DisplayName("저장 실패 시, VideoCreationException을 발생시킨다")
        fun createVideo_WhenSaveFails_ThrowsException() {
            // Given: 저장 실패 상황
            val userId = UUID.randomUUID()
            val request = VideoCreateRequest(/* ... */)

            // ✅ GOOD: 에러 모킹도 Mono.error()로 반환
            every { videoRepository.save(any()) } returns
                Mono.error(RuntimeException("DB error"))

            // When: 메서드 실행
            val result = videoService.createVideo(userId, request)

            // Then: StepVerifier로 예외 검증
            StepVerifier.create(result)
                .expectError(VideoException.VideoCreationException::class.java)
                .verify()
        }

        @Test
        @DisplayName("여러 Repository 호출 시, 모두 성공하면 결과를 반환한다")
        fun createVideo_WithMultipleRepositoryCalls_ReturnsResult() {
            // Given: 여러 Repository 모킹
            val userId = UUID.randomUUID()
            val video = Video(/* ... */)
            val metadata = VideoMetadata(/* ... */)

            // ✅ GOOD: 각 Repository 메서드를 Mono로 모킹
            every { videoRepository.save(any()) } returns Mono.just(video)
            every { metadataRepository.save(any()) } returns Mono.just(metadata)

            // When: 메서드 실행
            val result = videoService.createVideoWithMetadata(userId, request)

            // Then: StepVerifier로 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isNotNull()
                }
                .verifyComplete()

            verify(exactly = 1) { videoRepository.save(any()) }
            verify(exactly = 1) { metadataRepository.save(any()) }
        }
    }
}
```

### Service 단위 테스트 핵심 포인트

#### 1. Repository 모킹은 항상 Reactive 타입 반환

```kotlin
// ✅ GOOD: Mono/Flux 반환
every { userRepository.findById(userId) } returns Mono.just(user)
every { contentRepository.findAll() } returns Flux.just(content1, content2)

// ❌ BAD: 직접 객체 반환
every { userRepository.findById(userId) } returns user
```

#### 2. StepVerifier로 Reactive 타입 검증

```kotlin
// ✅ GOOD: StepVerifier 사용
StepVerifier.create(result)
    .assertNext { /* 검증 */ }
    .verifyComplete()

// ✅ GOOD: 에러 검증
StepVerifier.create(result)
    .expectError(CustomException::class.java)
    .verify()

// ❌ BAD: .block() 사용
val actual = result.block()
assertThat(actual).isEqualTo(expected)
```

#### 3. 에러 케이스 모킹

```kotlin
// ✅ GOOD: Mono.error()로 에러 모킹
every { repository.save(any()) } returns Mono.error(RuntimeException("Error"))

// ✅ GOOD: Mono.empty()로 결과 없음 모킹
every { repository.findById(any()) } returns Mono.empty()
```

### Service 테스트 체크리스트

- [ ] **MockK 사용**: @MockK, @InjectMockKs 어노테이션 활용
- [ ] **Reactive 모킹**: Repository 모킹 시 `Mono.just()`, `Flux.just()` 반환
- [ ] **StepVerifier**: Reactive 타입 검증에 StepVerifier 사용
- [ ] **Given-When-Then**: 명확한 테스트 구조
- [ ] **DisplayName**: 한글로 명확한 시나리오 설명
- [ ] **예외 처리 테스트**: 실패 시나리오도 반드시 테스트
- [ ] **Verify**: Mock 호출 횟수 검증
- [ ] **block() 금지**: Service 테스트에서 `.block()` 사용하지 않기

## Repository 테스트 (통합 테스트)

**중요**: Repository는 반드시 **통합 테스트 (Integration Test)** 로 작성합니다.

### Why Integration Test?

- Repository는 실제 데이터베이스와 상호작용하는 계층
- 단위 테스트로는 JOOQ 쿼리, SQL 문법, 데이터베이스 제약조건을 검증할 수 없음
- H2 In-Memory DB를 사용하여 실제 데이터베이스 동작을 검증
- 트랜잭션 격리, Soft Delete, Audit Trail 등 데이터베이스 레벨 기능 검증 필요

### Repository 테스트 템플릿

```kotlin
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("콘텐츠 인터랙션 Repository 통합 테스트")
class ContentInteractionRepositoryTest {

    @Autowired
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    private lateinit var testUser: User
    private lateinit var testContentId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 데이터 준비

        // 사용자 생성
        testUser = userRepository.save(
            User(
                email = "creator@test.com",
                provider = OAuthProvider.GOOGLE,
                providerId = "creator-123",
                role = UserRole.USER
            )
        )

        // 콘텐츠 생성
        testContentId = UUID.randomUUID()
        insertContent(testContentId, testUser.id!!, "Test Video")
    }

    @Nested
    @DisplayName("incrementViewCount - 조회수 증가")
    inner class IncrementViewCount {

        @Test
        @DisplayName("조회수를 1 증가시킨다")
        fun incrementViewCount_IncreasesCountByOne() {
            // Given: 초기 조회수 확인
            val initialCount = getViewCount(testContentId)

            // When: 조회수 증가
            contentInteractionRepository.incrementViewCount(testContentId).block()

            // Then: 1 증가 확인
            val updatedCount = getViewCount(testContentId)
            assertEquals(initialCount + 1, updatedCount)
        }

        @Test
        @DisplayName("여러 번 증가 시, 누적된다")
        fun incrementViewCount_MultipleTimes_Accumulates() {
            // Given: 초기 조회수 확인
            val initialCount = getViewCount(testContentId)

            // When: 3번 증가
            contentInteractionRepository.incrementViewCount(testContentId).block()
            contentInteractionRepository.incrementViewCount(testContentId).block()
            contentInteractionRepository.incrementViewCount(testContentId).block()

            // Then: 3 증가 확인
            val updatedCount = getViewCount(testContentId)
            assertEquals(initialCount + 3, updatedCount)
        }

        @Test
        @DisplayName("삭제된 콘텐츠는 업데이트되지 않는다")
        fun incrementViewCount_DeletedContent_DoesNotUpdate() {
            // Given: 콘텐츠 삭제 (Soft Delete)
            dslContext.update(CONTENT_INTERACTIONS)
                .set(CONTENT_INTERACTIONS.DELETED_AT, LocalDateTime.now())
                .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(testContentId.toString()))
                .execute()

            val initialCount = getViewCount(testContentId)

            // When: 조회수 증가 시도
            contentInteractionRepository.incrementViewCount(testContentId).block()

            // Then: 변경 없음
            val updatedCount = getViewCount(testContentId)
            assertEquals(initialCount, updatedCount)
        }
    }

    /**
     * 콘텐츠 삽입 헬퍼 메서드
     */
    private fun insertContent(
        contentId: UUID,
        creatorId: UUID,
        title: String
    ) {
        val now = LocalDateTime.now()

        // Contents 테이블 데이터 삽입
        dslContext.insertInto(CONTENTS)
            .set(CONTENTS.ID, contentId.toString())
            .set(CONTENTS.CREATOR_ID, creatorId.toString())
            .set(CONTENTS.CONTENT_TYPE, ContentType.VIDEO.name)
            .set(CONTENTS.STATUS, ContentStatus.PUBLISHED.name)
            .set(CONTENTS.CREATED_AT, now)
            .set(CONTENTS.CREATED_BY, creatorId.toString())
            .set(CONTENTS.UPDATED_AT, now)
            .set(CONTENTS.UPDATED_BY, creatorId.toString())
            .execute()

        // Content_Interactions 테이블 (초기값 0)
        dslContext.insertInto(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, 0)
            .set(CONTENT_INTERACTIONS.LIKE_COUNT, 0)
            .set(CONTENT_INTERACTIONS.CREATED_AT, now)
            .set(CONTENT_INTERACTIONS.CREATED_BY, creatorId.toString())
            .set(CONTENT_INTERACTIONS.UPDATED_AT, now)
            .set(CONTENT_INTERACTIONS.UPDATED_BY, creatorId.toString())
            .execute()
    }

    /**
     * 조회수 조회 헬퍼 메서드
     */
    private fun getViewCount(contentId: UUID): Int {
        return dslContext.select(CONTENT_INTERACTIONS.VIEW_COUNT)
            .from(CONTENT_INTERACTIONS)
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .fetchOne(CONTENT_INTERACTIONS.VIEW_COUNT) ?: 0
    }
}
```

### Repository 테스트 핵심 원칙

#### 1. 통합 테스트 필수 (Integration Test Required)

```kotlin
@SpringBootTest          // ✅ Spring 컨텍스트 로드 (H2 DB 포함)
@ActiveProfiles("test")  // ✅ application-test.yml 사용
@Transactional           // ✅ 각 테스트 후 자동 롤백 (테스트 격리)
```

#### 2. 실제 데이터베이스 검증

- DSLContext를 사용하여 실제 데이터베이스 상태 확인
- R2DBC + JOOQ 쿼리가 올바르게 실행되는지 검증
- Soft Delete, Audit Trail 등 데이터베이스 레벨 패턴 검증

#### 3. 헬퍼 메서드 활용

```kotlin
// ✅ 테스트 데이터 삽입 헬퍼 메서드 (동기 실행)
private fun insertContent(contentId: UUID, creatorId: UUID, title: String) {
    // DSLContext.execute()는 동기로 실행됨
    dslContext.insertInto(CONTENTS)
        .set(CONTENTS.ID, contentId.toString())
        .set(CONTENTS.CREATOR_ID, creatorId.toString())
        .execute()
}

// ✅ 데이터베이스 상태 확인 헬퍼 메서드 (동기 실행)
private fun getViewCount(contentId: UUID): Int {
    return dslContext.select(CONTENT_INTERACTIONS.VIEW_COUNT)
        .from(CONTENT_INTERACTIONS)
        .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
        .fetchOne(CONTENT_INTERACTIONS.VIEW_COUNT) ?: 0
}
```

#### 4. Given-When-Then 패턴 (R2DBC)

```kotlin
// Given: 테스트 데이터 준비 (BeforeEach 또는 테스트 메서드 내)
val initialCount = getViewCount(testContentId)

// When: Repository 메서드 실행 (✅ .block()!!로 동기 변환)
contentInteractionRepository.incrementViewCount(testContentId).block()

// Then: 데이터베이스 상태 검증
val updatedCount = getViewCount(testContentId)
assertEquals(initialCount + 1, updatedCount)
```

**중요**: Repository 통합 테스트에서는 `.block()!!`을 사용하여 Reactive 타입을 동기로 변환합니다.

#### 5. Reactive 타입 처리 (Repository 테스트)

```kotlin
// ✅ GOOD: Mono는 .block()!!로 동기 변환
val user = userRepository.save(testUser).block()!!
assertThat(user.id).isNotNull()

// ✅ GOOD: Flux는 .collectList().block()!!으로 리스트 변환
val results = repository.findAll().collectList().block()!!
assertEquals(3, results.size)

// ✅ GOOD: Mono<Void>는 .block()으로 완료 대기
repository.delete(userId).block()

// ✅ GOOD: 여러 결과를 순차 처리
val user1 = repository.save(user1).block()!!
val user2 = repository.save(user2).block()!!
val user3 = repository.save(user3).block()!!
```

**왜 Repository 테스트에서는 .block()을 사용하는가?**

- Repository 통합 테스트는 **실제 데이터베이스 상태를 검증**하는 것이 목적
- 테스트 코드의 가독성을 위해 동기 방식으로 작성
- Given-When-Then 구조를 명확하게 표현하기 위함
- **주의**: Production 코드 (Repository 구현체)에서는 절대 `.block()` 사용 금지!

### Repository 테스트 체크리스트

**모든 Repository는 반드시 다음을 테스트해야 합니다:**

- [ ] **CRUD 기본 동작**: save, findById, update, delete
- [ ] **조회 조건**: where 절, 정렬, 페이징, limit
- [ ] **Soft Delete**: deleted_at이 null인 데이터만 조회되는지 검증
- [ ] **Audit Trail**: created_at, created_by, updated_at, updated_by 자동 설정 검증
- [ ] **엣지 케이스**: 데이터 없을 때, 중복 데이터, null 값 처리
- [ ] **트랜잭션 격리**: 각 테스트가 독립적으로 실행되는지 확인 (@Transactional)

## 테스트 작성 전체 체크리스트

### 모든 기능 구현 시 필수

- [ ] **Controller 단위 테스트**: HTTP 요청/응답, Validation, REST Docs, Service 모킹
- [ ] **Controller 통합 테스트**: 전체 플로우 검증, 실제 DB 사용, REST Docs 없음
- [ ] **Service 테스트**: 비즈니스 로직, 예외 처리, Repository 모킹
- [ ] **Repository 테스트**: 데이터베이스 CRUD, Soft Delete, Audit Trail
- [ ] **동일한 테스트 케이스**: Controller 단위/통합 테스트는 동일한 시나리오 포함
- [ ] **Given-When-Then**: 모든 테스트에 명시적으로 작성
- [ ] **DisplayName**: 한글로 명확한 시나리오 설명
- [ ] **빌드/테스트 통과**: 모든 테스트가 통과해야 작업 완료
- [ ] **테스트 비중**: 단위 테스트 70%, 통합 테스트 30%

### 테스트 품질 확인

- [ ] **시나리오 기반**: 테스트만 보고 기능을 즉시 파악할 수 있는가?
- [ ] **독립성**: 각 테스트가 독립적으로 실행되는가?
- [ ] **재현 가능성**: 테스트 실패 시 동일한 조건으로 재현 가능한가?
- [ ] **빠른 실행**: 테스트가 빠르게 실행되는가?
