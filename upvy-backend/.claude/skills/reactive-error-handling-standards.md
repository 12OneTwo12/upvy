# Reactive 에러 처리 표준 규칙

**목적**: 일관된 에러 처리로 유지보수성 향상

---

## 핵심 규칙

### 1. switchIfEmpty - 빈 결과 처리

**규칙**: Repository 조회 시 결과가 없으면 명시적인 예외 발생

```kotlin
// ✅ DO - switchIfEmpty 사용
fun findById(id: UUID): Mono<User> {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(UserNotFoundException("User not found: $id")))
}

// ❌ DON'T - Blocking 방식
fun findById(id: UUID): Mono<User> {
    return Mono.fromCallable {
        userRepository.findById(id).orElseThrow { ... }  // Blocking!
    }
}
```

**사용 위치**: Repository, Service 레이어

---

### 2. onErrorMap - 예외 변환

**규칙**: 하위 예외를 도메인 예외로 변환

```kotlin
// ✅ DO - 특정 예외만 변환
fun saveContent(content: Content): Mono<Content> {
    return contentRepository.save(content)
        .onErrorMap { error ->
            when (error) {
                is DataAccessException -> ContentException.SaveException(
                    message = "Failed to save content: ${content.id}",
                    cause = error
                )
                else -> error  // 다른 예외는 그대로 전파
            }
        }
}

// ❌ DON'T - 모든 예외를 무조건 변환
.onErrorMap { ContentException.SaveException(it.message) }
```

**사용 위치**: Service 레이어

---

### 3. doOnError - 로깅 전용

**규칙**: 로깅만 하고 예외는 GlobalExceptionHandler로 전파

```kotlin
// ✅ DO - 로깅 후 예외 전파
fun createContent(userId: UUID, request: Request): Mono<ContentResponse> {
    return validateSession(userId, request)
        .flatMap { session -> saveContent(session) }
        .doOnError { error ->
            logger.error(
                "Failed to create content: userId={}, contentId={}",
                userId,
                request.contentId,
                error
            )
            // 예외는 GlobalExceptionHandler로 전파됨
        }
}

// ❌ DON'T - doOnError에서 예외 소비
.doOnError { error ->
    logger.error("Error", error)
    return@doOnError  // 잘못된 사용!
}
```

**사용 위치**: 모든 레이어

---

### 4. handle - 조건부 검증

**규칙**: 여러 조건을 순차적으로 검증할 때 사용

```kotlin
// ✅ DO - handle로 여러 조건 검증
fun validateSession(userId: UUID, sessionId: String): Mono<UploadSession> {
    return uploadSessionRepository.findByIdReactive(sessionId)
        .switchIfEmpty(Mono.error(UploadSessionNotFoundException(sessionId)))
        .handle { session, sink ->
            when {
                session.userId != userId.toString() ->
                    sink.error(UnauthorizedException("Not your session"))
                session.isExpired() ->
                    sink.error(UploadSessionExpiredException(sessionId))
                !s3Service.exists(session.s3Key) ->
                    sink.error(IllegalStateException("S3 object not found"))
                else ->
                    sink.next(session)
            }
        }
}

// ❌ DON'T - flatMap 중첩으로 검증
.flatMap { session ->
    if (session.userId != userId.toString()) {
        Mono.error(UnauthorizedException("Not your session"))
    } else {
        if (session.isExpired()) {
            Mono.error(UploadSessionExpiredException(sessionId))
        } else {
            // 계속 중첩...
        }
    }
}
```

**사용 위치**: Service 레이어

---

### 5. Blocking 연산 절대 금지

**규칙**: `.orElseThrow()`를 Reactive 체인에서 사용 금지

```kotlin
// ❌ DON'T - Mono.fromCallable 안에서 .orElseThrow()
return Mono.fromCallable {
    val session = repository.findById(id).orElseThrow {  // Blocking!
        UploadSessionNotFoundException(id)
    }
    session
}

// ✅ DO - Reactive 방식으로 전환
return repository.findByIdReactive(id)
    .switchIfEmpty(Mono.error(UploadSessionNotFoundException(id)))
```

---

## 레이어별 에러 처리

### Repository 레이어

```kotlin
// Repository는 switchIfEmpty만 사용
override fun findById(id: UUID): Mono<User> {
    return Flux.from(
        dslContext
            .selectFrom(USERS)
            .where(USERS.ID.eq(id.toString()))
            .and(USERS.DELETED_AT.isNull)
    )
    .next()
    .switchIfEmpty(Mono.error(UserNotFoundException("User not found: $id")))
    .map { record -> record.into(User::class.java) }
}
```

---

### Service 레이어

```kotlin
// Service는 switchIfEmpty + handle + onErrorMap + doOnError 조합
@Transactional
override fun createContent(userId: UUID, request: Request): Mono<ContentResponse> {
    return validateSession(userId, request)        // handle 사용
        .flatMap { session -> saveContent(session) }   // onErrorMap 사용
        .map { saved -> ContentResponse.from(saved) }
        .doOnError { error ->                          // doOnError 사용
            logger.error("Failed to create content", error)
        }
}

private fun saveContent(content: Content): Mono<Content> {
    return contentRepository.save(content)
        .onErrorMap { error ->
            when (error) {
                is DataAccessException -> ContentException.SaveException(cause = error)
                else -> error
            }
        }
}
```

---

### Controller 레이어

```kotlin
// Controller는 예외 처리 코드 작성하지 않음
@PostMapping
fun createContent(
    principal: Mono<Principal>,
    @RequestBody @Valid request: ContentCreationRequest
): Mono<ResponseEntity<ContentResponse>> {
    // 예외 처리 없음 - GlobalExceptionHandler가 처리
    return principal
        .toUserId()
        .flatMap { userId -> contentService.createContent(userId, request) }
        .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
}
```

---

## 체크리스트

### 코드 작성 전
- [ ] Repository가 Reactive API를 제공하는가?
- [ ] 비즈니스 검증 로직이 있는가?

### 코드 작성 중
- [ ] Repository 조회 시 `switchIfEmpty` 사용했는가?
- [ ] 비즈니스 검증은 `handle`로 구현했는가?
- [ ] 하위 예외 변환은 `onErrorMap` 사용했는가?
- [ ] 로깅은 `doOnError`로 처리했는가?
- [ ] Blocking 코드 (`.orElseThrow()`) 사용하지 않았는가?

### 코드 리뷰 시
- [ ] 에러 처리 패턴이 표준을 따르는가?
- [ ] 예외 메시지에 충분한 컨텍스트가 있는가?
- [ ] Controller에서 예외 처리하지 않았는가?

---

## 빠른 참조

### 표준 패턴 요약

| 패턴 | 사용 시기 | 위치 | 예외 전파 |
|------|----------|------|---------|
| `switchIfEmpty` | 빈 결과 → 예외 | Repository, Service | 새 예외 발생 |
| `onErrorMap` | 예외 변환 | Service | 변환된 예외 전파 |
| `doOnError` | 로깅 | 모든 레이어 | 원본 예외 전파 |
| `handle` | 조건부 검증 | Service | 조건 실패 시 예외 |

### DO ✅

```kotlin
// 1. switchIfEmpty
repository.findById(id)
    .switchIfEmpty(Mono.error(NotFoundException("Not found: $id")))

// 2. onErrorMap
repository.save(entity)
    .onErrorMap { error ->
        when (error) {
            is DataAccessException -> SaveException(cause = error)
            else -> error
        }
    }

// 3. doOnError (로깅만)
service.execute()
    .doOnError { error -> logger.error("Failed", error) }

// 4. handle (조건 검증)
.handle { value, sink ->
    when {
        value.isInvalid() -> sink.error(ValidationException())
        else -> sink.next(value)
    }
}
```

### DON'T ❌

```kotlin
// 1. Blocking 연산
Mono.fromCallable { repository.findById(id).orElseThrow() }

// 2. 예외를 소비
.doOnError { error -> return@doOnError }

// 3. flatMap 중첩 검증
.flatMap { if (...) Mono.error() else if (...) Mono.error() else ... }

// 4. Controller에서 예외 처리
@PostMapping
fun create() {
    try { ... } catch { ... }  // 금지!
}
```

---

**마지막 업데이트**: 2025-12-29
