# WebFlux 코드 품질 규칙

**목적**: 가독성 높고 유지보수 쉬운 Reactive 코드 작성

---

## 핵심 규칙

### 1. Reactive Chain 중첩 제한

**규칙**: 최대 2-3단계 중첩까지만 허용

```kotlin
// ✅ DO - 2단계 중첩
fun likeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
    return userLikeRepository.exists(userId, contentId)
        .flatMap { exists ->                              // LEVEL 1
            if (exists) {
                getLikeResponse(contentId, true)
            } else {
                userLikeRepository.save(userId, contentId)
                    .flatMap { incrementLikeCount(contentId) }  // LEVEL 2 - OK
                    .then(getLikeResponse(contentId, true))
            }
        }
}

// ❌ DON'T - 5단계 중첩
fun createContent(...): Mono<ContentResponse> {
    return validate()
        .flatMap { data ->                   // LEVEL 1
            save(data.content)
                .flatMap { content ->        // LEVEL 2
                    saveMetadata(content)
                        .flatMap { metadata -> // LEVEL 3
                            attachTags()
                                .map { tags -> ... }  // LEVEL 4, 5 - 읽기 힘듦!
                        }
                }
        }
}
```

**해결**: 복잡한 로직은 메서드로 분리

```kotlin
// ✅ DO - 메서드 분리로 평탄화
fun createContent(userId: UUID, request: Request): Mono<ContentResponse> {
    return validateSession(userId, request)
        .flatMap { session -> saveContent(session) }
        .flatMap { saved -> attachTags(saved, request) }
        .flatMap { saved -> savePhotos(saved, request) }
        .map { result -> ContentResponse.from(result) }
}
```

---

### 2. Tuple 대신 Data Class 사용

**규칙**: `Mono.zip()` 결과는 Data Class로 타입 안전성 확보

```kotlin
// ❌ DON'T - Tuple은 가독성 저하
Mono.zip(userInfoMono, replyCountMono, likeCountMono, likedIdsMono)
    .map { tuple ->
        val userInfoMap = tuple.t1      // t1, t2, t3가 뭔지 불명확
        val replyCountMap = tuple.t2
        val likeCountMap = tuple.t3
        val likedIds = tuple.t4
        // ...
    }

// ✅ DO - Data Class로 명확하게
data class CommentMetadata(
    val userInfoMap: Map<UUID, UserInfo>,
    val replyCountMap: Map<UUID?, Int>,
    val likeCountMap: Map<UUID?, Int>,
    val likedCommentIds: Set<UUID>
)

Mono.zip(userInfoMono, replyCountMono, likeCountMono, likedIdsMono)
    { userInfo, replyCounts, likeCounts, likedIds ->
        CommentMetadata(userInfo, replyCounts, likeCounts, likedIds)
    }
    .map { metadata ->
        // metadata.userInfoMap 처럼 명확하게 접근
        buildResponse(metadata)
    }
```

---

### 3. N+1 쿼리 절대 금지

**규칙**: 반복문 내부에서 Repository 호출 금지. 배치 쿼리 + 메모리 그룹화 사용

```kotlin
// ❌ DON'T - 각 옵션마다 DB 쿼리 (N번 실행)
return Flux.fromIterable(options)
    .flatMap { option ->
        quizAttemptRepository.findByQuizId(quizId)  // ← N번 반복!
            .filter { it.optionId == option.id }
            .reduce(0, Int::plus)
    }
    .collectList()

// ✅ DO - 1번 쿼리 후 메모리 그룹화
return quizAttemptRepository.findByQuizId(quizId)
    .collectList()                           // ← 1번만 실행
    .flatMap { allAttempts ->
        val answersMap = allAttempts.groupBy { it.optionId }
        Flux.fromIterable(options)
            .map { option ->
                val count = answersMap[option.id]?.size ?: 0
                QuizOptionResponse(selectionCount = count, ...)
            }
            .collectList()
    }
```

**성능 비교**:
- N+1 방식: 옵션 4개 × 시도 100개 = 400번 쿼리 (250ms)
- 배치 방식: 1번 쿼리 (25ms) - **10배 빠름**

---

### 4. 독립적인 작업은 병렬 실행

**규칙**: `Mono.zip()`으로 병렬 실행하여 성능 최적화

```kotlin
// ✅ DO - 4개 쿼리 병렬 실행
fun getComments(...): Mono<CommentListResponse> {
    return commentRepository.findByContentId(contentId)
        .collectList()
        .flatMap { comments ->
            // 4개 쿼리를 동시 실행
            val userInfoMono = findUserInfos(comments)
            val replyCountMono = countReplies(comments)
            val likeCountMono = countLikes(comments)
            val likedIdsMono = findLikedIds(comments)

            Mono.zip(userInfoMono, replyCountMono, likeCountMono, likedIdsMono)
                { userInfo, replies, likes, likedIds ->
                    CommentMetadata(userInfo, replies, likes, likedIds)
                }
        }
        .map { metadata -> buildResponse(metadata) }
}
```

**성능 비교**:
- 순차 실행: 100ms + 100ms + 100ms + 100ms = 400ms
- 병렬 실행: max(100ms) = 100ms - **4배 빠름**

---

### 5. Blocking 연산 절대 금지

**규칙**: Production 코드에서 Blocking 호출 사용 금지

```kotlin
// ❌ DON'T - Blocking 연산
return Mono.fromCallable {
    val session = repository.findById(id).orElseThrow { ... }  // Blocking!
}

// ✅ DO - Reactive 방식
return repository.findByIdReactive(id)
    .switchIfEmpty(Mono.error(NotFoundException(id)))
```

**체크 포인트**:
- `.block()` 사용 금지
- `.orElseThrow()` in `Mono.fromCallable` 금지
- Blocking Repository (Optional 반환) 사용 금지
- 모든 Repository는 `Mono`/`Flux` 반환

---

### 6. 메서드 분리 기준

**규칙**: flatMap 내부 로직이 10줄 이상이면 메서드로 분리

```kotlin
// ❌ DON'T - 긴 로직을 flatMap 안에
.flatMap { quiz ->
    // 30줄짜리 복잡한 로직
    quizAttemptRepository.findLatest(...)
        .flatMap { ... }
        .map { ... }
        // ...
}

// ✅ DO - 메서드로 분리
.flatMap { quiz -> buildQuizResult(userId, quiz) }

private fun buildQuizResult(userId: String, quiz: Quiz): Mono<QuizResultDto> {
    // 30줄 로직을 별도 메서드로
}
```

---

## 체크리스트

### 코드 작성 전
- [ ] 이 기능은 3단계 중첩으로 구현 가능한가?
- [ ] Blocking 연산이 포함되지 않았는가?
- [ ] 반복문 내부에서 Repository 호출하지 않는가?

### 코드 작성 중
- [ ] `Mono.zip()` 사용 시 Data Class로 받는가?
- [ ] 독립적인 작업은 병렬 실행하는가?
- [ ] flatMap 내부가 10줄 이하인가?

### 코드 리뷰 시
- [ ] Reactive Chain이 3단계 이하인가?
- [ ] N+1 쿼리 문제가 없는가?
- [ ] 모든 Repository가 Reactive인가?

---

## 빠른 참조

### 좋은 패턴 ✅

```kotlin
// 1. 메서드 분리로 의도 명확히
fun main(): Mono<Response> {
    return validate()
        .flatMap { save(it) }
        .map { toResponse(it) }
}

// 2. Data Class로 타입 안전성
Mono.zip(a, b, c) { x, y, z -> MyData(x, y, z) }

// 3. 배치 쿼리
repository.findAll().collectList()
    .map { list -> list.groupBy { it.id } }

// 4. 병렬 실행
Mono.zip(queryA(), queryB(), queryC())
```

### 나쁜 패턴 ❌

```kotlin
// 1. 깊은 중첩 (5단계)
.flatMap { .flatMap { .flatMap { .flatMap { .map { } } } } }

// 2. Tuple 사용
.map { tuple -> tuple.t1, tuple.t2, tuple.t3 }

// 3. N+1 쿼리
Flux.fromIterable(list).flatMap { repository.find(it.id) }

// 4. Blocking 연산
Mono.fromCallable { repository.findById(id).orElseThrow() }
```

---

**마지막 업데이트**: 2025-12-29
