# Upvy Backend MVC 계층 규칙

> Controller, Service, Repository 계층별 역할과 책임을 정의합니다.

## 프로젝트 구조 (MVC 패턴)

```
upvy-backend/
└── src/main/kotlin/me/onetwo/upvy/
    ├── domain/
    │   ├── video/
    │   │   ├── controller/       # VideoController.kt
    │   │   ├── service/          # VideoService.kt, VideoServiceImpl.kt
    │   │   ├── repository/       # VideoRepository.kt, VideoRepositoryImpl.kt
    │   │   ├── model/            # Video.kt (엔티티)
    │   │   ├── dto/              # Request/Response DTO
    │   │   └── exception/        # VideoException.kt
    │   ├── user/
    │   └── auth/
    └── infrastructure/
        ├── config/               # 설정
        ├── security/             # 보안
        └── redis/                # Redis
```

## 계층별 역할 (MVC Layer Responsibilities)

> **중요**: 각 계층은 자신의 책임에만 집중해야 합니다. 계층 간 책임을 명확히 분리하지 않으면 유지보수가 어려워지고 테스트가 복잡해집니다.

## Controller (컨트롤러)

**역할**: HTTP 요청/응답 처리만 담당

### 허용되는 역할

- ✅ HTTP 요청 수신 및 파라미터 추출
- ✅ Bean Validation (요청 검증)
- ✅ Service 호출 (비즈니스 로직 위임)
- ✅ HTTP 응답 생성 (상태 코드, 헤더, 바디)
- ✅ DTO 변환 (Entity → Response DTO)

### 금지되는 역할

- ❌ 비즈니스 로직 처리 금지
- ❌ 데이터베이스 접근 금지
- ❌ 복잡한 데이터 처리 금지 (FilePart 처리, 파일 변환 등)

### Principal 사용 규칙 (Spring Security 인증/인가)

**원칙**: userId를 파라미터로 받아야 한다면 Spring Security Context에서 `Mono<Principal>`로 추출해야 합니다.

**중요**: 이 프로젝트는 Spring Security OAuth2 Resource Server를 사용합니다. 사용자 ID는 JWT 토큰에서 추출되어 Spring Security Context에 저장됩니다.

**인증 방식**:
- JWT 토큰 기반 인증
- ReactiveJwtDecoder로 토큰 검증
- JwtAuthenticationConverter로 UUID principal 변환
- WebFlux 환경에서는 `Mono<Principal>`로 userId 추출

#### ✅ GOOD: Mono<Principal> 사용

```kotlin
@RestController
@RequestMapping("/api/v1/analytics")
class AnalyticsController(
    private val analyticsService: AnalyticsService
) {
    @PostMapping("/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun trackViewEvent(
        principal: Mono<Principal>,  // WebFlux에서 Principal 추출
        @Valid @RequestBody request: ViewEventRequest
    ): Mono<Void> {
        return principal
            .toUserId()  // Principal에서 userId 추출
            .flatMap { userId ->
                analyticsService.trackViewEvent(userId, request)
            }
    }
}
```

#### ❌ BAD: Request Body나 Path Variable로 userId 받기

```kotlin
// ❌ BAD: userId를 Request Body에서 받음 (보안 취약)
@PostMapping("/views")
fun trackViewEvent(
    @RequestBody request: ViewEventRequest  // userId가 request 안에 포함
): Mono<Void> {
    return analyticsService.trackViewEvent(request.userId, request)
}

// ❌ BAD: userId를 Path Variable로 받음 (변조 가능)
@PostMapping("/users/{userId}/views")
fun trackViewEvent(
    @PathVariable userId: UUID,  // 클라이언트가 임의로 변경 가능
    @RequestBody request: ViewEventRequest
): Mono<Void> {
    return analyticsService.trackViewEvent(userId, request)
}
```

### Principal 추출 체크리스트

- [ ] **userId는 Principal에서 추출**: Request Body나 Path Variable로 받지 않기
- [ ] **WebFlux 패턴**: `Mono<Principal>`로 받아 `.toUserId()`로 변환
- [ ] **JWT 토큰 검증 의존**: Spring Security가 토큰을 검증한 후 userId 제공
- [ ] **보안 우선**: 클라이언트가 userId를 임의로 변경할 수 없도록 설계

## Service (서비스)

**역할**: 비즈니스 로직 처리 (Reactive)

### 허용되는 역할

- ✅ 비즈니스 로직 구현
- ✅ 트랜잭션 관리 (@Transactional)
- ✅ 복잡한 데이터 처리 (FilePart 처리, 이미지 변환 등)
- ✅ 다른 서비스 호출 (서비스 간 조율)
- ✅ **Repository 호출 (데이터베이스 접근)**
- ✅ **Reactive 체이닝 (Repository가 반환하는 Mono/Flux를 flatMap, map 등으로 체이닝)**
- ✅ 예외 처리 및 변환

### 금지되는 역할

- ❌ HTTP 요청/응답 처리 금지
- ❌ HTTP 상태 코드 결정 금지
- ❌ **DSLContext 직접 사용 금지 (JOOQ 쿼리 금지)**

### Service 계층 규칙 상세

**절대 준수**: Service는 Repository를 통해서만 데이터베이스에 접근합니다.

```kotlin
// ✅ GOOD: Service가 Repository를 직접 체이닝 (Repository가 이미 Mono/Flux 반환)
@Service
class CommentServiceImpl(
    private val commentRepository: CommentRepository,
    private val userProfileRepository: UserProfileRepository
) : CommentService {

    override fun createComment(userId: UUID, contentId: UUID, request: CommentRequest): Mono<CommentResponse> {
        // Repository가 이미 Mono<Comment>를 반환하므로 직접 체이닝
        return commentRepository.save(
            Comment(
                contentId = contentId,
                userId = userId,
                content = request.content
            )
        ).switchIfEmpty(Mono.error(IllegalStateException("Failed to create comment")))
            .flatMap { savedComment ->
                // userProfileRepository도 Mono<Map>을 반환
                userProfileRepository.findUserInfosByUserIds(setOf(userId))
                    .map { userInfoMap ->
                        val (nickname, profileImageUrl) = userInfoMap[userId] ?: Pair("Unknown", null)
                        CommentResponse(
                            id = savedComment.id!!.toString(),
                            userNickname = nickname,
                            userProfileImageUrl = profileImageUrl,
                            content = savedComment.content
                        )
                    }
            }
    }

    override fun getComments(contentId: UUID): Flux<CommentResponse> {
        // Repository가 이미 Flux<Comment>를 반환하므로 직접 체이닝
        return commentRepository.findByContentId(contentId)
            .collectList()
            .flatMapMany { comments ->
                val userIds = comments.map { it.userId }.toSet()

                userProfileRepository.findUserInfosByUserIds(userIds)
                    .flatMapMany { userInfoMap ->
                        Flux.fromIterable(comments).map { comment ->
                            val userInfo = userInfoMap[comment.userId] ?: Pair("Unknown", null)
                            mapToCommentResponse(comment, userInfo)
                        }
                    }
            }
    }
}

// ❌ BAD: Service가 DSLContext를 직접 사용 (JOOQ 쿼리 실행)
@Service
class CommentServiceImpl(
    private val commentRepository: CommentRepository,
    private val dslContext: DSLContext  // ❌ Service에서 DSLContext 직접 사용
) : CommentService {

    override fun createComment(userId: UUID, contentId: UUID, request: CommentRequest): Mono<CommentResponse> {
        return commentRepository.save(
            Comment(
                contentId = contentId,
                userId = userId,
                content = request.content
            )
        ).flatMap { savedComment ->
            // ❌ Service에서 JOOQ 쿼리 직접 실행 (Repository 역할 침범)
            Mono.from(
                dslContext
                    .select(USER_PROFILES.NICKNAME, USER_PROFILES.PROFILE_IMAGE_URL)
                    .from(USER_PROFILES)
                    .where(USER_PROFILES.USER_ID.eq(userId.toString()))
            ).map { record ->
                CommentResponse(
                    id = savedComment.id!!.toString(),
                    userNickname = record.getValue(USER_PROFILES.NICKNAME) ?: "Unknown",
                    userProfileImageUrl = record.getValue(USER_PROFILES.PROFILE_IMAGE_URL)
                )
            }
        }
    }
}
```

### Service 체크리스트

- [ ] **Repository만 호출**: 데이터베이스 접근은 Repository를 통해서만
- [ ] **DSLContext 사용 금지**: Service에서 JOOQ 쿼리 직접 실행 금지
- [ ] **Reactive 체이닝**: Repository가 반환하는 Mono/Flux를 직접 체이닝
- [ ] **비즈니스 로직**: Repository를 조합하여 비즈니스 로직 구현

## Repository (레포지토리)

**역할**: 데이터베이스 CRUD (Reactive with JOOQ R2DBC)

### 허용되는 역할

- ✅ 데이터베이스 쿼리 실행 (JOOQ R2DBC 사용)
- ✅ Entity 저장/조회/수정/삭제
- ✅ **Reactive 타입 반환 (Mono<Entity>, Flux<Entity>, Mono<Boolean> 등)**
- ✅ **Non-blocking 데이터베이스 접근**

### 금지되는 역할

- ❌ 비즈니스 로직 금지
- ❌ 다른 Repository 호출 최소화

### Repository 계층 규칙 상세

**절대 준수**: Repository는 JOOQ R2DBC를 사용하여 Reactive 쿼리를 작성합니다.

```kotlin
// ✅ GOOD: Repository는 Reactive 타입 반환 (JOOQ R2DBC)
@Repository
class CommentRepositoryImpl(
    private val dslContext: DSLContext
) : CommentRepository {

    override fun save(comment: Comment): Mono<Comment> {
        val now = Instant.now()
        val commentId = comment.id ?: UUID.randomUUID()

        return Mono.from(
            dslContext
                .insertInto(COMMENTS)
                .set(COMMENTS.ID, commentId.toString())
                .set(COMMENTS.CONTENT, comment.content)
                .set(COMMENTS.CREATED_AT, now)
        ).thenReturn(
            Comment(
                id = commentId,
                content = comment.content,
                createdAt = now
            )
        )
    }

    override fun findById(commentId: UUID): Mono<Comment> {
        return Mono.from(
            dslContext
                .select(COMMENTS.ID, COMMENTS.CONTENT, COMMENTS.CREATED_AT)
                .from(COMMENTS)
                .where(COMMENTS.ID.eq(commentId.toString()))
                .and(COMMENTS.DELETED_AT.isNull)
        ).map { record -> recordToComment(record) }
    }

    override fun findByContentId(contentId: UUID): Flux<Comment> {
        return Flux.from(
            dslContext
                .select(COMMENTS.ID, COMMENTS.CONTENT, COMMENTS.CREATED_AT)
                .from(COMMENTS)
                .where(COMMENTS.CONTENT_ID.eq(contentId.toString()))
                .and(COMMENTS.DELETED_AT.isNull)
                .orderBy(COMMENTS.CREATED_AT.desc())
        ).map { record -> recordToComment(record) }
    }
}

// ❌ BAD: Repository가 순수 타입(blocking) 반환
@Repository
class CommentRepositoryImpl(
    private val dslContext: DSLContext
) : CommentRepository {

    // ❌ 순수 타입 반환은 blocking 호출을 유발
    override fun save(comment: Comment): Comment? {
        return dslContext
            .insertInto(COMMENTS)
            .set(COMMENTS.ID, comment.id.toString())
            .fetchOne()  // ❌ blocking 호출
            ?.let { recordToComment(it) }
    }
}
```

### Repository 체크리스트

- [ ] **JOOQ R2DBC 사용**: DSLContext를 사용한 Reactive 쿼리 작성
- [ ] **Reactive 타입 반환**: `Mono<Entity>`, `Flux<Entity>`, `Mono<Boolean>` 등 반환
- [ ] **Non-blocking 패턴**: `Mono.from()`, `Flux.from()`으로 JOOQ 쿼리를 Reactive로 변환
- [ ] **비즈니스 로직 없음**: 쿼리 실행만 담당

## 계층별 책임 예시: 프로필 이미지 업로드

### ❌ 잘못된 설계 (Controller에 비즈니스 로직)

```kotlin
// ❌ BAD: Controller가 FilePart 처리 (비즈니스 로직)를 수행
@RestController
class UserProfileController(
    private val imageUploadService: ImageUploadService  // Infrastructure 계층 직접 의존
) {
    @PostMapping("/image")
    fun uploadProfileImage(
        @RequestAttribute userId: UUID,
        @RequestPart("file") filePart: Mono<FilePart>
    ): Mono<ResponseEntity<ImageUploadResponse>> {
        // ❌ Controller가 FilePart 처리 - 비즈니스 로직!
        return filePart.content()
            .map { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                bytes
            }
            .reduce { acc, bytes -> acc + bytes }
            .flatMap { imageBytes ->
                val contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream"
                imageUploadService.uploadProfileImage(userId, imageBytes, contentType)
            }
            .map { imageUrl ->
                ResponseEntity.status(HttpStatus.CREATED).body(ImageUploadResponse(imageUrl))
            }
    }
}
```

**문제점**:
- Controller가 FilePart 처리 (바이트 배열 변환, Content-Type 추출) 수행
- Controller가 Infrastructure 계층 (ImageUploadService)에 직접 의존
- 비즈니스 로직이 Controller에 있어 재사용 불가능
- 테스트가 복잡해짐 (HTTP 계층과 비즈니스 로직이 섞임)

### ✅ 올바른 설계 (Service에 비즈니스 로직)

```kotlin
// ✅ GOOD: Controller는 HTTP 처리만, Service에 비즈니스 로직 위임
@RestController
class UserProfileController(
    private val userProfileService: UserProfileService  // Service 계층 의존
) {
    @PostMapping("/image")
    fun uploadProfileImage(
        @RequestAttribute userId: UUID,
        @RequestPart("file") filePart: Mono<FilePart>
    ): Mono<ResponseEntity<ImageUploadResponse>> {
        // ✅ Service에 비즈니스 로직 위임
        return filePart
            .flatMap { file ->
                userProfileService.uploadProfileImage(userId, file)
            }
            .map { imageUrl ->
                ResponseEntity.status(HttpStatus.CREATED).body(ImageUploadResponse(imageUrl))
            }
    }
}

// ✅ GOOD: Service가 비즈니스 로직 처리
@Service
class UserProfileServiceImpl(
    private val userProfileRepository: UserProfileRepository,
    private val imageUploadService: ImageUploadService
) : UserProfileService {
    override fun uploadProfileImage(userId: UUID, filePart: FilePart): Mono<String> {
        // ✅ FilePart 처리 로직은 Service에 위치
        return filePart.content()
            .map { dataBuffer ->
                val bytes = ByteArray(dataBuffer.readableByteCount())
                dataBuffer.read(bytes)
                bytes
            }
            .reduce { acc, bytes -> acc + bytes }
            .flatMap { imageBytes ->
                val contentType = filePart.headers().contentType?.toString() ?: "application/octet-stream"
                imageUploadService.uploadProfileImage(userId, imageBytes, contentType)
            }
    }
}
```

**장점**:
- Controller는 HTTP 처리만 담당 (단일 책임)
- Service에 비즈니스 로직이 있어 재사용 가능
- 테스트가 간단해짐 (Controller 테스트는 Service를 mock, Service 테스트는 단위 테스트)
- 계층 간 의존성이 명확함 (Controller → Service → Infrastructure)

## 서비스 간 의존성 패턴 (Service-to-Service Dependency)

### ✅ 허용되는 패턴 (Best Practice)

서비스에서 다른 서비스를 의존하는 것은 안티패턴은 아닙니다

```kotlin
// ✅ GOOD: 단방향 의존성
@Service
class UserProfileServiceImpl(
    private val userProfileRepository: UserProfileRepository,
    private val userService: UserService  // OK! 다른 서비스 의존
) : UserProfileService {

    override fun getProfile(userId: UUID): Mono<UserProfileResponse> {
        // 다른 서비스 호출은 문제없음
        return userService.findById(userId)
            .flatMap { user ->
                userProfileRepository.findByUserId(userId)
                    .map { profile -> profile.toResponse(user) }
            }
    }
}
```

### ⚠️ 주의사항

#### 1. 순환 의존성 금지 (Circular Dependency)

```kotlin
// ❌ BAD: 순환 의존성 발생
class UserService(private val profileService: ProfileService)
class ProfileService(private val userService: UserService)  // 순환!
```

#### 2. 복잡도 관리 - Facade 패턴 활용

```kotlin
// ⚠️ 3개 이상의 서비스 의존 시 Facade 패턴 고려
@Service
class UserProfileFacade(
    private val userService: UserService,
    private val profileService: UserProfileService,
    private val imageService: ImageUploadService,
    private val followService: FollowService
) {
    /**
     * 여러 서비스를 조율하는 복잡한 로직은 Facade에서 처리
     */
    fun createCompleteProfile(request: CreateProfileRequest): Mono<CompleteProfileResponse> {
        return userService.create(request.user)
            .flatMap { user ->
                profileService.create(user.id, request.profile)
                    .flatMap { profile ->
                        imageService.upload(user.id, request.image)
                            .map { CompleteProfileResponse(user, profile, it) }
                    }
            }
    }
}
```

### 서비스 의존성 체크리스트

- [ ] **단방향 의존성**: A → B는 허용, 하지만 B → A는 금지 (순환 의존 방지)
- [ ] **의존 이유 명확**: 왜 이 서비스가 필요한가? 책임이 명확한가?
- [ ] **복잡도 관리**: 3개 이상 서비스 의존 시 Facade/Orchestration 패턴 고려
- [ ] **SRP 준수**: 각 서비스의 단일 책임 원칙이 지켜지는가?
- [ ] **테스트 가능성**: 의존성 때문에 테스트가 어려워지지 않는가?

## 계층별 책임 체크리스트

**코드 작성 전 반드시 확인**:

- [ ] **Controller**: HTTP 요청/응답 처리만 하는가? 비즈니스 로직이 없는가?
- [ ] **Service**: 비즈니스 로직이 Service에 있는가? Controller에 비즈니스 로직이 없는가?
- [ ] **Repository**: 데이터베이스 쿼리만 수행하는가? 비즈니스 로직이 없는가?
- [ ] **테스트**: 각 계층을 독립적으로 테스트할 수 있는가?
