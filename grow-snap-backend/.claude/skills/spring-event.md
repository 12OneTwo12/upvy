# GrowSnap Backend Spring Event 패턴

> 비동기 이벤트 처리를 위한 Spring Event 패턴을 정의합니다.

## 개요

Spring Event는 애플리케이션 내에서 비동기 이벤트 기반 통신을 구현하는 패턴입니다.

### 언제 사용하는가?

- 메인 트랜잭션과 독립적으로 실행되어야 하는 작업
- 실패해도 메인 요청에 영향을 주지 않아야 하는 작업
- 여러 도메인 간 결합도를 낮추고 싶을 때

### GrowSnap에서의 사용 예시

**사용자가 콘텐츠에 좋아요를 누를 때**:

1. **메인 트랜잭션**: `content_interactions.like_count` 증가
2. **Spring Event 발행**: `UserInteractionEvent`
3. **비동기 처리**: `user_content_interactions` 테이블에 저장 (협업 필터링용)

## Spring Event 패턴 Best Practice

### 1. 이벤트 클래스 정의

```kotlin
/**
 * 사용자 인터랙션 이벤트
 *
 * 사용자의 콘텐츠 인터랙션 (LIKE, SAVE, SHARE)을 기록하기 위한 이벤트입니다.
 *
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 * @property interactionType 인터랙션 타입 (LIKE, SAVE, SHARE)
 */
data class UserInteractionEvent(
    val userId: UUID,
    val contentId: UUID,
    val interactionType: InteractionType
)
```

**이벤트 클래스 규칙**:
- ✅ **data class 사용**: 불변 객체로 정의
- ✅ **필요한 최소 정보만 포함**: 과도한 정보 전달 금지
- ✅ **명확한 이름**: 이벤트 이름만 보고 무엇을 의미하는지 파악 가능
- ✅ **KDoc 작성**: 이벤트의 목적과 사용 시점 명시

### 2. 이벤트 발행자 (Publisher) - R2DBC Reactive

```kotlin
@Service
class AnalyticsServiceImpl(
    private val contentInteractionRepository: ContentInteractionRepository,
    private val applicationEventPublisher: ApplicationEventPublisher  // Spring 제공
) : AnalyticsService {

    /**
     * 사용자 인터랙션 이벤트 추적 (Reactive)
     *
     * ### 처리 흐름 (R2DBC)
     * 1. Reactive Repository로 카운터 증가 (Mono<Void> 반환)
     * 2. doOnSuccess: 트랜잭션 성공 시 이벤트 발행
     * 3. @TransactionalEventListener(AFTER_COMMIT)이 트랜잭션 커밋 후 이벤트 수신
     *
     * @param userId 사용자 ID
     * @param request 인터랙션 이벤트 요청
     * @return Mono<Void> - 완료 신호
     */
    override fun trackInteractionEvent(userId: UUID, request: InteractionEventRequest): Mono<Void> {
        val contentId = request.contentId!!
        val interactionType = request.interactionType!!

        // 1. 메인 트랜잭션: R2DBC Repository로 카운터 증가 (Reactive)
        val incrementCounter = when (interactionType) {
            InteractionType.LIKE -> contentInteractionRepository.incrementLikeCount(contentId)
            InteractionType.SAVE -> contentInteractionRepository.incrementSaveCount(contentId)
            InteractionType.SHARE -> contentInteractionRepository.incrementShareCount(contentId)
        }

        // 2. 이벤트 발행 (doOnSuccess: 메인 트랜잭션 성공 시에만 발행)
        return incrementCounter.doOnSuccess {
            logger.debug(
                "Publishing UserInteractionEvent: userId={}, contentId={}, type={}",
                userId,
                contentId,
                interactionType
            )
            // Spring Event 발행 (동기 호출)
            applicationEventPublisher.publishEvent(
                UserInteractionEvent(
                    userId = userId,
                    contentId = contentId,
                    interactionType = interactionType
                )
            )
        }.then()  // ✅ Mono<Void> 반환
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AnalyticsServiceImpl::class.java)
    }
}
```

**이벤트 발행 규칙 (R2DBC)**:
- ✅ **ApplicationEventPublisher 주입**: Spring이 제공하는 인터페이스 사용
- ✅ **Reactive 타입 반환**: Repository 메서드는 `Mono<Void>` 또는 `Flux<T>` 반환
- ✅ **doOnSuccess로 발행**: Reactive 체인 성공 시에만 이벤트 발행
- ✅ **로깅**: 이벤트 발행 시점에 DEBUG 로그 남기기
- ❌ **메인 트랜잭션 실패 시 발행 금지**: 실패 시 이벤트 발행하지 않음
- ✅ **.then() 반환**: 최종적으로 `Mono<Void>` 반환

### 3. 이벤트 리스너 (Listener) - R2DBC Reactive

```kotlin
@Component
class UserInteractionEventListener(
    private val userContentInteractionRepository: UserContentInteractionRepository
) {

    /**
     * 사용자 인터랙션 이벤트 처리 (Reactive)
     *
     * ### 처리 흐름 (R2DBC)
     * 1. 메인 트랜잭션 커밋 후 실행 (@TransactionalEventListener + AFTER_COMMIT)
     * 2. 비동기로 실행 (@Async)
     * 3. R2DBC Repository로 user_content_interactions 테이블에 저장
     * 4. subscribe()로 Reactive 체인 실행
     *
     * ### R2DBC 트랜잭션 동작
     * - 메인 트랜잭션: ReactiveTransactionManager가 관리
     * - 트랜잭션 커밋 → AFTER_COMMIT 이벤트 발행
     * - 이벤트 리스너가 비동기로 실행 (별도 스레드)
     *
     * ### 장애 격리
     * - 이 메서드가 실패해도 메인 트랜잭션에 영향 없음
     * - subscribe() 에러 핸들러로 예외 처리
     * - 로그만 남기고 예외를 삼킴
     *
     * @param event 사용자 인터랙션 이벤트
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUserInteractionEvent(event: UserInteractionEvent) {
        try {
            logger.debug(
                "Handling UserInteractionEvent: userId={}, contentId={}, type={}",
                event.userId,
                event.contentId,
                event.interactionType
            )

            // R2DBC Repository로 저장 (Reactive)
            userContentInteractionRepository
                .saveInteraction(event.userId, event.contentId, event.interactionType)
                .subscribe(
                    { logger.debug("User interaction saved successfully") },  // onNext
                    { error ->  // onError
                        logger.error(
                            "Failed to save user interaction: userId={}, contentId={}, type={}",
                            event.userId,
                            event.contentId,
                            event.interactionType,
                            error
                        )
                    }
                )
        } catch (e: Exception) {
            // 예외를 삼켜서 메인 트랜잭션에 영향을 주지 않음
            logger.error("Failed to handle UserInteractionEvent", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserInteractionEventListener::class.java)
    }
}
```

**이벤트 리스너 규칙 (R2DBC)**:
- ✅ **@TransactionalEventListener(AFTER_COMMIT)**: ReactiveTransactionManager 커밋 후 실행
- ✅ **@Async**: 비동기로 실행하여 메인 요청에 영향 없음
- ✅ **R2DBC Repository 호출**: Repository 메서드는 `Mono<Void>` 반환
- ✅ **.subscribe()**: Reactive 체인을 실행하고 에러 핸들링
- ✅ **try-catch**: 예외를 삼켜서 메인 트랜잭션에 영향을 주지 않음
- ✅ **로깅**: DEBUG 레벨로 이벤트 처리 시점 로깅, ERROR 레벨로 실패 로깅
- ✅ **KDoc 작성**: R2DBC 트랜잭션 동작, 처리 흐름, 장애 격리 방식 명시

### 4. Spring Async 설정

```kotlin
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurerSupport() {

    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("async-event-")
        executor.initialize()
        return executor
    }
}
```

**Async 설정 규칙**:
- ✅ **@EnableAsync**: 비동기 처리 활성화
- ✅ **ThreadPoolTaskExecutor**: 스레드 풀 설정
- ✅ **적절한 풀 사이즈**: 애플리케이션 부하에 맞게 조정
- ✅ **스레드 이름 접두사**: 로그 추적을 위해 명확한 이름 설정

## R2DBC 트랜잭션과 Spring Event

### R2DBC에서의 트랜잭션 관리

GrowSnap은 **ReactiveTransactionManager**를 사용하여 R2DBC 트랜잭션을 관리합니다.

```kotlin
@Configuration
class TransactionConfig {

    @Bean
    fun transactionManager(
        connectionFactory: ConnectionFactory
    ): ReactiveTransactionManager {
        return R2dbcTransactionManager(connectionFactory)
    }

    @Bean
    fun transactionalOperator(
        transactionManager: ReactiveTransactionManager
    ): TransactionalOperator {
        return TransactionalOperator.create(transactionManager)
    }
}
```

### R2DBC 트랜잭션과 이벤트 발행 흐름

```
1. Service 메서드 시작 (Reactive 체인 시작)
   ↓
2. R2DBC Repository 호출 (Mono<Void> 반환)
   ↓
3. doOnSuccess: 쿼리 성공 시 이벤트 발행
   ↓
4. ReactiveTransactionManager가 트랜잭션 커밋
   ↓
5. @TransactionalEventListener(AFTER_COMMIT) 실행
   ↓
6. @Async 스레드에서 이벤트 처리
   ↓
7. R2DBC Repository로 비동기 저장
```

### Service에서 Reactive 트랜잭션 사용

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val profileRepository: ProfileRepository,
    private val transactionalOperator: TransactionalOperator  // Reactive 트랜잭션
) {

    /**
     * 사용자와 프로필을 하나의 트랜잭션으로 생성
     *
     * @param user 사용자
     * @param profile 프로필
     * @return Mono<User> - 생성된 사용자
     */
    fun createUserWithProfile(user: User, profile: Profile): Mono<User> {
        return userRepository.save(user)
            .flatMap { savedUser ->
                profileRepository.save(profile.copy(userId = savedUser.id))
                    .thenReturn(savedUser)
            }
            .`as`(transactionalOperator::transactional)  // ✅ Reactive 트랜잭션 적용
    }
}
```

**중요**:
- ✅ **`.as(transactionalOperator::transactional)`**: Reactive 체인 전체를 하나의 트랜잭션으로 묶음
- ✅ **flatMap()으로 순차 처리**: 사용자 저장 → 프로필 저장 순서 보장
- ✅ **트랜잭션 롤백**: 체인 중 에러 발생 시 자동 롤백

## Spring Event 패턴 체크리스트

**이벤트 기반 처리 구현 시 반드시 확인**:

### 기본 이벤트 패턴
- [ ] **이벤트 클래스**: data class로 정의, 필요한 최소 정보만 포함
- [ ] **이벤트 발행**: `applicationEventPublisher.publishEvent()` 사용
- [ ] **발행 시점**: 메인 트랜잭션 성공 시에만 발행 (`doOnSuccess`)
- [ ] **이벤트 리스너**: `@TransactionalEventListener(phase = AFTER_COMMIT)` 사용
- [ ] **비동기 처리**: `@Async` 사용
- [ ] **장애 격리**: try-catch로 예외 삼킴, 로그만 남김
- [ ] **Spring Async 설정**: `@EnableAsync` + ThreadPoolTaskExecutor 설정
- [ ] **KDoc 작성**: 이벤트 목적, 처리 흐름, 장애 격리 방식 명시

### R2DBC Reactive 패턴
- [ ] **Reactive 반환 타입**: Repository 메서드는 `Mono<T>` 또는 `Flux<T>` 반환
- [ ] **doOnSuccess 사용**: Reactive 체인 성공 시에만 이벤트 발행
- [ ] **subscribe() 호출**: 이벤트 리스너에서 Reactive 체인 실행
- [ ] **에러 핸들링**: subscribe()의 onError로 예외 처리
- [ ] **트랜잭션 관리**: ReactiveTransactionManager 사용

## 주의사항

### 1. 메인 트랜잭션과 독립성 보장

**중요**: 이벤트 리스너 실패가 메인 요청에 영향을 주지 않도록 설계

```kotlin
// ✅ GOOD: 메인 트랜잭션 커밋 후 실행
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handleEvent(event: MyEvent) {
    // 이 시점에는 메인 트랜잭션이 이미 커밋됨
}

// ❌ BAD: 메인 트랜잭션과 같은 시점에 실행
@EventListener
fun handleEvent(event: MyEvent) {
    // 메인 트랜잭션 커밋 전에 실행되어 롤백 가능성
}
```

### 2. 멱등성(Idempotency) 고려

**중요**: 이벤트가 중복 발생할 수 있으므로 멱등성 보장 필요

```kotlin
// ✅ GOOD: UNIQUE 제약 조건 설정
CREATE TABLE user_content_interactions (
    user_id CHAR(36) NOT NULL,
    content_id CHAR(36) NOT NULL,
    interaction_type VARCHAR(20) NOT NULL,
    UNIQUE KEY unique_interaction (user_id, content_id, interaction_type)
);

// ✅ GOOD: INSERT ... ON DUPLICATE KEY UPDATE
fun saveInteraction(userId: UUID, contentId: UUID, type: InteractionType) {
    dslContext
        .insertInto(USER_CONTENT_INTERACTIONS)
        .set(USER_CONTENT_INTERACTIONS.USER_ID, userId.toString())
        .set(USER_CONTENT_INTERACTIONS.CONTENT_ID, contentId.toString())
        .set(USER_CONTENT_INTERACTIONS.INTERACTION_TYPE, type.name)
        .onDuplicateKeyUpdate()
        .set(USER_CONTENT_INTERACTIONS.UPDATED_AT, LocalDateTime.now())
        .execute()
}
```

### 3. 로깅 충실

**중요**: 이벤트 발행/처리 시점에 DEBUG 로그 남기기, 실패 시 ERROR 로그로 추적 가능하도록

```kotlin
// ✅ GOOD: 충실한 로깅
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handleEvent(event: UserInteractionEvent) {
    try {
        logger.debug(
            "Handling event: userId={}, contentId={}, type={}",
            event.userId,
            event.contentId,
            event.interactionType
        )

        // 이벤트 처리 로직

        logger.debug("Event handled successfully")
    } catch (e: Exception) {
        logger.error(
            "Failed to handle event: userId={}, contentId={}, type={}",
            event.userId,
            event.contentId,
            event.interactionType,
            e
        )
    }
}
```

## 전체 흐름 예시

### 1. 좋아요 이벤트 발행

```kotlin
@Service
class AnalyticsServiceImpl(
    private val contentInteractionRepository: ContentInteractionRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) : AnalyticsService {

    override fun trackLike(userId: UUID, contentId: UUID): Mono<Void> {
        // 1. 메인 트랜잭션: like_count 증가
        return contentInteractionRepository.incrementLikeCount(contentId)
            .doOnSuccess {
                // 2. 이벤트 발행 (메인 트랜잭션 성공 후)
                logger.debug("Publishing LikeEvent: userId={}, contentId={}", userId, contentId)
                applicationEventPublisher.publishEvent(
                    UserInteractionEvent(userId, contentId, InteractionType.LIKE)
                )
            }
            .then()
    }
}
```

### 2. 이벤트 리스너에서 비동기 처리

```kotlin
@Component
class UserInteractionEventListener(
    private val userContentInteractionRepository: UserContentInteractionRepository,
    private val recommendationService: RecommendationService
) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleUserInteractionEvent(event: UserInteractionEvent) {
        try {
            logger.debug("Handling UserInteractionEvent: {}", event)

            // 1. user_content_interactions 테이블에 저장
            userContentInteractionRepository
                .saveInteraction(event.userId, event.contentId, event.interactionType)
                .subscribe()

            // 2. 추천 시스템 업데이트 (선택적)
            recommendationService.updateUserPreference(event.userId, event.contentId)

            logger.debug("Event handled successfully")
        } catch (e: Exception) {
            logger.error("Failed to handle event", e)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserInteractionEventListener::class.java)
    }
}
```

## 정리

### Spring Event 패턴 핵심 (R2DBC)

1. **이벤트 클래스**: data class로 정의, 필요한 최소 정보만 포함
2. **이벤트 발행**: `applicationEventPublisher.publishEvent()` + `doOnSuccess` (Reactive 체인 내)
3. **이벤트 리스너**: `@TransactionalEventListener(AFTER_COMMIT)` + `@Async`
4. **R2DBC Repository**: `Mono<T>` 또는 `Flux<T>` 반환, `.subscribe()`로 실행
5. **장애 격리**: try-catch + subscribe()의 onError로 예외 처리
6. **멱등성**: 중복 이벤트 처리 대비
7. **로깅**: DEBUG/ERROR 레벨로 충실한 로깅

### R2DBC와 Spring Event 통합

```kotlin
// 1. Service: Reactive 체인에서 이벤트 발행
return repository.save(entity)
    .doOnSuccess { event -> publishEvent(event) }
    .then()

// 2. Event Listener: 비동기로 R2DBC Repository 호출
@Async
@TransactionalEventListener(AFTER_COMMIT)
fun handleEvent(event: MyEvent) {
    repository.save(data).subscribe()  // ✅ subscribe()로 실행
}
```

### 언제 사용하는가?

- ✅ **메인 트랜잭션과 독립적인 작업**: 이메일 발송, 알림 전송, 통계 업데이트
- ✅ **실패해도 메인 요청에 영향 없는 작업**: 추천 시스템 업데이트, 로그 저장
- ✅ **도메인 간 결합도 낮추기**: 주문 완료 후 재고 업데이트, 포인트 적립
- ❌ **메인 트랜잭션에 영향을 주는 작업**: 결제 처리, 재고 차감 (동기 처리 필요)

### R2DBC 주의사항

- ✅ **Repository는 Mono/Flux 반환**: 절대 `.block()` 사용 금지 (Production 코드)
- ✅ **subscribe() 호출 필수**: 이벤트 리스너에서 Reactive 체인 실행
- ✅ **에러 핸들링**: subscribe(onNext, onError)로 예외 처리
- ✅ **ReactiveTransactionManager**: R2DBC 트랜잭션 관리
