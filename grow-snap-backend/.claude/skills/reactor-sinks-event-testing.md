# Reactor Sinks API 이벤트 테스트 가이드

## 개요

**WebFlux 환경에서 Reactor Sinks API를 사용한 완전한 Reactive 이벤트 처리**를 테스트하는 방법을 설명합니다.

### ⚠️ 기존 패턴과의 차이점

**기존 (ApplicationEventPublisher + @EventListener):**
```kotlin
@EventListener
@Async
fun handleEvent(event: MyEvent) {
    repository.save(...).subscribe()  // ❌ Fire-and-forget
}
```
- 문제: Race condition, 실행 보장 없음, backpressure 무시

**새로운 (Reactor Sinks API):**
```kotlin
@PostConstruct
fun subscribe() {
    domainEventFlux
        .filter { it is MyEvent }
        .flatMap { handleEvent(it) }  // ✅ Reactive chain
        .subscribe()
}
```
- 해결: 완전한 Non-blocking, backpressure 지원, 에러 격리

---

## 필수 의존성

```kotlin
testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
testImplementation("io.projectreactor:reactor-test:3.6.0")
```

---

## Reactor Sinks API 이벤트 아키텍처

### 1. Event Bus Configuration

```kotlin
@Configuration
class ReactiveEventBusConfig {
    @Bean
    fun domainEventSink(): Sinks.Many<DomainEvent> =
        Sinks.many()
            .multicast()
            .onBackpressureBuffer(1000)

    @Bean
    fun domainEventFlux(sink: Sinks.Many<DomainEvent>): Flux<DomainEvent> =
        sink.asFlux().share()
}
```

### 2. Event Publisher

```kotlin
@Component
class ReactiveEventPublisher(
    private val domainEventSink: Sinks.Many<DomainEvent>
) {
    fun publish(event: DomainEvent) {
        domainEventSink.tryEmitNext(event)
    }
}
```

### 3. Event Subscriber

```kotlin
@Component
class UserInteractionEventSubscriber(
    private val userContentInteractionService: UserContentInteractionService,
    private val domainEventFlux: Flux<DomainEvent>
) {
    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is UserInteractionEvent }
            .cast(UserInteractionEvent::class.java)
            .flatMap { event ->
                userContentInteractionService.saveUserInteraction(...)
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume { error ->
                        logger.error("Event failed", error)
                        Mono.empty()  // 에러 격리
                    }
            }
            .subscribe()
    }
}
```

### 4. Service (카운트는 메인 체인에 포함)

```kotlin
@Service
class LikeServiceImpl(
    private val userLikeRepository: UserLikeRepository,
    private val contentInteractionService: ContentInteractionService,
    private val eventPublisher: ReactiveEventPublisher
) {
    @Transactional
    override fun likeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
        return userLikeRepository.save(userId, contentId)
            .flatMap {
                // 카운트 증가를 메인 체인에 포함 ← 즉시 반영
                contentInteractionService.incrementLikeCount(contentId)
            }
            .doOnSuccess {
                // 협업 필터링만 이벤트로 처리 (실패해도 OK)
                eventPublisher.publish(
                    UserInteractionEvent(userId, contentId, InteractionType.LIKE)
                )
            }
            .then(getLikeResponse(contentId, true))  // ← 카운트 항상 정확!
    }
}
```

---

## 동기 데이터 테스트 (카운트)

### 카운트는 메인 체인에 포함 → 즉시 확인 가능

카운트 증가/감소는 **메인 Reactive Chain**에 포함되어 있으므로 **Awaitility 불필요**합니다.

```kotlin
@Test
fun `좋아요 추가 시 카운트가 즉시 증가한다`() {
    // Given
    val user = userRepository.save(User(...)).block()!!
    val content = contentRepository.save(Content(...)).block()!!

    // When: 좋아요 API 호출
    val response = webTestClient
        .mutateWith(mockUser(user.id!!))
        .post()
        .uri("/api/v1/contents/${content.id}/like")
        .exchange()
        .expectStatus().isOk
        .expectBody(LikeResponse::class.java)
        .returnResult()
        .responseBody!!

    // Then: 즉시 확인 가능 (Awaitility 불필요!)
    assertThat(response.likeCount).isEqualTo(1)
    assertThat(response.isLiked).isTrue()

    // DB 확인
    val interaction = contentInteractionRepository
        .findByContentId(UUID.fromString(content.id))
        .block()
    assertThat(interaction!!.likeCount).isEqualTo(1)
}
```

**왜 즉시 확인 가능한가?**
- ✅ 카운트 증가가 **메인 체인**에 포함됨 (`flatMap`)
- ✅ 트랜잭션이 커밋된 후 응답 반환
- ✅ Race Condition 해결

---

## 비동기 이벤트 테스트 (협업 필터링)

### 협업 필터링은 이벤트로 처리 → Awaitility 사용

`user_content_interactions` 저장은 **비동기 이벤트**로 처리되므로 **Awaitility 필요**합니다.

```kotlin
@Test
fun `좋아요 추가 시 협업 필터링 데이터가 비동기로 저장된다`() {
    // Given
    val user = userRepository.save(User(...)).block()!!
    val content = contentRepository.save(Content(...)).block()!!

    // When: 좋아요 API 호출
    webTestClient
        .mutateWith(mockUser(user.id!!))
        .post()
        .uri("/api/v1/contents/${content.id}/like")
        .exchange()
        .expectStatus().isOk

    // Then: Awaitility로 비동기 처리 대기
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val userInteraction = userContentInteractionRepository
            .findByUserIdAndContentId(user.id!!, UUID.fromString(content.id))
            .block()

        assertThat(userInteraction).isNotNull
        assertThat(userInteraction!!.interactionType).isEqualTo(InteractionType.LIKE)
    }
}
```

---

## StepVerifier를 사용한 Reactive 테스트

### Mono/Flux 반환 메서드 테스트

```kotlin
@Test
fun `Mono 반환 메서드 테스트 - StepVerifier 사용`() {
    // Given
    val userId = UUID.randomUUID()
    val contentId = UUID.randomUUID()

    // When & Then
    StepVerifier.create(
        likeService.likeContent(userId, contentId)
    )
    .assertNext { response ->
        assertThat(response.likeCount).isEqualTo(1)
        assertThat(response.isLiked).isTrue()
    }
    .verifyComplete()
}
```

### 에러 처리 테스트

```kotlin
@Test
fun `이벤트 처리 실패해도 메인 로직은 성공한다`() {
    // Given: Repository를 Mock으로 대체하여 이벤트 처리 실패 시뮬레이션
    val userInteractionService = mock(UserContentInteractionService::class.java)
    `when`(userInteractionService.saveUserInteraction(any(), any(), any()))
        .thenReturn(Mono.error(RuntimeException("Database error")))

    // When: 좋아요 API 호출
    val response = webTestClient
        .mutateWith(mockUser(userId))
        .post()
        .uri("/api/v1/contents/${contentId}/like")
        .exchange()
        .expectStatus().isOk  // ← 메인 로직은 성공!
        .expectBody(LikeResponse::class.java)
        .returnResult()
        .responseBody!!

    // Then: 좋아요와 카운트는 정상 처리됨
    assertThat(response.isLiked).isTrue()
    assertThat(response.likeCount).isEqualTo(1)

    // 협업 필터링 데이터는 저장 안됨 (에러 격리)
    await.pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted {
        val userInteraction = userContentInteractionRepository
            .findByUserIdAndContentId(userId, contentId)
            .block()

        assertThat(userInteraction).isNull()  // 이벤트 처리 실패
    }
}
```

---

## 통합 테스트 패턴

### 1. 전체 플로우 테스트

```kotlin
@Test
fun `좋아요 전체 플로우 테스트 - 카운트와 협업 필터링`() {
    // Given
    val user = userRepository.save(User(...)).block()!!
    val content = contentRepository.save(Content(...)).block()!!

    // When: 좋아요 API 호출
    val response = webTestClient
        .mutateWith(mockUser(user.id!!))
        .post()
        .uri("/api/v1/contents/${content.id}/like")
        .exchange()
        .expectStatus().isOk
        .expectBody(LikeResponse::class.java)
        .returnResult()
        .responseBody!!

    // Then 1: 카운트는 즉시 확인 가능
    assertThat(response.likeCount).isEqualTo(1)
    assertThat(response.isLiked).isTrue()

    // Then 2: user_likes 저장 확인
    val userLike = userLikeRepository
        .findByUserIdAndContentId(user.id!!, UUID.fromString(content.id))
        .block()
    assertThat(userLike).isNotNull

    // Then 3: content_interactions 카운트 확인
    val interaction = contentInteractionRepository
        .findByContentId(UUID.fromString(content.id))
        .block()
    assertThat(interaction!!.likeCount).isEqualTo(1)

    // Then 4: 협업 필터링 데이터 확인 (비동기, Awaitility 사용)
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val userInteraction = userContentInteractionRepository
            .findByUserIdAndContentId(user.id!!, UUID.fromString(content.id))
            .block()

        assertThat(userInteraction).isNotNull
        assertThat(userInteraction!!.interactionType).isEqualTo(InteractionType.LIKE)
    }
}
```

### 2. 이벤트 체인 테스트

```kotlin
@Test
fun `여러 이벤트가 순차적으로 발생하는 경우`() {
    // Given
    val user = userRepository.save(User(...)).block()!!

    // When 1: 콘텐츠 생성
    val content = webTestClient
        .mutateWith(mockUser(user.id!!))
        .post()
        .uri("/api/v1/contents")
        .bodyValue(createRequest)
        .exchange()
        .expectStatus().isCreated
        .expectBody(ContentResponse::class.java)
        .returnResult()
        .responseBody!!

    // Then 1: content_interactions 초기화 (비동기 이벤트)
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val interaction = contentInteractionRepository
            .findByContentId(UUID.fromString(content.id))
            .block()

        assertThat(interaction).isNotNull
        assertThat(interaction!!.likeCount).isEqualTo(0)
    }

    // When 2: 좋아요 추가
    webTestClient
        .mutateWith(mockUser(user.id!!))
        .post()
        .uri("/api/v1/contents/${content.id}/like")
        .exchange()
        .expectStatus().isOk

    // Then 2: 카운트 증가 (즉시)
    val interaction = contentInteractionRepository
        .findByContentId(UUID.fromString(content.id))
        .block()
    assertThat(interaction!!.likeCount).isEqualTo(1)

    // Then 3: 협업 필터링 데이터 저장 (비동기)
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val userInteraction = userContentInteractionRepository
            .findByUserIdAndContentId(user.id!!, UUID.fromString(content.id))
            .block()

        assertThat(userInteraction).isNotNull
    }
}
```

---

## Awaitility 사용법

### 기본 패턴

```kotlin
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import java.util.concurrent.TimeUnit

// 기본 패턴
await.atMost(2, TimeUnit.SECONDS).untilAsserted {
    // 검증 로직
    assertThat(result).isNotNull()
}

// 조건이 true가 될 때까지 대기
await.atMost(2, TimeUnit.SECONDS).until {
    repository.exists(id).block() == true
}

// Polling Interval 설정
await
    .atMost(5, TimeUnit.SECONDS)
    .pollInterval(100, TimeUnit.MILLISECONDS)
    .untilAsserted {
        assertThat(counter).isGreaterThan(10)
    }
```

### 언제 Awaitility를 사용하는가?

**✅ 사용해야 하는 경우:**
- 협업 필터링 데이터 저장 (`user_content_interactions`)
- 비동기 이벤트로 처리되는 부가 기능
- 알림, 로그, 추천 시스템 업데이트

**❌ 사용하지 않아도 되는 경우:**
- 카운트 증가/감소 (메인 체인에 포함)
- user_likes, user_saves, comments 테이블 저장 (메인 로직)
- content_interactions 테이블 업데이트 (메인 로직)

---

## 베스트 프랙티스

### 1. 카운트는 즉시 검증, 협업 필터링은 Awaitility

```kotlin
// ✅ 좋은 예
@Test
fun `카운트와 협업 필터링 분리 테스트`() {
    // When
    val response = likeContent(userId, contentId)

    // Then 1: 카운트는 즉시 확인
    assertThat(response.likeCount).isEqualTo(1)

    // Then 2: 협업 필터링은 Awaitility
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        assertThat(findUserInteraction(userId, contentId)).isNotNull()
    }
}

// ❌ 나쁜 예: 카운트도 Awaitility 사용 (불필요)
@Test
fun `카운트를 Awaitility로 대기`() {
    val response = likeContent(userId, contentId)

    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        assertThat(response.likeCount).isEqualTo(1)  // 불필요한 대기
    }
}
```

### 2. 적절한 타임아웃 설정

```kotlin
// ✅ 좋은 예: 충분한 시간 (2-5초)
await.atMost(2, TimeUnit.SECONDS).untilAsserted { ... }

// ❌ 나쁜 예: 너무 짧음 (불안정)
await.atMost(100, TimeUnit.MILLISECONDS).untilAsserted { ... }

// ❌ 나쁜 예: 너무 김 (테스트 느려짐)
await.atMost(30, TimeUnit.SECONDS).untilAsserted { ... }
```

### 3. 명확한 검증

```kotlin
// ✅ 좋은 예: 구체적인 검증
await.atMost(2, TimeUnit.SECONDS).untilAsserted {
    val interaction = userContentInteractionRepository
        .findByUserIdAndContentId(userId, contentId)
        .block()

    assertThat(interaction).isNotNull
    assertThat(interaction!!.interactionType).isEqualTo(InteractionType.LIKE)
    assertThat(interaction.createdAt).isNotNull()
}

// ❌ 나쁜 예: 불명확한 검증
await.atMost(2, TimeUnit.SECONDS).until {
    true  // 항상 true
}
```

### 4. 장애 격리 테스트

```kotlin
@Test
fun `협업 필터링 실패해도 메인 로직은 성공한다`() {
    // Given: Mock으로 이벤트 처리 실패 시뮬레이션
    // ...

    // When: API 호출
    val response = likeContent(userId, contentId)

    // Then 1: 메인 로직은 성공
    assertThat(response.isLiked).isTrue()
    assertThat(response.likeCount).isEqualTo(1)

    // Then 2: 좋아요 데이터는 저장됨
    val userLike = findUserLike(userId, contentId)
    assertThat(userLike).isNotNull()

    // Then 3: 카운트도 증가됨
    val interaction = findContentInteraction(contentId)
    assertThat(interaction!!.likeCount).isEqualTo(1)

    // Then 4: 협업 필터링만 실패 (에러 격리)
    await.pollDelay(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted {
        val userInteraction = findUserInteraction(userId, contentId)
        assertThat(userInteraction).isNull()  // 이벤트 처리 실패
    }
}
```

---

## 트러블슈팅

### 문제: 카운트가 반영되지 않음

**원인:** 카운트가 이벤트로 처리되고 있음 (기존 패턴)

**해결:** 카운트를 메인 체인으로 이동

```kotlin
// ❌ 나쁜 예 (기존): 카운트가 이벤트로 처리
return userLikeRepository.save(...)
    .doOnSuccess { publishEvent(LikeCreatedEvent(...)) }  // 비동기
    .then(getLikeResponse(...))  // 카운트가 아직 증가 안됨

// ✅ 좋은 예 (새로운): 카운트가 메인 체인에 포함
return userLikeRepository.save(...)
    .flatMap { contentInteractionService.incrementLikeCount(...) }  // 동기
    .doOnSuccess { publishEvent(UserInteractionEvent(...)) }  // 협업 필터링만 이벤트
    .then(getLikeResponse(...))  // 카운트 항상 정확!
```

### 문제: 협업 필터링 데이터가 저장되지 않음

**원인 1: Subscriber가 구독하지 않음**

```kotlin
// ❌ 나쁜 예: @PostConstruct 누락
@Component
class EventSubscriber(
    private val domainEventFlux: Flux<DomainEvent>
) {
    // subscribe() 호출 없음!
}

// ✅ 좋은 예: @PostConstruct에서 구독
@Component
class EventSubscriber(
    private val domainEventFlux: Flux<DomainEvent>
) {
    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is UserInteractionEvent }
            .flatMap { handleEvent(it) }
            .subscribe()
    }
}
```

**원인 2: 에러 격리로 인해 예외가 로그로만 남음**

```kotlin
// 해결: 로그 확인
logger.error("Failed to handle event", error)  // 에러 원인 확인
```

### 문제: Awaitility 타임아웃

**해결 1: 타임아웃 증가**

```kotlin
await.atMost(5, TimeUnit.SECONDS).untilAsserted { ... }
```

**해결 2: Polling Interval 조정**

```kotlin
await
    .atMost(5, TimeUnit.SECONDS)
    .pollInterval(50, TimeUnit.MILLISECONDS)  // 더 자주 확인
    .untilAsserted { ... }
```

**해결 3: 로깅 추가하여 원인 파악**

```kotlin
@PostConstruct
fun subscribe() {
    domainEventFlux
        .doOnNext { logger.info("Event received: $it") }
        .filter { it is UserInteractionEvent }
        .flatMap { event ->
            handleEvent(event)
                .doOnSuccess { logger.info("Event processed successfully") }
                .doOnError { logger.error("Event processing failed", it) }
        }
        .subscribe()
}
```

---

## 정리

### Reactor Sinks API 테스트 핵심

1. **카운트는 즉시 검증**: 메인 체인에 포함되어 있으므로 Awaitility 불필요
2. **협업 필터링은 Awaitility**: 비동기 이벤트로 처리되므로 대기 필요
3. **장애 격리 검증**: 이벤트 실패해도 메인 로직 성공 확인
4. **StepVerifier 활용**: Mono/Flux 반환 메서드는 StepVerifier로 테스트
5. **명확한 검증**: 각 단계별로 명확하게 검증
6. **적절한 타임아웃**: 보통 2-5초 설정

### 테스트 전략

```
Main Logic (즉시 검증):
├─ user_likes 저장 ✓
├─ content_interactions 카운트 증가 ✓
└─ 응답 검증 ✓

Async Events (Awaitility 사용):
└─ user_content_interactions 저장 ✓
    └─ 에러 격리 검증 ✓
```

### ApplicationEventPublisher vs Reactor Sinks API 테스트 차이

| 테스트 대상 | ApplicationEventPublisher | Reactor Sinks API |
|-----------|--------------------------|-------------------|
| **카운트** | Awaitility 필요 (비동기) | 즉시 검증 (동기) |
| **협업 필터링** | Awaitility 필요 | Awaitility 필요 |
| **장애 격리** | 어려움 (Race condition) | 명확함 (메인 체인 분리) |
| **테스트 복잡도** | 높음 (모두 비동기) | 낮음 (Critical Path 동기) |
