# Spring WebFlux 통합 테스트 작성 가이드

## 개요

이 문서는 Spring WebFlux 기반 애플리케이션의 통합 테스트 작성 방법을 설명합니다.

## 기본 설정

### 필수 의존성

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-starter-webflux") // WebTestClient
testImplementation("io.mockk:mockk:1.13.9")
testImplementation("com.ninja-squad:springmockk:4.0.2")
testImplementation("org.awaitility:awaitility-kotlin:4.2.0") // 비동기 이벤트 테스트용
```

### 테스트 클래스 기본 구조

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Transactional
@Rollback
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("XXX Controller 통합 테스트")
class XxxControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var xxxRepository: XxxRepository

    // 실제 빈 사용, mocking 없음
}
```

### 주요 어노테이션 설명

- `@SpringBootTest`: 전체 Spring 컨텍스트를 로드
  - `webEnvironment = RANDOM_PORT`: 랜덤 포트로 실제 서버 실행
- `@AutoConfigureWebTestClient`: WebTestClient 자동 설정
- `@Transactional`: 각 테스트를 트랜잭션 내에서 실행
- `@Rollback`: 테스트 종료 후 자동 롤백 (기본값 true)
- `@Import(TestSecurityConfig::class)`: 테스트용 Security 설정 적용
- `@ActiveProfiles("test")`: test 프로파일 활성화

## 단위 테스트 vs 통합 테스트

| 구분 | 단위 테스트 | 통합 테스트 |
|------|------------|------------|
| 범위 | 단일 컴포넌트 (Controller, Service 등) | 전체 애플리케이션 스택 |
| 의존성 | Mock (@MockkBean) | 실제 빈 (@Autowired) |
| 데이터베이스 | Mock | 실제 DB (H2, Embedded Redis) |
| 속도 | 빠름 | 느림 |
| 목적 | 로직 검증 | E2E 동작 검증, 이벤트 처리 검증 |

## WebTestClient 사용법

### 기본 HTTP 요청

```kotlin
@Test
fun `콘텐츠 생성 API 테스트`() {
    // Given: 테스트 데이터 준비
    val user = userRepository.save(User(...))
    val request = CreateContentRequest(...)

    // When & Then: API 호출 및 검증
    webTestClient
        .mutateWith(mockUser(user!!.id!!))  // 인증 사용자 설정
        .post()
        .uri("/api/v1/contents")
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.id").isNotEmpty
        .jsonPath("$.url").isEqualTo(request.url)
}
```

### 응답 검증 패턴

```kotlin
// 상태 코드 검증
.expectStatus().isOk                // 200
.expectStatus().isCreated           // 201
.expectStatus().isNoContent         // 204
.expectStatus().isBadRequest        // 400
.expectStatus().isNotFound          // 404

// JSON 응답 검증
.expectBody()
.jsonPath("$.id").isEqualTo("123")
.jsonPath("$.name").exists()
.jsonPath("$.items").isArray
.jsonPath("$.items.length()").isEqualTo(5)

// 응답 본문 추출
.expectBody(ContentResponse::class.java)
.returnResult()
.responseBody!!
```

## 테스트 인증 처리

### TestSecurityConfig 설정

```kotlin
@TestConfiguration
@EnableWebFluxSecurity
class TestSecurityConfig {

    @Bean
    @Primary
    fun reactiveJwtDecoder(): ReactiveJwtDecoder {
        return ReactiveJwtDecoder { token ->
            Mono.error(IllegalStateException("JWT decoder should not be called in tests"))
        }
    }

    @Bean
    @Primary
    fun jwtTokenProvider(): JwtTokenProvider = mockk(relaxed = true)

    @Bean
    @Primary
    fun customReactiveOAuth2UserService(): CustomReactiveOAuth2UserService = mockk(relaxed = true)

    @Bean
    @Primary
    fun oAuth2AuthenticationSuccessHandler(): OAuth2AuthenticationSuccessHandler = mockk(relaxed = true)

    @Bean
    @Primary
    fun oAuth2AuthenticationFailureHandler(): OAuth2AuthenticationFailureHandler = mockk(relaxed = true)

    @Bean
    @Primary
    fun refreshTokenRepository(): RefreshTokenRepository = mockk(relaxed = true)
}
```

### mockUser 유틸리티 사용

```kotlin
// src/test/kotlin/util/MockUserUtils.kt
fun mockUser(userId: UUID): SecurityMockServerConfigurers.MockOAuth2LoginMutator {
    return SecurityMockServerConfigurers.mockJwt()
        .jwt { jwt -> jwt.subject(userId.toString()) }
}

// 테스트에서 사용
webTestClient
    .mutateWith(mockUser(userId))
    .get()
    .uri("/api/v1/users/me")
    .exchange()
```

## 트랜잭션 및 데이터 정리

### 자동 롤백

`@Transactional` + `@Rollback`을 사용하면 각 테스트 종료 시 자동으로 롤백됩니다.

```kotlin
@Test
@Transactional
@Rollback  // 기본값이므로 생략 가능
fun `데이터 자동 롤백 테스트`() {
    val user = userRepository.save(User(...))
    // 테스트 종료 후 자동 롤백
}
```

### 수동 정리 (지양)

```kotlin
// ❌ 나쁜 예: 수동으로 데이터 정리
@AfterEach
fun tearDown() {
    userRepository.deleteAll()
}

// ✅ 좋은 예: @Transactional + @Rollback 사용
@Test
@Transactional
@Rollback
fun test() {
    // 테스트 종료 후 자동 롤백
}
```

## 베스트 프랙티스

### 1. 테스트 독립성 유지

각 테스트는 독립적으로 실행되어야 합니다.

```kotlin
// ❌ 나쁜 예: 테스트 간 의존성
private var savedUser: User? = null

@Test
fun test1() {
    savedUser = userRepository.save(User(...))
}

@Test
fun test2() {
    // savedUser에 의존 (test1이 먼저 실행되어야 함)
    contentRepository.save(Content(creatorId = savedUser!!.id))
}

// ✅ 좋은 예: 각 테스트에서 독립적으로 데이터 생성
@Test
fun test1() {
    val user = userRepository.save(User(...))
    // 테스트 로직
}

@Test
fun test2() {
    val user = userRepository.save(User(...))
    val content = contentRepository.save(Content(creatorId = user!!.id))
    // 테스트 로직
}
```

### 2. Given-When-Then 패턴 사용

```kotlin
@Test
fun `좋아요 추가 테스트`() {
    // Given: 테스트 데이터 준비
    val user = userRepository.save(User(...))
    val content = contentRepository.save(Content(...))

    // When & Then: API 호출 및 검증
    webTestClient
        .mutateWith(mockUser(user!!.id!!))
        .post()
        .uri("/api/v1/contents/${content!!.id}/like")
        .exchange()
        .expectStatus().isOk

    // Then: 데이터베이스 상태 검증 (추가 검증)
    val like = userLikeRepository.findByUserIdAndContentId(user.id!!, content.id!!)
    assertThat(like).isNotNull
}
```

### 3. Nested 클래스로 테스트 그룹화

```kotlin
@Nested
@DisplayName("POST /api/v1/contents - 콘텐츠 생성")
inner class CreateContent {

    @Test
    @DisplayName("유효한 요청으로 콘텐츠 생성 시, 201 Created를 반환한다")
    fun createContent_WithValidRequest_ReturnsCreated() {
        // 테스트 로직
    }

    @Test
    @DisplayName("URL이 비어있으면, 400 Bad Request를 반환한다")
    fun createContent_WithEmptyUrl_ReturnsBadRequest() {
        // 테스트 로직
    }
}
```

### 4. 리포지토리 테스트 주의사항 (JOOQ + R2DBC)

이 프로젝트는 **JOOQ와 R2DBC를 함께 사용**합니다.

**중요**: JOOQ와 R2DBC 모두 **리액티브 방식**으로 구현되어 있습니다!

#### JOOQ 리포지토리 (Mono로 감싼 리액티브)
- JOOQ DSL을 `Mono.from()`으로 감싸서 **리액티브하게** 동작합니다
- 반환 타입: `Mono<T>`, `Flux<T>`
- 테스트에서는 `.block()` 호출이 **필요**합니다

```kotlin
// UserRepository (JOOQ 기반이지만 리액티브!)
fun save(user: User): Mono<User> {
    return Mono.from(dsl.insertInto(USERS)...)  // Mono.from()으로 감쌈
}

// ✅ 올바른 사용법 (테스트)
val user = userRepository.save(User(...)).block()!!  // .block() 필요!
val profile = userProfileRepository.save(Profile(...)).block()!!
```

#### R2DBC 리포지토리 (네이티브 리액티브)
- R2DBC를 사용하여 **네이티브하게 리액티브** 동작합니다
- 반환 타입: `Mono<T>`, `Flux<T>`
- 테스트에서는 `.block()` 호출이 **필요**합니다

```kotlin
// ✅ 올바른 사용법 (테스트)
val interaction = contentInteractionRepository.incrementViewCount(contentId).block()!!
val count = contentInteractionRepository.getViewCount(contentId).block()!!
```

**핵심**: JOOQ든 R2DBC든 **모든 레포지토리가 리액티브**이므로 테스트에서는 **반드시 `.block()` 필요**!

## 테스트 실행

```bash
# 전체 통합 테스트 실행
./gradlew test --tests "*IntegrationTest"

# 특정 컨트롤러 통합 테스트 실행
./gradlew test --tests "*.UserControllerIntegrationTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "*.UserControllerIntegrationTest.getMe_Success"
```

## Reactor Sinks 이벤트 테스트

통합 테스트에서는 실제 이벤트가 발행되고 처리되므로, 이벤트 처리 결과를 검증할 수 있습니다.

이 프로젝트는 WebFlux 환경에서 **Reactor Sinks API**를 사용하여 이벤트를 처리합니다.

### Critical Path (동기) vs Non-Critical Path (비동기)

#### Critical Path - 즉시 검증 (Awaitility 불필요)
좋아요 카운트 증가와 같은 중요한 로직은 메인 리액티브 체인에서 동기적으로 처리됩니다.

```kotlin
@Test
fun `좋아요 추가 시 카운트가 즉시 증가한다`() {
    // Given
    val (user, _) = createUserWithProfile(userRepository, userProfileRepository)
    val content = contentRepository.save(Content(...))

    // When: API 호출
    val response = webTestClient
        .mutateWith(mockUser(user.id!!))
        .post()
        .uri("/api/v1/contents/${content.id}/like")
        .exchange()
        .expectStatus().isOk
        .expectBody<LikeResponse>()
        .returnResult()
        .responseBody!!

    // Then: 즉시 검증 (Awaitility 불필요!)
    assertThat(response.likeCount).isEqualTo(1)
}
```

#### Non-Critical Path - Awaitility 검증 (비동기)
협업 필터링 데이터 생성과 같은 부가 기능은 이벤트로 비동기 처리됩니다.

```kotlin
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import java.util.concurrent.TimeUnit

@Test
fun `좋아요 시 협업 필터링 데이터가 생성된다`() {
    // Given
    val (user, _) = createUserWithProfile(userRepository, userProfileRepository)
    val content = contentRepository.save(Content(...))

    // When: API 호출
    webTestClient
        .mutateWith(mockUser(user.id!!))
        .post()
        .uri("/api/v1/contents/${content.id}/like")
        .exchange()
        .expectStatus().isOk

    // Then: 이벤트 처리 대기 및 검증 (비동기)
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val interaction = userContentInteractionRepository
            .findByUserIdAndContentId(user.id!!, content.id!!)
        assertThat(interaction).isNotNull
        assertThat(interaction?.interactionType).isEqualTo("LIKE")
    }
}
```

### Awaitility 타임아웃 설정

```kotlin
// 적절한 타임아웃: 2-5초
await.atMost(2, TimeUnit.SECONDS).untilAsserted { ... }

// 너무 짧음 (불안정): 피할 것
await.atMost(100, TimeUnit.MILLISECONDS).untilAsserted { ... }

// 너무 김 (느림): 피할 것
await.atMost(30, TimeUnit.SECONDS).untilAsserted { ... }
```

자세한 내용은 `.claude/skills/reactor-sinks-event-testing.md`를 참고하세요.

## 주의사항

1. **실제 빈 사용**: 통합 테스트에서는 `@MockkBean` 대신 실제 빈을 사용합니다.
2. **트랜잭션 롤백**: `@Transactional` + `@Rollback`으로 테스트 데이터를 자동 정리합니다.
3. **테스트 독립성**: 각 테스트는 독립적으로 실행 가능해야 합니다.
4. **인증 처리**: `mockUser()` 유틸리티를 사용하여 인증 사용자를 설정합니다.
5. **Security 설정**: `TestSecurityConfig`에서 OAuth2 관련 빈을 mock으로 대체합니다.
6. **비동기 이벤트**: Awaitility를 사용하여 이벤트 처리를 대기합니다.
