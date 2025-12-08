# Upvy Backend 코드 스타일 규칙

> 로깅, 네이밍, Kotlin 특성 활용, WebFlux Reactive 패턴을 정의합니다.

## 로깅 및 출력 규칙

**절대 준수**: 다음 규칙은 예외 없이 반드시 지켜야 합니다.

### 1. println 사용 금지

**절대 사용 금지**: `println()`, `print()`, `System.out.println()` 등 모든 콘솔 출력

- ❌ **절대 사용 금지**: `println()`, `print()`, `System.out.println()` 등 모든 콘솔 출력
- ✅ **반드시 사용**: SLF4J Logger 사용

```kotlin
// ❌ BAD: println 사용
fun startRedis() {
    redisServer?.start()
    println("Embedded Redis started on port $redisPort")  // 절대 금지!
}

// ✅ GOOD: Logger 사용
@Service
class RedisService {
    companion object {
        private val logger = LoggerFactory.getLogger(RedisService::class.java)
    }

    fun startRedis() {
        redisServer?.start()
        logger.info("Embedded Redis started on port {}", redisPort)
    }
}
```

**이유**:
- println은 로그 레벨 제어 불가
- 프로덕션 환경에서 로그 추적 불가능
- 로그 파일로 저장되지 않음
- 구조화된 로깅 불가능

### 2. 이모티콘 사용 금지

**절대 사용 금지**: 코드, 주석, 로그 메시지에 이모티콘 사용 금지

- ❌ **절대 사용 금지**: 코드, 주석, 로그 메시지에 이모티콘 사용 금지
- ✅ **허용**: 문서 파일 (README.md, CLAUDE.md 등)에서만 사용 가능

```kotlin
// ❌ BAD: 이모티콘 사용
logger.info("✅ Redis started successfully")
logger.warn("⚠️ Redis port already in use")
// 주석에도 이모티콘 사용 금지: // ✅ 성공 케이스

// ✅ GOOD: 텍스트만 사용
logger.info("Redis started successfully")
logger.warn("Redis port already in use")
// 주석도 텍스트만: // 성공 케이스
```

**이유**:
- 로그 파일 인코딩 문제 발생 가능
- 로그 검색 및 파싱 어려움
- 전문성 저하
- CI/CD 환경에서 이모티콘 깨질 수 있음

### 3. FQCN 사용 금지

**절대 사용 금지**: Fully Qualified Class Name (FQCN) 사용 금지

- ❌ **절대 사용 금지**: Fully Qualified Class Name (FQCN) 사용 금지
- ✅ **반드시 사용**: import 문 사용

```kotlin
// ❌ BAD: FQCN 사용
val scanOptions = org.springframework.data.redis.core.ScanOptions.scanOptions()
    .match(pattern)
    .build()

return redisTemplate.execute { connection ->
    connection.scriptingCommands()
        .eval(
            ByteBuffer.wrap(script.toByteArray()),
            org.springframework.data.redis.connection.ReturnType.INTEGER,  // FQCN 사용 금지!
            1,
            ByteBuffer.wrap(key.toByteArray())
        )
}

// ✅ GOOD: import 사용
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.connection.ReturnType

val scanOptions = ScanOptions.scanOptions()
    .match(pattern)
    .build()

return redisTemplate.execute { connection ->
    connection.scriptingCommands()
        .eval(
            ByteBuffer.wrap(script.toByteArray()),
            ReturnType.INTEGER,  // import한 클래스 사용
            1,
            ByteBuffer.wrap(key.toByteArray())
        )
}
```

**이유**:
- 코드 가독성 저하
- 네임스페이스 오염
- IDE의 자동 import 기능 활용 불가
- 코드 리뷰 및 유지보수 어려움

### 로깅 체크리스트

**코드 작성 전 반드시 확인**:

- [ ] **println 사용 금지**: `println`, `print`, `System.out.println` 사용하지 않았는가?
- [ ] **Logger 사용**: SLF4J Logger를 사용했는가?
- [ ] **이모티콘 제거**: 코드, 주석, 로그에 이모티콘이 없는가?
- [ ] **로그 레벨 적절성**: 적절한 로그 레벨(info, warn, error, debug)을 사용했는가?
- [ ] **FQCN 사용 금지**: `package.name.ClassName` 형태로 직접 사용하지 않았는가?
- [ ] **import 문 사용**: 모든 외부 클래스는 import하여 사용했는가?

## 네이밍 규칙

```kotlin
// 클래스: PascalCase
class VideoService
class UserProfileController

// 함수/변수: camelCase
fun createVideo(request: VideoCreateRequest)
val videoTitle = "제목"
val userId = UUID.randomUUID()

// 상수: UPPER_SNAKE_CASE
const val MAX_VIDEO_SIZE = 100_000_000
const val DEFAULT_PAGE_SIZE = 20

// 패키지: lowercase
package me.onetwo.upvy.domain.video
package me.onetwo.upvy.infrastructure.config
```

### 네이밍 가이드라인

- **클래스**: PascalCase 사용 (첫 글자 대문자)
- **함수/변수**: camelCase 사용 (첫 글자 소문자)
- **상수**: UPPER_SNAKE_CASE 사용 (모두 대문자, 언더스코어로 구분)
- **패키지**: 모두 소문자, 점(.)으로 구분
- **의미 있는 이름 사용**: 축약어 대신 전체 단어 사용 (user 대신 u 사용 금지)

## Kotlin 특성 활용

```kotlin
// ✅ data class 활용
data class VideoResponse(val id: String, val title: String)
data class User(val id: UUID, val email: String, val name: String)

// ✅ null 안전성
fun getVideo(id: String): Video? = videoRepository.findById(id).orElse(null)
val title = video?.title ?: "기본 제목"
val email = user?.email?.lowercase() ?: throw IllegalStateException("Email required")

// ✅ 확장 함수
fun Video.toResponse(): VideoResponse = VideoResponse(id, title)
fun User.toDto(): UserDto = UserDto(id.toString(), email, name)

// ✅ when 표현식
val type = when (category) {
    VideoCategory.PROGRAMMING -> "기술"
    VideoCategory.LIFESTYLE -> "라이프스타일"
    VideoCategory.EDUCATION -> "교육"
    else -> "기타"
}

// ✅ let, apply, also 활용
val result = user?.let {
    logger.info("Processing user: ${it.email}")
    processUser(it)
}

val video = Video(id, title).apply {
    this.duration = 120
    this.url = "https://example.com/video.mp4"
}

// ✅ scope functions 활용
val user = User(email, name).also {
    logger.info("Created user: ${it.email}")
}
```

### Kotlin 스타일 가이드

- **data class**: DTO, Entity 등 데이터 클래스에 활용
- **null safety**: `?.`, `?:`, `!!` 연산자 적절히 활용
- **확장 함수**: 변환 로직을 확장 함수로 분리
- **when 표현식**: if-else 체인 대신 when 사용
- **scope functions**: let, apply, also, run, with 적절히 활용

## WebFlux Reactive 패턴

```kotlin
// ✅ Good: Reactive 체인
fun getVideoFeed(userId: String): Flux<VideoDto> {
    return userRepository.findById(userId)
        .flatMapMany { user ->
            videoRepository.findRecommendedVideos(user.interests)
        }
        .map { it.toDto() }
}

// ❌ Bad: 블로킹 호출
fun getVideoFeed(userId: String): List<VideoDto> {
    val user = userRepository.findById(userId).block()!!  // 블로킹!
    return videoRepository.findAll().collectList().block()!!
}

// ✅ Good: 배압 제어
fun processVideos(): Flux<ProcessedVideo> {
    return videoRepository.findAll()
        .limitRate(100)  // 한 번에 100개씩
        .flatMap { video -> videoProcessor.process(video) }
}

// ✅ Good: 에러 핸들링
fun createVideo(request: VideoCreateRequest): Mono<VideoResponse> {
    return videoRepository.save(request.toEntity())
        .doOnSuccess { logger.info("Video created: ${it.id}") }
        .doOnError { logger.error("Failed to create video", it) }
        .onErrorMap { VideoException.VideoCreationException(it.message) }
}

// ✅ Good: flatMap vs map 구분
fun getUserWithProfile(userId: UUID): Mono<UserProfileResponse> {
    return userRepository.findById(userId)
        .flatMap { user ->  // ✅ flatMap: Mono를 반환하는 경우
            profileRepository.findByUserId(user.id)
                .map { profile -> user.toResponse(profile) }  // ✅ map: 일반 객체 반환
        }
}
```

### WebFlux 패턴 가이드

- **블로킹 금지**: `.block()` 사용 최소화, 불가피한 경우만 사용
- **flatMap vs map**: Mono/Flux 반환 시 flatMap, 일반 객체 반환 시 map
- **배압 제어**: `.limitRate()` 사용하여 부하 관리
- **에러 핸들링**: `doOnError`, `onErrorMap`, `onErrorResume` 활용
- **로깅**: `doOnSuccess`, `doOnError`로 비동기 로깅

## 코드 리뷰 체크리스트

### Claude가 PR 전에 반드시 확인할 항목

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

## 개발 시작 전 체크리스트

### 새로운 기능 개발 시

- [ ] **요구사항 이해**: 기능의 목적과 범위를 명확히 이해했는가?
- [ ] **테스트 먼저**: 테스트 코드를 먼저 작성할 준비가 되었는가?
- [ ] **SOLID 원칙**: 어떤 클래스/서비스를 만들 것인지 설계했는가?
- [ ] **계층 분리**: Controller-Service-Repository 역할이 명확한가?
- [ ] **API 설계**: RESTful URL과 HTTP 상태 코드를 결정했는가?
- [ ] **데이터베이스 스키마**: Audit Trail 필드가 포함되어 있는가?
- [ ] **예외 처리**: 어떤 예외를 정의하고 처리할 것인가?
- [ ] **문서화**: KDoc과 REST Docs를 작성할 준비가 되었는가?

## 정리

### 반드시 지켜야 할 규칙

1. **println 금지**: SLF4J Logger 필수 사용
2. **이모티콘 금지**: 코드, 주석, 로그에 이모티콘 절대 사용 금지
3. **FQCN 금지**: import 문 사용
4. **네이밍**: PascalCase, camelCase, UPPER_SNAKE_CASE 적절히 사용
5. **Kotlin 특성**: data class, null safety, 확장 함수 활용
6. **WebFlux**: 블로킹 호출 최소화, flatMap/map 구분
7. **코드 리뷰**: PR 전 체크리스트 확인
8. **개발 프로세스**: 테스트 → 구현 → 리팩토링 → 문서화 → 빌드/테스트 → 커밋
