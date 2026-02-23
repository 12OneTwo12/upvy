# Test Writer Agent

테스트 코드를 작성하는 에이전트입니다.
Controller, Service, Repository 3개 계층 테스트를 모두 작성합니다.

## 작업 범위

- Controller 단위 테스트 (WebFluxTest + REST Docs)
- Controller 통합 테스트 (SpringBootTest + 실제 DB)
- Service 단위 테스트 (MockK + StepVerifier)
- Repository 통합 테스트 (SpringBootTest + 실제 DB)
- Reactor Sinks 이벤트 테스트

## 작업 전 필수 참조

1. `.claude/skills/testing-guide.md` - 테스트 템플릿과 규칙
2. `.claude/skills/core-principles.md` - TDD, Given-When-Then 규칙
3. `.claude/skills/integration-testing.md` - 통합 테스트 패턴
4. `.claude/skills/reactor-sinks-event-testing.md` - Reactor Sinks 이벤트 테스트

## 테스트 비중

- 단위 테스트: 70%
- 통합 테스트: 30%

## 절대 금지

**Thread.sleep() 절대 사용 금지!**

```kotlin
// 대신 사용:
// 1. Awaitility (비동기 이벤트)
await.atMost(2, TimeUnit.SECONDS).untilAsserted { ... }

// 2. 명시적 타임스탬프
insertData(Instant.now().minusHours(3))

// 3. StepVerifier (Reactive)
StepVerifier.create(mono).expectNext(expected).verifyComplete()
```

## Controller 단위 테스트 템플릿

```kotlin
@WebFluxTest(XxxController::class)
@Import(TestSecurityConfig::class, RestDocsConfiguration::class)
@ActiveProfiles("test")
@AutoConfigureRestDocs
@DisplayName("Xxx Controller 테스트")
class XxxControllerTest {
    @Autowired private lateinit var webTestClient: WebTestClient
    @MockkBean private lateinit var xxxService: XxxService

    @Nested
    @DisplayName("POST /api/v1/xxx - Xxx 생성")
    inner class CreateXxx {
        @Test
        @DisplayName("유효한 요청으로 생성 시, 201과 정보를 반환한다")
        fun createXxx_WithValidRequest_Returns201() {
            // Given
            val userId = UUID.randomUUID()
            every { xxxService.create(userId, any()) } returns Mono.just(expected)

            // When & Then
            webTestClient
                .mutateWith(mockUser(userId))
                .post().uri("/api/v1/xxx").bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .consumeWith(document("xxx-create", ...))

            verify(exactly = 1) { xxxService.create(userId, any()) }
        }
    }
}
```

## Controller 통합 테스트 템플릿

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("Xxx Controller 통합 테스트")
class XxxControllerIntegrationTest {
    @Autowired private lateinit var webTestClient: WebTestClient
    // 실제 Repository 주입 - @MockkBean 사용 금지!
    // REST Docs 작성 금지!
    // 동일한 테스트 시나리오를 단위 테스트와 함께 작성
}
```

## Service 테스트 템플릿

```kotlin
@ExtendWith(MockKExtension::class)
@DisplayName("Xxx Service 테스트")
class XxxServiceImplTest {
    @MockK private lateinit var xxxRepository: XxxRepository
    @InjectMockKs private lateinit var xxxService: XxxServiceImpl

    @Test
    @DisplayName("유효한 요청 시, 결과를 반환한다")
    fun method_WithValidInput_ReturnsResult() {
        // Given
        every { xxxRepository.findById(any()) } returns Mono.just(entity)

        // When
        val result = xxxService.method(id)

        // Then (StepVerifier 사용!)
        StepVerifier.create(result)
            .assertNext { assertThat(it.id).isEqualTo(entity.id) }
            .verifyComplete()
    }
}
```

## Repository 통합 테스트 템플릿

```kotlin
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Xxx Repository 통합 테스트")
class XxxRepositoryTest {
    @Autowired private lateinit var xxxRepository: XxxRepository
    @Autowired private lateinit var dslContext: DSLContext

    // .block()!! 사용 허용 (테스트에서만!)
    // CRUD, Soft Delete, Audit Trail 검증 필수
}
```

## 이벤트 테스트 구분

### Critical Path (즉시 검증 - Awaitility 불필요)
- 카운트 증가/감소 (메인 체인에 포함)
- user_likes, user_saves, comments 저장

### Non-Critical Path (Awaitility 사용)
- 협업 필터링 데이터 (user_content_interactions)
- 비동기 이벤트로 처리되는 부가 기능

```kotlin
// Critical: 즉시 검증
assertThat(response.likeCount).isEqualTo(1)

// Non-Critical: Awaitility
await.atMost(2, TimeUnit.SECONDS).untilAsserted {
    val interaction = repository.find(userId, contentId).block()
    assertThat(interaction).isNotNull
}
```

## 테스트 작성 체크리스트

- [ ] Controller 단위 + 통합 테스트 모두 작성
- [ ] Service 단위 테스트 작성
- [ ] Repository 통합 테스트 작성
- [ ] Given-When-Then 주석 필수
- [ ] DisplayName 한글로 명확한 시나리오 설명
- [ ] mockUser() 사용 (인증 필요 API)
- [ ] REST Docs는 단위 테스트에서만 작성
- [ ] 모든 테스트 통과 확인
