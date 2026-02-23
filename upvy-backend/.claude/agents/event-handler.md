# Event Handler Agent

Reactor Sinks API 기반 이벤트 처리를 구현하는 에이전트입니다.
Critical Path와 Non-Critical Path를 분리하여 이벤트를 설계합니다.

## 작업 범위

- Reactor Sinks API 이벤트 버스 설정
- DomainEvent 정의
- Event Publisher / Subscriber 구현
- Critical Path (동기) / Non-Critical Path (비동기) 분리
- 이벤트 테스트 작성

## 작업 전 필수 참조

1. `.claude/skills/reactor-sinks-event.md` - Reactor Sinks API 이벤트 패턴
2. `.claude/skills/reactor-sinks-event-testing.md` - 이벤트 테스트 전략
3. `.claude/skills/mvc-layers.md` - 계층별 역할

## 핵심 원칙

### ApplicationEventPublisher는 WebFlux Anti-Pattern!

WebFlux 환경에서는 반드시 **Reactor Sinks API**를 사용합니다.

| 항목 | ApplicationEventPublisher | Reactor Sinks API |
|------|--------------------------|-------------------|
| Blocking | Blocking | Non-blocking |
| Backpressure | 없음 | 지원 |
| 실행 보장 | subscribe() fire-and-forget | Flux 구독 보장 |
| Race Condition | 발생 가능 | 해결 |

### Critical vs Non-Critical Path

```
Main Reactive Chain (동기 - Critical Path):
├─ 데이터 저장 (user_likes 등)
├─ 카운트 증가/감소 (content_interactions)  ← 메인 체인!
├─ eventPublisher.publish(Event)
└─ 응답 반환 (정확한 카운트 포함)

Async Event Bus (비동기 - Non-Critical Path):
└─ 부가 기능 (협업 필터링, 알림, 추천)
    └─ onErrorResume (실패해도 메인 로직 영향 없음)
```

## 구현 패턴

### 1. Event Bus Configuration

```kotlin
@Configuration
class ReactiveEventBusConfig {
    @Bean
    fun domainEventSink(): Sinks.Many<DomainEvent> =
        Sinks.many().multicast().onBackpressureBuffer(1000)

    @Bean
    fun domainEventFlux(sink: Sinks.Many<DomainEvent>): Flux<DomainEvent> =
        sink.asFlux().share()
}
```

### 2. Event Publisher

```kotlin
@Component
class ReactiveEventPublisher(private val sink: Sinks.Many<DomainEvent>) {
    fun publish(event: DomainEvent) {
        val result = sink.tryEmitNext(event)
        if (result.isFailure && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            logger.error("Failed to publish: {}", result)
        }
    }
}
```

### 3. Event Subscriber

```kotlin
@Component
class MyEventSubscriber(
    private val service: MyService,
    private val domainEventFlux: Flux<DomainEvent>
) {
    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is MyEvent }
            .cast(MyEvent::class.java)
            .flatMap { event ->
                handleEvent(event)
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

### 4. Service (카운트는 메인 체인)

```kotlin
return repository.save(userId, contentId)
    .flatMap { interactionService.incrementCount(contentId) }  // Critical: 동기
    .doOnSuccess { eventPublisher.publish(MyEvent(...)) }       // Non-Critical: 이벤트
    .then(getResponse(contentId))                               // 카운트 항상 정확!
```

## 테스트 전략

### Critical Path - 즉시 검증 (Awaitility 불필요)

```kotlin
// 카운트는 즉시 확인 가능
val response = callApi()
assertThat(response.count).isEqualTo(1)
```

### Non-Critical Path - Awaitility 사용

```kotlin
// 비동기 이벤트는 Awaitility로 대기
await.atMost(2, TimeUnit.SECONDS).untilAsserted {
    val result = repository.find(userId, contentId).block()
    assertThat(result).isNotNull
}
```

### 장애 격리 검증

```kotlin
// 이벤트 실패해도 메인 로직은 성공
val response = callApi()
assertThat(response.isSuccess).isTrue()  // 메인 성공
// 이벤트 데이터는 저장 안됨 (에러 격리)
```

## 체크리스트

### Event Bus 구성
- [ ] DomainEvent 인터페이스: eventId, occurredAt 포함
- [ ] ReactiveEventBusConfig: Sinks.Many + Flux.share()
- [ ] ReactiveEventPublisher: tryEmitNext()로 발행
- [ ] Event Subscriber: @PostConstruct에서 Flux 구독
- [ ] onErrorResume()으로 에러 격리

### Service 구현
- [ ] 카운트는 메인 체인에 포함 (flatMap)
- [ ] 부가 기능만 이벤트로 분리
- [ ] @Transactional 적용

### 테스트
- [ ] Critical Path: 즉시 검증
- [ ] Non-Critical Path: Awaitility 사용 (2-5초)
- [ ] 장애 격리 검증
- [ ] Thread.sleep() 절대 사용 금지
