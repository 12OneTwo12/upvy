# Spring 이벤트 테스트 가이드

## 개요

Spring의 `@EventListener`와 `@TransactionalEventListener`를 사용한 비동기 이벤트 처리를 테스트하는 방법을 설명합니다.

## 필수 의존성

```kotlin
testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
```

## 이벤트 기반 아키텍처

### 이벤트 발행 (Publisher)

```kotlin
@Service
class LikeService(
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun like(userId: UUID, contentId: UUID) {
        // 비즈니스 로직...

        // 이벤트 발행
        applicationEventPublisher.publishEvent(
            ContentLikedEvent(contentId = contentId, userId = userId)
        )
    }
}
```

### 이벤트 리스너 (Subscriber)

```kotlin
@Component
class UserInteractionEventListener(
    private val userContentInteractionRepository: UserContentInteractionRepository
) {

    @EventListener
    @Async
    fun handleContentLiked(event: ContentLikedEvent) {
        // 비동기로 이벤트 처리
        userContentInteractionRepository.recordInteraction(
            userId = event.userId,
            contentId = event.contentId,
            type = InteractionType.LIKE
        )
    }
}
```

## 동기 이벤트 테스트

### @EventListener (동기)

`@EventListener`는 기본적으로 **동기**로 실행됩니다. `@Async`가 없으면 즉시 실행됩니다.

```kotlin
@Test
fun `동기 이벤트 테스트`() {
    // Given
    val user = userRepository.save(User(...))
    val content = contentRepository.save(Content(...))

    // When: API 호출
    webTestClient
        .mutateWith(mockUser(user!!.id!!))
        .post()
        .uri("/api/v1/contents/${content!!.id}/like")
        .exchange()
        .expectStatus().isOk

    // Then: 즉시 확인 가능
    val interaction = userContentInteractionRepository.findByUserIdAndContentId(
        user.id!!, content.id!!
    )
    assertThat(interaction).isNotNull
    assertThat(interaction!!.type).isEqualTo(InteractionType.LIKE)
}
```

## 비동기 이벤트 테스트 with Awaitility

### @EventListener + @Async (비동기)

`@Async`가 붙은 이벤트 리스너는 **비동기**로 실행됩니다. Awaitility를 사용하여 테스트합니다.

```kotlin
@Test
fun `비동기 이벤트 테스트`() {
    // Given
    val user = userRepository.save(User(...))
    val content = contentRepository.save(Content(...))

    // When: API 호출
    webTestClient
        .mutateWith(mockUser(user!!.id!!))
        .post()
        .uri("/api/v1/contents/${content!!.id}/like")
        .exchange()
        .expectStatus().isOk

    // Then: Awaitility로 비동기 처리 대기
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val interaction = userContentInteractionRepository.findByUserIdAndContentId(
            user.id!!, content.id!!
        )
        assertThat(interaction).isNotNull
        assertThat(interaction!!.type).isEqualTo(InteractionType.LIKE)
    }
}
```

### Awaitility 기본 사용법

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
    repository.exists(id)
}

// Pollling Interval 설정
await
    .atMost(5, TimeUnit.SECONDS)
    .pollInterval(100, TimeUnit.MILLISECONDS)
    .untilAsserted {
        assertThat(counter).isGreaterThan(10)
    }
```

## @TransactionalEventListener 테스트

### AFTER_COMMIT (트랜잭션 커밋 후 실행)

`@TransactionalEventListener`는 트랜잭션이 완료된 후에 이벤트를 처리합니다.

```kotlin
@Component
class ContentEventHandler(
    private val contentMetadataRepository: ContentMetadataRepository
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleContentCreated(event: ContentCreatedEvent) {
        contentMetadataRepository.save(
            ContentMetadata(contentId = event.contentId, likeCount = 0)
        )
    }
}

// 테스트
@Test
@Transactional
@Rollback
fun `트랜잭션 이벤트 테스트`() {
    // Given
    val user = userRepository.save(User(...))

    // When: API 호출
    val response = webTestClient
        .mutateWith(mockUser(user!!.id!!))
        .post()
        .uri("/api/v1/contents")
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated
        .expectBody(ContentResponse::class.java)
        .returnResult()
        .responseBody!!

    // Then: 트랜잭션 커밋 후 처리되므로 Awaitility 사용
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val metadata = contentMetadataRepository.findByContentId(
            UUID.fromString(response.id)
        )
        assertThat(metadata).isNotNull
        assertThat(metadata!!.likeCount).isEqualTo(0)
    }
}
```

## 통합 테스트에서 이벤트 테스트 패턴

### 1. 이벤트 발행 확인

```kotlin
@Test
fun `이벤트가 발행되는지 확인`() {
    // Given
    val user = userRepository.save(User(...))
    val content = contentRepository.save(Content(...))

    // When: API 호출
    webTestClient
        .mutateWith(mockUser(user!!.id!!))
        .post()
        .uri("/api/v1/contents/${content!!.id}/like")
        .exchange()
        .expectStatus().isOk

    // Then: 이벤트 처리 결과 확인 (DB 변화)
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val like = userLikeRepository.findByUserIdAndContentId(user.id!!, content.id!!)
        assertThat(like).isNotNull
    }
}
```

### 2. 이벤트 체인 테스트

여러 이벤트가 순차적으로 발생하는 경우:

```kotlin
@Test
fun `이벤트 체인 테스트`() {
    // Given
    val user = userRepository.save(User(...))
    val content = contentRepository.save(Content(...))

    // When: 팔로우 API 호출
    webTestClient
        .mutateWith(mockUser(user!!.id!!))
        .post()
        .uri("/api/v1/users/${content.creatorId}/follow")
        .exchange()
        .expectStatus().isOk

    // Then: 팔로우 이벤트 처리 확인
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val follow = followRepository.findByFollowerIdAndFollowingId(
            user.id!!, content.creatorId!!
        )
        assertThat(follow).isNotNull
    }

    // And: 프로필 업데이트 이벤트 처리 확인
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val profile = userProfileRepository.findByUserId(content.creatorId!!)
        assertThat(profile!!.followerCount).isEqualTo(1)
    }
}
```

### 3. 이벤트 실패 처리 테스트

```kotlin
@Test
fun `이벤트 처리 실패 시 원본 트랜잭션은 성공해야 함`() {
    // Given: 이벤트 리스너가 실패하도록 설정
    // (별도의 서비스 mock 필요)

    // When: API 호출
    webTestClient
        .mutateWith(mockUser(userId))
        .post()
        .uri("/api/v1/contents/${contentId}/like")
        .exchange()
        .expectStatus().isOk  // 원본 요청은 성공

    // Then: 좋아요는 저장되었지만, 이벤트 처리는 실패
    val like = userLikeRepository.findByUserIdAndContentId(userId, contentId)
    assertThat(like).isNotNull

    // 이벤트 처리 결과는 없음 (실패했으므로)
    await.atMost(2, TimeUnit.SECONDS).untilAsserted {
        val interaction = userContentInteractionRepository.findByUserIdAndContentId(
            userId, contentId
        )
        assertThat(interaction).isNull()
    }
}
```

## Awaitility 고급 사용법

### 1. 커스텀 실패 메시지

```kotlin
await
    .atMost(2, TimeUnit.SECONDS)
    .failFast("좋아요 레코드가 생성되지 않았습니다")
    .untilAsserted {
        val like = userLikeRepository.findByUserIdAndContentId(userId, contentId)
        assertThat(like).isNotNull
    }
```

### 2. 조건부 대기

```kotlin
await.atMost(5, TimeUnit.SECONDS).until {
    val metadata = contentMetadataRepository.findByContentId(contentId)
    metadata != null && metadata.likeCount > 0
}
```

### 3. Ignore Exception

일부 예외를 무시하고 계속 재시도:

```kotlin
await
    .atMost(2, TimeUnit.SECONDS)
    .ignoreExceptions()
    .untilAsserted {
        val like = userLikeRepository.findByUserIdAndContentId(userId, contentId)
        assertThat(like).isNotNull
    }
```

## 베스트 프랙티스

### 1. 적절한 타임아웃 설정

```kotlin
// ✅ 좋은 예: 충분한 시간 (2-5초)
await.atMost(2, TimeUnit.SECONDS).untilAsserted { ... }

// ❌ 나쁜 예: 너무 짧음 (불안정)
await.atMost(100, TimeUnit.MILLISECONDS).untilAsserted { ... }

// ❌ 나쁜 예: 너무 김 (테스트 느려짐)
await.atMost(30, TimeUnit.SECONDS).untilAsserted { ... }
```

### 2. 명확한 검증

```kotlin
// ✅ 좋은 예: 구체적인 검증
await.atMost(2, TimeUnit.SECONDS).untilAsserted {
    val like = userLikeRepository.findByUserIdAndContentId(userId, contentId)
    assertThat(like).isNotNull
    assertThat(like!!.createdAt).isNotNull()
}

// ❌ 나쁜 예: 불명확한 검증
await.atMost(2, TimeUnit.SECONDS).until {
    true  // 항상 true
}
```

### 3. 테스트 독립성

각 테스트는 독립적이어야 하며, 이벤트 처리 완료를 기다려야 합니다.

```kotlin
@Test
@Transactional
@Rollback
fun test1() {
    // 이벤트 처리 대기
    await.atMost(2, TimeUnit.SECONDS).untilAsserted { ... }
    // 트랜잭션 롤백
}

@Test
@Transactional
@Rollback
fun test2() {
    // 독립적으로 실행
    await.atMost(2, TimeUnit.SECONDS).untilAsserted { ... }
}
```

## 주의사항

1. **비동기 이벤트에만 Awaitility 사용**: 동기 이벤트는 즉시 확인 가능
2. **적절한 타임아웃**: 너무 짧거나 길지 않게 (보통 2-5초)
3. **명확한 검증**: 무엇을 기다리는지 명확하게 검증
4. **@Transactional + @Rollback**: 테스트 데이터 자동 정리
5. **@Async 활성화**: `@EnableAsync`가 설정되어 있어야 비동기 이벤트 처리

## 트러블슈팅

### 문제: 이벤트가 처리되지 않음

```kotlin
// 원인 1: @EnableAsync 누락
@SpringBootApplication
@EnableAsync  // 추가 필요
class Application

// 원인 2: @Async 누락
@EventListener
@Async  // 추가 필요
fun handleEvent(event: MyEvent) { ... }

// 원인 3: 트랜잭션 미완료
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handleEvent(event: MyEvent) {
    // 트랜잭션이 커밋되어야 실행됨
}
```

### 문제: Awaitility 타임아웃

```kotlin
// 해결 1: 타임아웃 증가
await.atMost(5, TimeUnit.SECONDS).untilAsserted { ... }

// 해결 2: Polling Interval 조정
await
    .atMost(5, TimeUnit.SECONDS)
    .pollInterval(50, TimeUnit.MILLISECONDS)  // 더 자주 확인
    .untilAsserted { ... }

// 해결 3: 로깅 추가하여 원인 파악
@EventListener
@Async
fun handleEvent(event: MyEvent) {
    logger.info("Event received: $event")
    // 처리 로직
    logger.info("Event processed")
}
```
