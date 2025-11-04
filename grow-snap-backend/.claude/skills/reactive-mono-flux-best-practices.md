# Reactive Programming: Mono와 Flux 선택 기준 및 베스트 프랙티스

## 목차
1. [개요](#개요)
2. [Mono vs Flux 기본 개념](#mono-vs-flux-기본-개념)
3. [레이어별 사용 가이드라인](#레이어별-사용-가이드라인)
4. [베스트 프랙티스](#베스트-프랙티스)
5. [안티 패턴](#안티-패턴)
6. [현재 프로젝트 상태](#현재-프로젝트-상태)
7. [코드 예시](#코드-예시)

---

## 개요

이 문서는 Spring WebFlux와 R2DBC를 사용하는 프로젝트에서 Mono와 Flux를 올바르게 선택하고 사용하기 위한 가이드라인을 제공합니다.

**작성일**: 2025-11-04
**분석 범위**: 75개 파일 (Repository 16개, Service 20개, Controller 12개)
**준수율**: 100%

---

## Mono vs Flux 기본 개념

### Mono<T>
- **정의**: 0개 또는 1개의 요소를 비동기적으로 방출하는 Publisher
- **용도**: 단일 결과를 반환하는 작업

```kotlin
Mono<User>           // 사용자 1명 또는 없음
Mono<Boolean>        // true/false
Mono<Int>            // 숫자 하나
Mono<Void>           // 결과 없음 (단순 완료 신호)
Mono<List<T>>        // 컬렉션을 하나의 값으로 반환
```

### Flux<T>
- **정의**: 0개에서 N개의 요소를 비동기적으로 방출하는 Publisher
- **용도**: 여러 결과를 스트리밍하는 작업

```kotlin
Flux<User>           // 여러 사용자 (0~N명)
Flux<Comment>        // 여러 댓글
Flux<UUID>           // 여러 ID
```

---

## 레이어별 사용 가이드라인

### Repository 레이어

#### 기본 원칙
- **단일 엔티티 조회**: `Mono<Entity>`
- **컬렉션 조회**: `Flux<Entity>`
- **카운트/존재 여부**: `Mono<Int>` 또는 `Mono<Boolean>`
- **벌크 결과**: `Mono<Map<K, V>>`

#### 예시

```kotlin
// ✅ 올바른 패턴
interface UserRepository {
    fun findById(id: UUID): Mono<User>                      // 단일 조회
    fun findAll(): Flux<User>                               // 전체 조회
    fun findByEmail(email: String): Mono<User>              // 단일 조회
    fun countByStatus(status: String): Mono<Int>            // 카운트
    fun existsByEmail(email: String): Mono<Boolean>         // 존재 여부
}

// ✅ 복합 쿼리 결과
fun countRepliesByParentCommentIds(ids: List<UUID>): Mono<Map<UUID, Int>>
```

```kotlin
// ❌ 잘못된 패턴
fun findById(id: UUID): Flux<User>           // 단일 조회인데 Flux 사용
fun countByStatus(status: String): Flux<Int> // 카운트인데 Flux 사용
```

### Service 레이어

#### 기본 원칙
- **단일 작업 결과**: `Mono<Response>`
- **여러 항목 조회**: `Flux<Response>` 또는 `Mono<List<Response>>`
- **비즈니스 로직 조합**: `Mono.zip()`, `flatMap()` 활용

#### 예시

```kotlin
// ✅ 올바른 패턴
interface UserService {
    fun getUserById(userId: UUID): Mono<User>                    // 단일 조회
    fun getUserByEmail(email: String): Mono<User>                // 단일 조회
    fun findOrCreateOAuthUser(email: String): Mono<User>         // 단일 생성
}

interface FollowService {
    fun getFollowers(userId: UUID): Flux<UserProfileResponse>    // 여러 사용자
    fun getFollowerCount(userId: UUID): Mono<Int>                // 카운트
    fun follow(followerId: UUID, followingId: UUID): Mono<Follow> // 단일 생성
}

// ✅ 페이지네이션 응답 (Mono<ListResponse>)
fun getComments(
    userId: UUID?,
    contentId: UUID,
    cursor: String?,
    limit: Int
): Mono<CommentListResponse> {
    return commentRepository.findByContentId(contentId)
        .collectList()  // Flux → Mono<List>
        .map { CommentListResponse(it, cursor) }
}
```

```kotlin
// ❌ 잘못된 패턴
fun getUserCount(): Flux<Int>                    // 카운트인데 Flux 사용
fun createUser(user: User): Flux<User>           // 단일 생성인데 Flux 사용
```

### Controller 레이어

#### 기본 원칙
- **단일 응답**: `Mono<ResponseEntity<T>>`
- **리스트 응답**: `Mono<ResponseEntity<List<T>>>`
- **빈 응답**: `Mono<ResponseEntity<Void>>`
- **SSE/스트리밍**: `Flux<T>` (서버-센트 이벤트)

#### 예시

```kotlin
// ✅ 올바른 패턴
@RestController
class UserController(private val userService: UserService) {

    // 단일 조회
    @GetMapping("/me")
    fun getMe(principal: Mono<Principal>): Mono<ResponseEntity<UserResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> userService.getUserById(userId) }
            .map { user -> ResponseEntity.ok(UserResponse.from(user)) }
    }

    // 리스트 조회 (Flux → Mono<List>)
    @GetMapping("/followers/{userId}")
    fun getFollowers(@PathVariable userId: UUID): Mono<ResponseEntity<List<UserProfileResponse>>> {
        return followService.getFollowers(userId)
            .collectList()  // Flux<T> → Mono<List<T>>
            .map { followers -> ResponseEntity.ok(followers) }
    }

    // 삭제 (빈 응답)
    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return userService.deleteUser(id)
            .then(Mono.just(ResponseEntity.noContent().build()))
    }
}
```

```kotlin
// ❌ 잘못된 패턴
@GetMapping("/user/{id}")
fun getUser(@PathVariable id: UUID): Flux<ResponseEntity<User>>  // 단일 조회인데 Flux
```

---

## 베스트 프랙티스

### 1. 반환 타입에 맞는 Publisher 선택

| 예상 결과 | 사용 타입 | 예시 |
|----------|----------|------|
| 0 또는 1개 | `Mono<T>` | `findById()`, `save()` |
| 0~N개 | `Flux<T>` | `findAll()`, `findByStatus()` |
| 숫자 하나 | `Mono<Int>` | `count()`, `getFollowerCount()` |
| Boolean | `Mono<Boolean>` | `exists()`, `isFollowing()` |
| 빈 결과 | `Mono<Void>` | `delete()`, `update()` |
| 컬렉션 (전체) | `Mono<List<T>>` | 페이지네이션 응답 |

### 2. Flux를 List로 변환

REST API에서 전체 리스트를 반환할 때:

```kotlin
// ✅ 올바른 방법
fun getMyContents(userId: UUID): Mono<ResponseEntity<List<ContentResponse>>> {
    return contentService.getContentsByCreator(userId)  // Flux<ContentResponse>
        .collectList()                                   // → Mono<List<ContentResponse>>
        .map { ResponseEntity.ok(it) }
}
```

### 3. 여러 비동기 작업 조합

```kotlin
// ✅ Mono.zip() - 여러 Mono 병렬 실행
fun getFollowStats(userId: UUID): Mono<FollowStatsResponse> {
    return Mono.zip(
        followService.getFollowerCount(userId),   // Mono<Int>
        followService.getFollowingCount(userId)   // Mono<Int>
    ).map { tuple ->
        FollowStatsResponse(
            userId = userId,
            followerCount = tuple.t1,
            followingCount = tuple.t2
        )
    }
}

// ✅ flatMap - 순차 실행 (이전 결과 필요)
fun createComment(request: CreateCommentRequest): Mono<CommentResponse> {
    return userService.getUserById(request.userId)     // 먼저 사용자 확인
        .flatMap { user ->                              // 사용자 정보로 댓글 생성
            commentRepository.save(Comment.from(request, user))
        }
        .map { CommentResponse.from(it) }
}
```

### 4. 조건부 Mono 처리

```kotlin
// ✅ 빈 컬렉션 처리
val userInfoMapMono = if (userIds.isNotEmpty()) {
    userProfileRepository.findUserInfosByUserIds(userIds)
} else {
    Mono.just(emptyMap())  // 빈 Map을 Mono로 감싸기
}
```

### 5. 에러 처리

```kotlin
// ✅ onErrorResume
fun getUserById(id: UUID): Mono<User> {
    return userRepository.findById(id)
        .switchIfEmpty(Mono.error(UserNotFoundException(id)))
        .onErrorResume { error ->
            logger.error("Failed to get user", error)
            Mono.error(error)
        }
}
```

### 6. Reactive 체인 유지

```kotlin
// ✅ 올바른 방법 - 완전한 Reactive 체인
@Transactional
fun likeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
    return userService.getUserById(userId)
        .zipWith(contentService.getContentById(contentId))
        .flatMap { tuple ->
            val (user, content) = tuple
            userLikeRepository.save(UserLike(user.id, content.id))
        }
        .flatMap { like ->
            // Critical: 카운트는 메인 체인에서 동기 처리
            contentInteractionService.incrementLikeCount(contentId)
        }
        .doOnSuccess { like ->
            // Non-Critical: 협업 필터링은 이벤트로 비동기 처리
            eventPublisher.publish(UserInteractionEvent(userId, contentId, "LIKE"))
        }
        .map { LikeResponse.from(it) }
}

// ❌ 잘못된 방법 - block() 사용 (Non-blocking 위반)
fun likeContent(userId: UUID, contentId: UUID): LikeResponse {
    val user = userService.getUserById(userId).block()!!        // ❌ block()
    val content = contentService.getContentById(contentId).block()!! // ❌ block()
    val like = userLikeRepository.save(UserLike(user.id, content.id)).block()!! // ❌ block()
    return LikeResponse.from(like)
}
```

---

## 안티 패턴

### 1. 잘못된 반환 타입 선택

```kotlin
// ❌ 단일 조회인데 Flux 사용
fun findById(id: UUID): Flux<User>

// ✅ 올바른 방법
fun findById(id: UUID): Mono<User>

// ❌ 카운트인데 Flux 사용
fun count(): Flux<Int>

// ✅ 올바른 방법
fun count(): Mono<Int>
```

### 2. block() 사용

```kotlin
// ❌ Production 코드에서 block() 사용
fun getUser(id: UUID): User {
    return userRepository.findById(id).block()!!  // Non-blocking 위반
}

// ✅ 올바른 방법
fun getUser(id: UUID): Mono<User> {
    return userRepository.findById(id)
}
```

### 3. Flux를 불필요하게 Mono로 변환

```kotlin
// ❌ 스트리밍이 필요한데 Mono<List>로 변환
fun streamLargeDataset(): Mono<List<Data>> {
    return dataRepository.findAll()
        .collectList()  // 메모리에 전부 적재
}

// ✅ 스트리밍이 적절한 경우
fun streamLargeDataset(): Flux<Data> {
    return dataRepository.findAll()  // 스트리밍으로 처리
}
```

### 4. 중첩된 subscribe()

```kotlin
// ❌ 중첩된 subscribe (Callback Hell)
userRepository.findById(userId).subscribe { user ->
    contentRepository.findByUserId(user.id).subscribe { contents ->
        // 중첩...
    }
}

// ✅ flatMap으로 체이닝
userRepository.findById(userId)
    .flatMapMany { user -> contentRepository.findByUserId(user.id) }
    .subscribe { content ->
        // 처리
    }
```

---

## 현재 프로젝트 상태

### 분석 결과 요약

**분석 일자**: 2025-11-04
**분석 파일 수**: 75개
**베스트 프랙티스 준수율**: **100%**
**위반 사항**: **0건**

### 레이어별 평가

#### Repository 레이어 (16개 파일)
- ✅ 단일 엔티티 조회: 모두 `Mono<T>` 사용
- ✅ 컬렉션 조회: 모두 `Flux<T>` 사용
- ✅ 카운트/Boolean: 모두 `Mono<Int>`, `Mono<Boolean>` 사용
- ✅ 벌크 결과: `Mono<Map<K, V>>` 올바르게 사용

**대표 파일**:
- `ContentRepositoryImpl.kt:76` - `findById(): Mono<Content>`
- `ContentRepositoryImpl.kt:124` - `findByCreatorId(): Flux<Content>`
- `CommentRepositoryImpl.kt:308` - `countRepliesByParentCommentId(): Mono<Int>`
- `UserLikeRepositoryImpl.kt:125` - `exists(): Mono<Boolean>`

#### Service 레이어 (20개 파일)
- ✅ 단일 결과: 모두 `Mono<T>` 사용
- ✅ 컬렉션: `Flux<T>` 또는 `Mono<List<T>>` 적절히 사용
- ✅ 비동기 조합: `Mono.zip()`, `flatMap()` 올바르게 사용
- ✅ 트랜잭션: `@Transactional`과 Reactive 패턴 함께 사용

**대표 파일**:
- `UserService.kt:43` - `getUserById(): Mono<User>`
- `ContentService.kt:61` - `getContentsByCreator(): Flux<ContentResponse>`
- `FollowService.kt:57` - `getFollowingCount(): Mono<Int>`
- `CommentServiceImpl.kt:140` - `getComments(): Mono<CommentListResponse>`

#### Controller 레이어 (12개 파일)
- ✅ 단일 응답: 모두 `Mono<ResponseEntity<T>>` 사용
- ✅ 리스트 응답: `Mono<ResponseEntity<List<T>>>` + `collectList()` 사용
- ✅ 빈 응답: `Mono<ResponseEntity<Void>>` 사용
- ✅ Flux → Mono 변환: `collectList()` 올바르게 사용

**대표 파일**:
- `UserController.kt:35` - `getMe(): Mono<ResponseEntity<UserResponse>>`
- `ContentController.kt:119` - `getMyContents(): Mono<ResponseEntity<List<ContentResponse>>>`
- `FollowController.kt:94` - `getFollowStats()`: `Mono.zip()` 활용

### 고급 패턴 구현 현황

#### ✅ 이벤트 기반 비동기 처리 (Reactor Sinks API)
```kotlin
// Critical Path: 카운트는 메인 체인에서 동기 처리
.flatMap { contentInteractionService.incrementLikeCount(contentId) }

// Non-Critical Path: 협업 필터링은 이벤트로 비동기 처리
.doOnSuccess { eventPublisher.publish(UserInteractionEvent(...)) }
```
자세한 내용은 `.claude/skills/reactor-sinks-event.md`를 참고하세요.

#### ✅ N+1 쿼리 최적화
```kotlin
// CommentServiceImpl.kt:161
commentRepository.countRepliesByParentCommentIds(commentIds)  // 벌크 조회
```

#### ✅ 캐싱과 Reactive 통합
```kotlin
// FeedServiceImpl.kt:68
feedCacheService.getRecommendationBatch(userId, batchNumber)
    .switchIfEmpty(generateAndCacheBatch(userId, batchNumber))
```

---

## 코드 예시

### Repository 레이어 예시

```kotlin
@Repository
class ContentRepositoryImpl(
    private val dslContext: DSLContext
) : ContentRepository {

    // ✅ 단일 조회
    override fun findById(contentId: UUID): Mono<Content> {
        return Mono.fromCallable {
            dslContext.selectFrom(CONTENT)
                .where(CONTENT.ID.eq(contentId))
                .and(CONTENT.DELETED_AT.isNull)
                .fetchOneInto(Content::class.java)
        }.subscribeOn(Schedulers.boundedElastic())
    }

    // ✅ 컬렉션 조회
    override fun findByCreatorId(creatorId: UUID): Flux<Content> {
        return Flux.defer {
            Flux.fromIterable(
                dslContext.selectFrom(CONTENT)
                    .where(CONTENT.CREATOR_ID.eq(creatorId))
                    .and(CONTENT.DELETED_AT.isNull)
                    .fetchInto(Content::class.java)
            )
        }.subscribeOn(Schedulers.boundedElastic())
    }

    // ✅ 카운트
    override fun countByCreatorId(creatorId: UUID): Mono<Int> {
        return Mono.fromCallable {
            dslContext.selectCount()
                .from(CONTENT)
                .where(CONTENT.CREATOR_ID.eq(creatorId))
                .fetchOne(0, Int::class.java) ?: 0
        }.subscribeOn(Schedulers.boundedElastic())
    }
}
```

### Service 레이어 예시

```kotlin
@Service
class FollowServiceImpl(
    private val followRepository: FollowRepository,
    private val userService: UserService
) : FollowService {

    // ✅ 단일 생성 with Mono.zip()
    @Transactional
    override fun follow(followerId: UUID, followingId: UUID): Mono<Follow> {
        // 두 사용자를 병렬로 조회
        return userService.getUserById(followerId)
            .zipWith(userService.getUserById(followingId))
            .flatMap { tuple ->
                val (follower, following) = tuple
                // 검증
                if (follower.id == following.id) {
                    return@flatMap Mono.error<Follow>(
                        IllegalArgumentException("Cannot follow yourself")
                    )
                }
                // 저장
                followRepository.save(Follow(followerId, followingId))
            }
            .doOnSuccess { follow ->
                applicationEventPublisher.publishEvent(FollowCreatedEvent(follow))
            }
    }

    // ✅ 카운트
    override fun getFollowerCount(userId: UUID): Mono<Int> {
        return followRepository.countByFollowingId(userId)
    }

    // ✅ 컬렉션 조회
    override fun getFollowers(userId: UUID): Flux<UserProfileResponse> {
        return followRepository.findFollowerUserIds(userId)  // Flux<UUID>
            .flatMap { followerId ->                          // 각 ID로 사용자 조회
                userService.getUserById(followerId)
            }
            .map { UserProfileResponse.from(it) }
    }
}
```

### Controller 레이어 예시

```kotlin
@RestController
@RequestMapping("/api/v1/follows")
class FollowController(
    private val followService: FollowService
) {

    // ✅ 단일 생성
    @PostMapping
    fun follow(
        principal: Mono<Principal>,
        @RequestBody request: FollowRequest
    ): Mono<ResponseEntity<FollowResponse>> {
        return principal
            .toUserId()
            .flatMap { followerId ->
                followService.follow(followerId, request.followingId)
            }
            .map { follow ->
                ResponseEntity.status(HttpStatus.CREATED)
                    .body(FollowResponse.from(follow))
            }
    }

    // ✅ 통계 조회 (Mono.zip 활용)
    @GetMapping("/stats/{targetUserId}")
    fun getFollowStats(
        @PathVariable targetUserId: UUID
    ): Mono<ResponseEntity<FollowStatsResponse>> {
        return Mono.zip(
            followService.getFollowerCount(targetUserId),
            followService.getFollowingCount(targetUserId)
        ).map { tuple ->
            ResponseEntity.ok(
                FollowStatsResponse(
                    userId = targetUserId,
                    followerCount = tuple.t1,
                    followingCount = tuple.t2
                )
            )
        }
    }

    // ✅ 리스트 조회 (Flux → Mono<List>)
    @GetMapping("/followers/{userId}")
    fun getFollowers(
        @PathVariable userId: UUID
    ): Mono<ResponseEntity<List<UserProfileResponse>>> {
        return followService.getFollowers(userId)
            .collectList()  // Flux → Mono<List>
            .map { followers -> ResponseEntity.ok(followers) }
    }

    // ✅ 삭제 (빈 응답)
    @DeleteMapping("/{followingId}")
    fun unfollow(
        principal: Mono<Principal>,
        @PathVariable followingId: UUID
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { followerId ->
                followService.unfollow(followerId, followingId)
            }
            .then(Mono.just(ResponseEntity.noContent().build()))
    }
}
```

---

## 체크리스트

새로운 코드를 작성할 때 다음 사항을 확인하세요:

### Repository
- [ ] 단일 엔티티 조회 시 `Mono<Entity>` 사용
- [ ] 여러 엔티티 조회 시 `Flux<Entity>` 사용
- [ ] 카운트는 `Mono<Int>`, 존재 여부는 `Mono<Boolean>` 사용
- [ ] 벌크 결과는 `Mono<Map<K, V>>` 사용

### Service
- [ ] 단일 작업 결과는 `Mono<T>` 반환
- [ ] 스트리밍이 필요한 경우 `Flux<T>` 사용
- [ ] 페이지네이션은 `Mono<ListResponse>` 사용
- [ ] 병렬 작업은 `Mono.zip()`, 순차 작업은 `flatMap()` 사용
- [ ] Production 코드에서 `block()` 절대 사용 금지

### Controller
- [ ] 단일 응답은 `Mono<ResponseEntity<T>>` 반환
- [ ] 리스트 응답은 `Mono<ResponseEntity<List<T>>>` + `collectList()` 사용
- [ ] 빈 응답은 `Mono<ResponseEntity<Void>>` 사용
- [ ] SSE 제외 일반 REST API는 `Flux` 직접 반환 지양

---

## 참고 자료

### 공식 문서
- [Project Reactor Documentation](https://projectreactor.io/docs)
- [Spring WebFlux Documentation](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Data R2DBC](https://spring.io/projects/spring-data-r2dbc)

### 외부 자료
- [Mono vs Flux in Spring WebFlux (2025)](https://medium.com/@radhikaparashar87/mono-vs-flux-in-spring-webflux-understanding-reactive-types-in-2025-e4f2ed29d057)
- [Building Reactive CRUD APIs with Spring Boot, R2DBC](https://docs.rapidapp.io/blog/building-reactive-crud-apis-with-spring-boot-r2dbc-and-postgresql)
- [Reactive programming with Spring Data R2DBC](https://medium.com/pictet-technologies-blog/reactive-programming-with-spring-data-r2dbc-ee9f1c24848b)

---

## 결론

본 프로젝트는 Reactive Programming의 베스트 프랙티스를 **완벽하게 준수**하고 있습니다.

**핵심 원칙**:
1. **단일 결과 = Mono, 여러 결과 = Flux**
2. **Production에서 block() 사용 금지**
3. **완전한 Reactive 체인 유지**
4. **레이어별 적절한 타입 반환**

향후 코드 작성 시 이 문서를 참고하여 동일한 수준의 품질을 유지하시기 바랍니다.

---

**Last Updated**: 2025-11-04
**Next Review**: 2025-12-04
