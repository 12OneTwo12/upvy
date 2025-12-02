# GrowSnap Backend 빠른 참조 가이드

> 모든 개발 작업 시 빠르게 참조할 수 있는 체크리스트와 핵심 규칙 요약입니다.

## Claude가 반드시 지킬 것 (19가지 핵심 규칙)

1. **TDD**: 테스트 → 구현 → 리팩토링 (시나리오 기반, Given-When-Then 주석 필수)
2. **테스트 검증**: 구현 후 반드시 빌드/테스트 실행, 통과해야만 완료
3. **SOLID**: 단일 책임, 인터페이스 분리, 의존성 역전
4. **KDoc**: 모든 public 함수/클래스
5. **REST Docs**: 모든 API
6. **DisplayName**: 시나리오를 명확히 설명하는 한글 설명
7. **MockK**: 단위 테스트 모킹
8. **Git Convention**: `feat(scope): subject`
9. **MVC 패턴**: Controller → Service → Repository
10. **성능 vs 가독성**: 가독성 우선, 필요시 최적화
11. **RESTful API**: 동사 금지, 적절한 HTTP 메서드/상태 코드
12. **Audit Trail**: 모든 엔티티에 5가지 필드 필수 (createdAt, createdBy, updatedAt, updatedBy, deletedAt), 물리적 삭제 금지
13. **Database Query**: SELECT 쿼리에서 asterisk (*) 사용 절대 금지, 필요한 컬럼만 명시적으로 선택
14. **로깅 규칙**: println 절대 금지, SLF4J Logger 필수 사용
15. **이모티콘 금지**: 코드, 주석, 로그에 이모티콘 절대 사용 금지 (문서 파일만 허용)
16. **FQCN 금지**: Fully Qualified Class Name 사용 금지, 반드시 import 문 사용
17. **Principal 추출 (WebFlux)**: userId는 `principal: Mono<Principal>`로 Spring Security Context에서 추출, Request Body/Path Variable 사용 금지
18. **Spring Event 패턴**: 비동기 이벤트 처리 시 @TransactionalEventListener(AFTER_COMMIT) + @Async 사용, 메인 트랜잭션과 독립성 보장
19. **Thread.sleep() 절대 금지**: 모든 테스트에서 Thread.sleep() 사용 금지, 대신 Awaitility, 명시적 타임스탬프, StepVerifier 사용

## 개발 프로세스 (항상 이 순서로)

```
1. 테스트 코드 작성 (Controller + Service + Repository)
   ↓
2. 테스트 통과하는 최소 코드 작성 (SOLID 원칙 준수)
   ↓
3. 리팩토링 (SOLID 원칙 적용)
   ↓
4. KDoc + REST Docs 작성
   ↓
5. 빌드 및 테스트 (모두 정상이여야함, 일부 실패 용인하지 않음)
   ↓
6. 커밋 (feat(scope): message)
```

## 개발 시작 전 체크리스트

**새로운 기능 개발 시 반드시 확인**:

- [ ] **요구사항 이해**: 기능의 목적과 범위를 명확히 이해했는가?
- [ ] **테스트 먼저**: 테스트 코드를 먼저 작성할 준비가 되었는가?
- [ ] **SOLID 원칙**: 어떤 클래스/서비스를 만들 것인지 설계했는가?
- [ ] **계층 분리**: Controller-Service-Repository 역할이 명확한가?
- [ ] **API 설계**: RESTful URL과 HTTP 상태 코드를 결정했는가?
- [ ] **데이터베이스 스키마**: Audit Trail 필드가 포함되어 있는가?
- [ ] **예외 처리**: 어떤 예외를 정의하고 처리할 것인가?
- [ ] **문서화**: KDoc과 REST Docs를 작성할 준비가 되었는가?

## 코드 리뷰 체크리스트

**PR 전에 Claude가 반드시 확인할 항목**:

- [ ] **TDD**: 테스트 코드를 먼저 작성했는가?
- [ ] **테스트 통과**: 모든 테스트가 통과하는가?
- [ ] **빌드 성공**: ./gradlew build가 성공하는가?
- [ ] **모킹**: 단위 테스트에서 MockK를 사용했는가?
- [ ] **시나리오 기반**: 테스트만 보고 기능을 즉시 파악할 수 있는가?
- [ ] **Given-When-Then**: 모든 테스트에 주석이 명시되어 있는가?
- [ ] **DisplayName**: 한글로 명확한 시나리오 설명이 있는가?
- [ ] **SOLID 원칙**: 단일 책임, 의존성 역전 원칙을 지켰는가?
- [ ] **KDoc**: 모든 public 함수/클래스에 KDoc이 작성되어 있는가?
- [ ] **REST Docs**: 모든 API에 document()가 추가되어 있는가?
- [ ] **Audit Trail**: 모든 엔티티에 5가지 필드가 있는가?
- [ ] **Soft Delete**: 물리적 삭제를 사용하지 않았는가?
- [ ] **SELECT asterisk 금지**: 필요한 컬럼만 명시적으로 선택했는가?
- [ ] **println 금지**: SLF4J Logger를 사용했는가?
- [ ] **이모티콘 금지**: 코드, 주석, 로그에 이모티콘이 없는가?
- [ ] **FQCN 금지**: import 문을 사용했는가?
- [ ] **WebFlux 패턴**: 블로킹 호출을 사용하지 않았는가?

## 계층별 역할 요약

### Controller (HTTP 처리만)

- ✅ HTTP 요청/응답 처리, Bean Validation, Service 호출
- ❌ 비즈니스 로직, 데이터베이스 접근, 복잡한 데이터 처리

### Service (비즈니스 로직)

- ✅ 비즈니스 로직, 트랜잭션 관리, Repository 호출, Reactive 체이닝
- ❌ HTTP 처리, DSLContext 직접 사용

### Repository (데이터베이스)

- ✅ JOOQ R2DBC 쿼리, **Reactive 타입 반환 (Mono<Entity>, Flux<Entity>)**
- ❌ 비즈니스 로직

## 금지 사항 (절대 준수)

### 1. println 사용 금지

```kotlin
// ❌ BAD
println("Redis started")

// ✅ GOOD
logger.info("Redis started on port {}", port)
```

### 2. 이모티콘 사용 금지

```kotlin
// ❌ BAD
logger.info("✅ Success")

// ✅ GOOD
logger.info("Success")
```

### 3. FQCN 사용 금지

```kotlin
// ❌ BAD
org.springframework.data.redis.connection.ReturnType.INTEGER

// ✅ GOOD
import org.springframework.data.redis.connection.ReturnType
ReturnType.INTEGER
```

### 4. SELECT asterisk (*) 금지

```kotlin
// ❌ BAD
dslContext.select(CONTENTS.asterisk()).from(CONTENTS)

// ✅ GOOD
dslContext.select(CONTENTS.ID, CONTENTS.TITLE).from(CONTENTS)
```

### 5. 물리적 삭제 금지

```kotlin
// ❌ BAD
dslContext.deleteFrom(USER).where(USER.ID.eq(userId))

// ✅ GOOD
dslContext.update(USER)
    .set(USER.DELETED_AT, Instant.now())
    .where(USER.ID.eq(userId))
```

## 필수 패턴

### 1. Audit Trail (5가지 필드)

```kotlin
data class User(
    val id: UUID,
    val email: String,
    // Audit Trail 필드 (필수)
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null  // Soft Delete
)
```

### 2. Soft Delete 조회

```kotlin
// ✅ GOOD: 조회 시 삭제된 데이터 제외
dslContext.select(USER.ID, USER.EMAIL)
    .from(USER)
    .where(USER.DELETED_AT.isNull)
```

### 3. WebFlux Controller 반환 타입

```kotlin
// ✅ GOOD: Mono<ResponseEntity<T>> 패턴
@GetMapping("/{id}")
fun getUser(@PathVariable id: UUID): Mono<ResponseEntity<UserResponse>> {
    return userService.findById(id)
        .map { ResponseEntity.ok(it) }
        .defaultIfEmpty(ResponseEntity.notFound().build())
}
```

### 4. Principal 추출 (WebFlux)

```kotlin
// ✅ GOOD: Mono<Principal>로 추출
@PostMapping("/views")
fun trackViewEvent(
    principal: Mono<Principal>,
    @RequestBody request: ViewEventRequest
): Mono<Void> {
    return principal
        .toUserId()
        .flatMap { userId ->
            analyticsService.trackViewEvent(userId, request)
        }
}
```

### 5. Spring Event 패턴

```kotlin
// ✅ GOOD: 이벤트 발행
return incrementCounter.doOnSuccess {
    applicationEventPublisher.publishEvent(
        UserInteractionEvent(userId, contentId, type)
    )
}.then()

// ✅ GOOD: 이벤트 리스너
@Async
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun handleEvent(event: UserInteractionEvent) {
    try {
        // 이벤트 처리 로직
    } catch (e: Exception) {
        logger.error("Failed to handle event", e)
    }
}
```

## 테스트 작성 필수 계층

**모든 기능 구현 시 3가지 계층 테스트 필수**:

1. **Controller 테스트** - HTTP 요청/응답, Validation, REST Docs
2. **Service 테스트** - 비즈니스 로직, 예외 처리
3. **Repository 테스트** - 데이터베이스 CRUD, Soft Delete, Audit Trail

## HTTP 상태 코드

| 상황 | 상태 코드 |
|-----|---------|
| 성공적인 조회 | 200 OK |
| 성공적인 생성 | 201 Created |
| 성공적인 삭제 | 204 No Content |
| 잘못된 요청 | 400 Bad Request |
| 인증 실패 | 401 Unauthorized |
| 권한 없음 | 403 Forbidden |
| 리소스 없음 | 404 Not Found |
| 중복/충돌 | 409 Conflict |
| 서버 오류 | 500 Internal Server Error |

## 네이밍 규칙

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

## 빠른 디버깅 팁

### 로그 레벨

```kotlin
logger.debug("디버깅 정보")    // 개발 환경에서만
logger.info("일반 정보")       // 프로덕션에서 추적
logger.warn("경고")           // 잠재적 문제
logger.error("에러", ex)      // 예외 발생
```

### WebFlux 디버깅

```kotlin
return userService.findById(id)
    .doOnNext { logger.debug("User found: {}", it) }
    .doOnError { logger.error("Failed to find user", it) }
    .map { ResponseEntity.ok(it) }
```

## 정리

**모든 개발 작업 시 이 파일을 먼저 읽고 체크리스트를 확인하세요!**

- **개발 시작 전**: 개발 시작 전 체크리스트 확인
- **코드 작성 중**: 계층별 역할, 금지 사항, 필수 패턴 확인
- **PR 전**: 코드 리뷰 체크리스트 확인
- **빠른 참조**: Claude가 반드시 지킬 것 18가지 숙지
