# GrowSnap Backend Reactive Event 패턴 (Reactor Sinks API)

> WebFlux 환경에서 완전한 Non-blocking 이벤트 처리를 위한 Reactor Sinks API 패턴을 정의합니다.

## 개요

**⚠️ 중요: ApplicationEventPublisher는 WebFlux Anti-Pattern**

Spring의 `ApplicationEventPublisher` + `@EventListener`는 **MVC 환경**을 위해 설계되었으며, **WebFlux에서는 권장되지 않습니다**.

### ApplicationEventPublisher의 문제점

**1. Blocking 방식**
- [Spring 공식 이슈 #21025](https://github.com/spring-projects/spring-framework/issues/21025): "There is no equivalent of reactive ApplicationEventPublisher"
- WebFlux Reactive Chain을 끊어버림

**2. subscribe() Fire-and-Forget 문제**
```kotlin
@EventListener
fun handleEvent(event: MyEvent) {
    repository.save(...).subscribe()  // ❌ 실행 보장 없음!
}
```
- Backpressure 무시
- 에러 전파 안됨
- Reactive Context 유실

**3. Race Condition**
```kotlin
return repository.save(...)
    .doOnSuccess { publishEvent(...) }  // 비동기 처리
    .then(getResponse())  // ← 이벤트 처리 전에 반환! (카운트 누락)
```
→ **프론트엔드에서 좋아요를 눌러도 카운트가 즉시 반영되지 않는 버그**

---

## ✅ WebFlux Best Practice: Reactor Sinks API

### 언제 사용하는가?

- 메인 로직과 독립적으로 실행되어야 하는 **부가 기능**
- 실패해도 메인 요청에 영향을 주지 않아야 하는 작업
- 여러 도메인 간 결합도를 낮추고 싶을 때

### GrowSnap에서의 사용 예시

**사용자가 콘텐츠에 좋아요를 누를 때**:

```
Main Reactive Chain (동기):
├─ user_likes 테이블 저장
├─ content_interactions.like_count 증가  ← 메인 체인에 포함!
├─ eventBus.publish(UserInteractionEvent)
└─ 응답 반환 (정확한 카운트 포함)

Async Event Bus (비동기, 격리):
└─ user_content_interactions 저장 (협업 필터링용)
    └─ onErrorResume (실패해도 메인 로직 영향 없음)
```

**핵심 설계 원칙:**
- ✅ **Critical Path**: 사용자에게 중요한 데이터(카운트)는 **메인 체인에서 동기 처리**
- ✅ **Non-Critical Path**: 부가 기능(협업 필터링)은 **이벤트로 비동기 처리**

---

## Reactor Sinks API 패턴 구현

### 1. 공통 이벤트 인터페이스

```kotlin
package me.onetwo.growsnap.infrastructure.event

import java.time.Instant
import java.util.UUID

/**
 * 도메인 이벤트 기본 인터페이스
 *
 * ## WebFlux Best Practice (2025)
 *
 * Reactor Sinks API를 사용한 완전한 Reactive Event Bus 패턴
 *
 * ### Reactor Sinks API 장점
 * 1. **완전한 Non-blocking**: 모든 이벤트 처리가 reactive
 * 2. **Backpressure 지원**: 시스템 과부하 방지
 * 3. **에러 격리**: onErrorResume으로 실패해도 메인 로직 영향 없음
 * 4. **실행 보장**: Flux 구독으로 확실한 이벤트 처리
 * 5. **Thread-safe**: Sinks.many().multicast()가 동시성 처리
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 */
interface DomainEvent {
    val eventId: UUID
    val occurredAt: Instant
}
```

### 2. Event Bus Configuration

```kotlin
package me.onetwo.growsnap.infrastructure.event

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/**
 * Reactive Event Bus 설정
 *
 * Reactor Sinks API를 사용한 완전한 Non-blocking Event Bus
 *
 * ## Sinks.Many 설정
 * - multicast(): 여러 Subscriber에게 이벤트 브로드캐스트
 * - onBackpressureBuffer(1000): 최대 1000개 이벤트 버퍼링
 *
 * ## Hot Stream
 * - share()로 Hot Stream 변환
 * - 구독 시점과 관계없이 실시간 이벤트 수신
 */
@Configuration
class ReactiveEventBusConfig {

    /**
     * 도메인 이벤트 Sink
     *
     * 이벤트를 발행하는 Publisher 역할
     */
    @Bean
    fun domainEventSink(): Sinks.Many<DomainEvent> =
        Sinks.many()
            .multicast()
            .onBackpressureBuffer(1000)

    /**
     * 도메인 이벤트 Flux
     *
     * 이벤트를 구독하는 Subscriber들이 사용하는 Hot Stream
     */
    @Bean
    fun domainEventFlux(sink: Sinks.Many<DomainEvent>): Flux<DomainEvent> =
        sink.asFlux()
            .share()  // Hot stream으로 변환
}
```

### 3. Event Publisher 추상화

```kotlin
package me.onetwo.growsnap.infrastructure.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Sinks

/**
 * Reactive Event Publisher
 *
 * Reactor Sinks API를 사용한 이벤트 발행
 *
 * ## tryEmitNext vs emitNext
 * - tryEmitNext(): 발행 결과를 EmitResult로 반환 (권장)
 * - emitNext(): 발행 실패 시 예외 발생
 *
 * ## EmitResult 종류
 * - OK: 성공적으로 발행
 * - FAIL_OVERFLOW: 버퍼 오버플로우 (backpressure)
 * - FAIL_CANCELLED: 모든 Subscriber가 취소됨
 * - FAIL_TERMINATED: Sink가 종료됨
 * - FAIL_ZERO_SUBSCRIBER: Subscriber 없음 (정상, 로그 불필요)
 */
@Component
class ReactiveEventPublisher(
    private val domainEventSink: Sinks.Many<DomainEvent>
) {

    /**
     * 이벤트 발행
     *
     * @param event 도메인 이벤트
     */
    fun publish(event: DomainEvent) {
        val result = domainEventSink.tryEmitNext(event)

        if (result.isFailure && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            logger.error(
                "Failed to publish event: type={}, eventId={}, reason={}",
                event::class.simpleName,
                event.eventId,
                result
            )
        } else {
            logger.debug(
                "Event published: type={}, eventId={}",
                event::class.simpleName,
                event.eventId
            )
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReactiveEventPublisher::class.java)
    }
}
```

### 4. 이벤트 정의

```kotlin
package me.onetwo.growsnap.domain.analytics.event

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.infrastructure.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 사용자 인터랙션 이벤트
 *
 * user_content_interactions 테이블 저장용 이벤트 (협업 필터링)
 *
 * ## 처리 흐름
 * 1. 메인 로직: content_interactions 카운트 증가 (동기)
 * 2. 이벤트 발행: UserInteractionEvent
 * 3. 비동기 처리: user_content_interactions 저장
 * 4. 에러 격리: 실패해도 메인 로직 영향 없음
 *
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 * @property interactionType 인터랙션 타입 (LIKE, SAVE, COMMENT)
 */
data class UserInteractionEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val userId: UUID,
    val contentId: UUID,
    val interactionType: InteractionType
) : DomainEvent

/**
 * 콘텐츠 생성 이벤트
 *
 * content_interactions 레코드 초기화용 이벤트
 *
 * @property contentId 콘텐츠 ID
 * @property creatorId 생성자 ID
 */
data class ContentCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val contentId: UUID,
    val creatorId: UUID
) : DomainEvent
```

### 5. Event Subscriber (Reactive)

```kotlin
package me.onetwo.growsnap.domain.analytics.event

import jakarta.annotation.PostConstruct
import me.onetwo.growsnap.domain.analytics.service.UserContentInteractionService
import me.onetwo.growsnap.infrastructure.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 사용자 인터랙션 이벤트 Subscriber
 *
 * Reactor Sinks API를 사용한 완전한 Reactive Event Listener
 *
 * ## Reactive Subscriber 패턴
 * 1. @PostConstruct에서 Flux 구독 시작
 * 2. filter()로 특정 이벤트 타입만 처리
 * 3. flatMap()으로 비동기 처리 (Mono 반환)
 * 4. subscribeOn(boundedElastic())으로 별도 스레드에서 실행
 * 5. onErrorResume()으로 에러 격리 (메인 로직 영향 없음)
 *
 * ## 장애 격리
 * - user_content_interactions 저장 실패해도 메인 로직 성공
 * - onErrorResume으로 예외 흡수
 * - ERROR 로그만 남김
 *
 * ## Thread Safety
 * - Sinks.Many가 내부적으로 동시성 처리
 * - flatMap의 concurrency 설정으로 병렬 처리 제어
 */
@Component
class UserInteractionEventSubscriber(
    private val userContentInteractionService: UserContentInteractionService,
    private val domainEventFlux: Flux<DomainEvent>
) {

    /**
     * 이벤트 구독 시작
     *
     * 애플리케이션 시작 시 자동으로 Flux 구독
     */
    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is UserInteractionEvent }
            .cast(UserInteractionEvent::class.java)
            .flatMap { event ->
                handleUserInteractionEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())  // 별도 스레드
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to handle UserInteractionEvent: " +
                            "userId={}, contentId={}, type={}",
                            event.userId,
                            event.contentId,
                            event.interactionType,
                            error
                        )
                        Mono.empty()  // 에러 격리: 메인 로직에 영향 없음
                    }
            }
            .subscribe()
    }

    /**
     * 사용자 인터랙션 이벤트 처리
     *
     * user_content_interactions 테이블에 저장 (협업 필터링용)
     *
     * @param event 사용자 인터랙션 이벤트
     * @return Mono<Void>
     */
    private fun handleUserInteractionEvent(event: UserInteractionEvent): Mono<Void> {
        logger.debug(
            "Handling UserInteractionEvent: userId={}, contentId={}, type={}",
            event.userId,
            event.contentId,
            event.interactionType
        )

        return userContentInteractionService.saveUserInteraction(
            event.userId,
            event.contentId,
            event.interactionType
        )
        .doOnSuccess {
            logger.debug("UserInteractionEvent handled successfully")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserInteractionEventSubscriber::class.java)
    }
}
```

### 6. Service 구현 (카운트는 메인 체인에 포함)

```kotlin
package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.event.UserInteractionEvent
import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.domain.interaction.dto.LikeResponse
import me.onetwo.growsnap.domain.interaction.repository.UserLikeRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 좋아요 서비스 (Reactor Sinks API 패턴)
 *
 * ## 처리 흐름
 * 1. user_likes 저장 (트랜잭션 내)
 * 2. content_interactions 카운트 증가 (트랜잭션 내, 메인 체인) ← 즉시 반영!
 * 3. 이벤트 발행 (협업 필터링용)
 * 4. 응답 반환 (정확한 카운트 포함)
 * 5. [비동기] user_content_interactions 저장 (실패해도 OK)
 *
 * ## 카운트를 메인 체인에 포함한 이유
 * - ✅ 프론트엔드에 즉시 반영 (Race Condition 해결)
 * - ✅ 사용자에게 중요한 데이터 = Critical Path
 * - ✅ 트랜잭션 보장
 *
 * ## 협업 필터링을 이벤트로 분리한 이유
 * - ✅ 실패해도 메인 로직 영향 없음 (Non-Critical Path)
 * - ✅ 부가 기능이므로 장애 격리
 * - ✅ 추후 확장 가능 (추천 시스템 추가 등)
 */
@Service
class LikeServiceImpl(
    private val userLikeRepository: UserLikeRepository,
    private val contentInteractionService: ContentInteractionService,
    private val eventPublisher: ReactiveEventPublisher
) : LikeService {

    /**
     * 좋아요
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답 (정확한 카운트 포함)
     */
    @Transactional
    override fun likeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
        logger.debug("Liking content: userId={}, contentId={}", userId, contentId)

        return userLikeRepository.exists(userId, contentId)
            .flatMap { exists ->
                if (exists) {
                    logger.debug("Content already liked: userId={}, contentId={}", userId, contentId)
                    getLikeResponse(contentId, true)
                } else {
                    userLikeRepository.save(userId, contentId)
                        .flatMap {
                            // 카운트 증가를 메인 체인에 포함 ← 즉시 반영!
                            contentInteractionService.incrementLikeCount(contentId)
                        }
                        .doOnSuccess {
                            logger.debug("Publishing UserInteractionEvent: userId={}, contentId={}", userId, contentId)
                            // 협업 필터링만 이벤트로 처리 (실패해도 OK)
                            eventPublisher.publish(
                                UserInteractionEvent(
                                    userId = userId,
                                    contentId = contentId,
                                    interactionType = InteractionType.LIKE
                                )
                            )
                        }
                        .then(getLikeResponse(contentId, true))  // ← 카운트 항상 정확!
                }
            }
            .doOnSuccess { logger.debug("Content liked successfully: userId={}, contentId={}", userId, contentId) }
            .doOnError { error ->
                logger.error("Failed to like content: userId={}, contentId={}", userId, contentId, error)
            }
    }

    /**
     * 좋아요 취소
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답
     */
    @Transactional
    override fun unlikeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
        logger.debug("Unliking content: userId={}, contentId={}", userId, contentId)

        return userLikeRepository.exists(userId, contentId)
            .flatMap { exists ->
                if (!exists) {
                    logger.debug("Content not liked: userId={}, contentId={}", userId, contentId)
                    getLikeResponse(contentId, false)
                } else {
                    userLikeRepository.delete(userId, contentId)
                        .flatMap {
                            // 카운트 감소를 메인 체인에 포함
                            contentInteractionService.decrementLikeCount(contentId)
                        }
                        .then(getLikeResponse(contentId, false))
                }
            }
            .doOnSuccess { logger.debug("Content unliked successfully: userId={}, contentId={}", userId, contentId) }
            .doOnError { error ->
                logger.error("Failed to unlike content: userId={}, contentId={}", userId, contentId, error)
            }
    }

    private fun getLikeResponse(contentId: UUID, isLiked: Boolean): Mono<LikeResponse> {
        return contentInteractionService.getLikeCount(contentId)
            .map { likeCount ->
                LikeResponse(
                    contentId = contentId.toString(),
                    likeCount = likeCount,
                    isLiked = isLiked
                )
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LikeServiceImpl::class.java)
    }
}
```

---

## Reactor Sinks API 패턴 체크리스트

### 기본 Event Bus 구성
- [ ] **DomainEvent 인터페이스**: eventId, occurredAt 포함
- [ ] **ReactiveEventBusConfig**: Sinks.Many + Flux.share()
- [ ] **ReactiveEventPublisher**: tryEmitNext()로 이벤트 발행
- [ ] **Event Subscriber**: @PostConstruct에서 Flux 구독
- [ ] **filter() + cast()**: 특정 이벤트 타입만 처리
- [ ] **flatMap()**: 비동기 처리 (Mono 반환)
- [ ] **subscribeOn(boundedElastic())**: 별도 스레드 실행
- [ ] **onErrorResume()**: 에러 격리, 로그만 남김

### Service 구현
- [ ] **카운트는 메인 체인에 포함**: flatMap()으로 순차 처리
- [ ] **협업 필터링만 이벤트로 분리**: eventPublisher.publish()
- [ ] **정확한 응답 반환**: 카운트 증가 후 조회
- [ ] **트랜잭션 보장**: @Transactional

### 테스트
- [ ] **통합 테스트**: 카운트가 즉시 반영되는지 확인
- [ ] **이벤트 처리 확인**: Awaitility로 비동기 처리 대기
- [ ] **장애 격리 검증**: 이벤트 실패해도 메인 로직 성공

---

## ApplicationEventPublisher vs Reactor Sinks API

| 항목 | ApplicationEventPublisher | Reactor Sinks API |
|------|--------------------------|-------------------|
| **환경** | Spring MVC | Spring WebFlux |
| **Blocking** | ✅ Blocking | ✅ Non-blocking |
| **Backpressure** | ❌ 없음 | ✅ 지원 |
| **실행 보장** | ❌ subscribe() fire-and-forget | ✅ Flux 구독 보장 |
| **에러 전파** | ❌ 안됨 | ✅ onErrorResume |
| **Race Condition** | ❌ 발생 가능 | ✅ 해결 (카운트 메인 체인) |
| **Spring 지원** | ✅ 공식 지원 | ⚠️ 직접 구현 필요 |
| **복잡도** | 낮음 | 중간 |
| **WebFlux 권장** | ❌ Anti-pattern | ✅ Best Practice |

---

## 주의사항

### 1. 카운트는 메인 체인에 포함

**Critical Path** (사용자에게 중요):
```kotlin
return userLikeRepository.save(...)
    .flatMap {
        contentInteractionService.incrementLikeCount(...)  // ← 메인 체인
    }
    .doOnSuccess { eventPublisher.publish(...) }  // ← 협업 필터링만 이벤트
    .then(getLikeResponse(...))  // ← 카운트 항상 정확!
```

### 2. 에러 격리 필수

**Non-Critical Path** (부가 기능):
```kotlin
domainEventFlux
    .flatMap { event ->
        handleEvent(event)
            .onErrorResume { error ->  // ← 에러 격리
                logger.error("Event failed", error)
                Mono.empty()  // 메인 로직에 영향 없음
            }
    }
    .subscribe()
```

### 3. 멱등성 보장

```kotlin
// ✅ GOOD: UNIQUE 제약 조건
CREATE TABLE user_content_interactions (
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    interaction_type VARCHAR(20) NOT NULL,
    UNIQUE KEY unique_interaction (user_id, content_id, interaction_type)
);
```

### 4. Sinks EmitResult 처리

```kotlin
val result = sink.tryEmitNext(event)
if (result.isFailure && result != FAIL_ZERO_SUBSCRIBER) {
    logger.error("Failed to emit: {}", result)
}
```

---

## 정리

### Reactor Sinks API 핵심 원칙

1. **Event Bus**: Sinks.Many + Flux.share()로 Hot Stream 구성
2. **Event Publisher**: tryEmitNext()로 이벤트 발행, EmitResult 확인
3. **Event Subscriber**: @PostConstruct에서 Flux 구독, filter/cast/flatMap 체인
4. **에러 격리**: onErrorResume()으로 메인 로직 보호
5. **카운트는 메인 체인**: Critical Path는 동기 처리 (즉시 반영)
6. **협업 필터링은 이벤트**: Non-Critical Path는 비동기 처리 (실패 격리)
7. **로깅**: DEBUG/ERROR 레벨로 추적

### 언제 사용하는가?

- ✅ **부가 기능**: 협업 필터링, 알림, 로그, 추천 시스템
- ✅ **실패 격리**: 실패해도 메인 로직 영향 없는 작업
- ✅ **도메인 분리**: 낮은 결합도 유지
- ❌ **Critical Path**: 사용자에게 중요한 데이터는 메인 체인에서 동기 처리

### WebFlux 환경에서의 올바른 이벤트 처리

```
✅ Reactor Sinks API (2025 Best Practice)
❌ ApplicationEventPublisher + @EventListener (MVC Pattern, WebFlux Anti-Pattern)
```
