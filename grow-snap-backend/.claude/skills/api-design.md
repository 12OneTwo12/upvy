# GrowSnap Backend REST API 설계 규칙

> RESTful API 설계, HTTP 상태 코드, WebFlux 반환 타입 패턴을 정의합니다.

## URL 패턴

```
✅ 올바른 예시
GET    /api/v1/contents              # 목록 조회
POST   /api/v1/contents              # 생성
GET    /api/v1/contents/{id}         # 상세 조회
PUT    /api/v1/contents/{id}         # 전체 수정
PATCH  /api/v1/contents/{id}         # 부분 수정
DELETE /api/v1/contents/{id}         # 삭제
POST   /api/v1/contents/{id}/like    # 좋아요 (액션)

❌ 잘못된 예시
GET    /api/v1/getAllContents        # 동사 사용 금지
POST   /api/v1/createContents         # 동사 사용 금지
GET    /api/v1/contents/get/{id}     # 불필요한 동사
```

### URL 패턴 규칙

- ✅ **명사 사용**: 리소스는 명사로 표현
- ✅ **복수형 사용**: 컬렉션은 복수형 (contents, users, comments)
- ✅ **소문자 사용**: 모든 URL은 소문자
- ✅ **하이픈 사용**: 단어 구분은 하이픈 (-)
- ❌ **동사 금지**: createVideo, getVideo 등 동사 사용 금지
- ❌ **언더스코어 금지**: video_list 대신 video-list 사용

## HTTP 상태 코드

```kotlin
200 OK              // 조회, 수정 성공
201 Created         // 생성 성공
204 No Content      // 삭제 성공
400 Bad Request     // 잘못된 요청
401 Unauthorized    // 인증 실패
403 Forbidden       // 권한 없음
404 Not Found       // 리소스 없음
409 Conflict        // 중복, 충돌
500 Internal Server Error  // 서버 오류
```

### 상태 코드 사용 가이드

| 상황 | 상태 코드 | 예시 |
|-----|---------|-----|
| **성공적인 조회** | 200 OK | GET /contents/{id} |
| **성공적인 생성** | 201 Created | POST /contents |
| **성공적인 수정** | 200 OK | PUT /contents/{id} |
| **성공적인 삭제** | 204 No Content | DELETE /contents/{id} |
| **잘못된 요청** | 400 Bad Request | 필수 필드 누락, Validation 실패 |
| **인증 실패** | 401 Unauthorized | 토큰 없음, 만료된 토큰 |
| **권한 없음** | 403 Forbidden | 본인이 아닌 리소스 수정 시도 |
| **리소스 없음** | 404 Not Found | 존재하지 않는 ID 조회 |
| **중복/충돌** | 409 Conflict | 이미 존재하는 이메일로 가입 시도 |

## 응답 형식

**반드시 `ResponseEntity` 사용**

```kotlin
org.springframework.http.ResponseEntity 사용할것
```

## WebFlux Controller 반환 타입 패턴

**원칙: 일관성 있게 `Mono<ResponseEntity<T>>` 패턴 사용**

### 1. `Mono<ResponseEntity<T>>` - 권장 패턴 ✅

**사용 시기**: 대부분의 경우 (기본 패턴)

- HTTP 상태 코드, 헤더, 바디 모두를 비동기적으로 결정
- 비동기 처리 결과에 따라 상태 코드를 다르게 반환 가능
- 에러 핸들링이 유연함

```kotlin
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

    /**
     * ✅ GOOD: Mono<ResponseEntity<T>> 패턴
     *
     * 비동기 처리 결과에 따라 상태 코드를 다르게 반환 가능
     */
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: UUID): Mono<ResponseEntity<UserResponse>> {
        return userService.findById(id)
            .map { user -> ResponseEntity.ok(user) }              // 200 OK
            .defaultIfEmpty(ResponseEntity.notFound().build())     // 404 Not Found
    }

    @PostMapping
    fun createUser(@RequestBody request: UserCreateRequest): Mono<ResponseEntity<UserResponse>> {
        return userService.create(request)
            .map { user -> ResponseEntity.status(HttpStatus.CREATED).body(user) }  // 201 Created
    }

    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: UUID): Mono<ResponseEntity<Void>> {
        return userService.delete(id)
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))  // 204 No Content
    }
}
```

### 2. `ResponseEntity<Mono<T>>` - 제한적 사용 ⚠️

**사용 시기**: 상태 코드와 헤더를 즉시 결정할 수 있고, 바디만 비동기 처리

- 상태 코드와 헤더가 미리 확정됨
- 바디 데이터만 비동기로 제공
- **대부분의 경우 `Mono<ResponseEntity<T>>`가 더 적합**

```kotlin
// ⚠️ 제한적 사용: 상태 코드가 항상 200 OK로 확정된 경우
@GetMapping("/stats")
fun getStats(): ResponseEntity<Mono<StatsResponse>> {
    // 상태 코드는 즉시 200 OK로 결정, 바디만 비동기 처리
    return ResponseEntity.ok(userService.calculateStats())
}
```

### 3. `Mono<T>` - 간단한 경우 ⚠️

**사용 시기**: 항상 200 OK를 반환하는 단순한 경우

- HTTP 상태 코드를 커스터마이즈할 필요가 없을 때
- Spring WebFlux가 자동으로 200 OK 반환
- **하지만 명시적으로 `Mono<ResponseEntity<T>>`를 사용하는 것이 더 명확함**

```kotlin
// ⚠️ 간단하지만 명시적이지 않음
@GetMapping("/simple")
fun getSimple(): Mono<UserResponse> {
    return userService.findById(userId)  // 자동으로 200 OK
}

// ✅ BETTER: 명시적으로 상태 코드 지정
@GetMapping("/simple")
fun getSimple(): Mono<ResponseEntity<UserResponse>> {
    return userService.findById(userId)
        .map { ResponseEntity.ok(it) }  // 명시적으로 200 OK
}
```

### 4. `Flux<T>` vs `Mono<ResponseEntity<Flux<T>>>` - 스트리밍

**스트리밍 응답 (Server-Sent Events, Streaming JSON)**

```kotlin
// ✅ GOOD: 스트리밍 응답 (여러 개의 데이터를 순차적으로 전송)
@GetMapping(value = ["/stream"], produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
fun streamUsers(): Flux<UserResponse> {
    return userService.findAll()  // 스트리밍으로 전송
}

// ✅ GOOD: 컬렉션을 한 번에 반환
@GetMapping("/all")
fun getAllUsers(): Mono<ResponseEntity<List<UserResponse>>> {
    return userService.findAll()
        .collectList()
        .map { ResponseEntity.ok(it) }
}
```

## WebFlux Controller 반환 타입 선택 가이드

| 상황 | 권장 반환 타입 | 이유 |
|------|--------------|------|
| **일반적인 CRUD API** | `Mono<ResponseEntity<T>>` | 상태 코드, 헤더, 바디 모두 비동기 결정 |
| **조건부 상태 코드** (200/404) | `Mono<ResponseEntity<T>>` | `defaultIfEmpty()`로 404 처리 |
| **리스트 반환** | `Mono<ResponseEntity<List<T>>>` | `collectList()`로 변환 후 반환 |
| **스트리밍 응답** (SSE) | `Flux<T>` | Server-Sent Events 스트리밍 |
| **삭제 API** (바디 없음) | `Mono<ResponseEntity<Void>>` | 204 No Content |
| **생성 API** | `Mono<ResponseEntity<T>>` | 201 Created |

## 피해야 할 패턴 ❌

```kotlin
// ❌ BAD: 블로킹 호출
@GetMapping("/{id}")
fun getUser(@PathVariable id: UUID): ResponseEntity<UserResponse> {
    val user = userService.findById(id).block()!!  // 블로킹!
    return ResponseEntity.ok(user)
}

// ❌ BAD: ResponseEntity를 Mono로 감싸지 않음 (비일관성)
@GetMapping("/inconsistent")
fun inconsistentReturn(): UserResponse {
    // 상태 코드 제어 불가
}
```

## 정리

1. **기본 패턴**: `Mono<ResponseEntity<T>>` 사용 (가장 유연함)
2. **상태 코드 제어**: `.map { ResponseEntity.status(...).body(it) }`
3. **404 처리**: `.defaultIfEmpty(ResponseEntity.notFound().build())`
4. **스트리밍**: `Flux<T>`만 사용 (SSE, Streaming JSON)
5. **일관성 유지**: 프로젝트 전체에서 동일한 패턴 사용

## REST Docs 작성 (필수)

### 모든 API 테스트에 document() 추가

```kotlin
@Test
@DisplayName("비디오 목록 조회 시, 페이지네이션된 목록을 반환한다")
fun getContents_ReturnsPagedList() {
    // Given, When, Then...

    webTestClient.get()
        .uri("/api/v1/contents?page=0&size=10")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .consumeWith(
            document("contents-list",
                queryParameters(
                    parameterWithName("page").description("페이지 번호 (0부터 시작)"),
                    parameterWithName("size").description("페이지 크기")
                ),
                responseFields(
                    fieldWithPath("content[].id").description("비디오 ID"),
                    fieldWithPath("content[].title").description("비디오 제목")
                )
            )
        )
}
```

### REST Docs 체크리스트

- [ ] **모든 API에 document() 추가**: 테스트에서 API 문서 자동 생성
- [ ] **Request Fields 문서화**: requestFields() 사용
- [ ] **Response Fields 문서화**: responseFields() 사용
- [ ] **Query Parameters 문서화**: queryParameters() 사용
- [ ] **Path Parameters 문서화**: pathParameters() 사용
- [ ] **AsciiDoc 생성 확인**: build 후 docs 디렉토리 확인

## KDoc 작성 규칙

### 클래스 KDoc 템플릿

```kotlin
/**
 * 비디오 관련 비즈니스 로직을 처리하는 서비스
 *
 * 비디오 생성, 조회, 수정, 삭제 및 S3 업로드 URL 생성 기능을 제공합니다.
 *
 * @property videoRepository 비디오 데이터베이스 액세스를 위한 레포지토리
 * @property s3Service S3 파일 업로드 URL 생성 서비스
 */
@Service
class VideoServiceImpl(
    private val videoRepository: VideoRepository,
    private val s3Service: S3Service
) : VideoService {
    // ...
}
```

### 함수 KDoc 템플릿

```kotlin
/**
 * 새로운 비디오를 생성합니다.
 *
 * 비디오 메타데이터를 데이터베이스에 저장하고,
 * S3 업로드를 위한 Presigned URL을 생성하여 반환합니다.
 *
 * ### 처리 흐름
 * 1. 비디오 엔티티 생성 및 저장
 * 2. S3 Presigned URL 생성
 * 3. 비디오 응답 반환
 *
 * @param request 비디오 생성 요청 정보
 * @return 생성된 비디오 정보를 담은 Mono
 * @throws VideoException.VideoCreationException 비디오 생성 실패 시
 */
@Transactional
override fun createVideo(request: VideoCreateRequest): Mono<VideoResponse> {
    logger.info("Creating video: title=${request.title}")

    return videoRepository.save(request.toEntity())
        .flatMap { saved ->
            s3Service.generateUploadUrl(saved.id)
                .map { saved.toResponse(it) }
        }
        .doOnSuccess { logger.info("Video created: id=${it.id}") }
        .doOnError { logger.error("Failed to create video", it) }
        .onErrorMap { VideoException.VideoCreationException(it.message) }
}
```

## API 설계 체크리스트

**모든 API 개발 시 반드시 확인**:

- [ ] **RESTful URL**: 명사 사용, 동사 금지
- [ ] **HTTP 메서드**: 적절한 메서드 사용 (GET, POST, PUT, DELETE)
- [ ] **HTTP 상태 코드**: 적절한 상태 코드 반환
- [ ] **Mono<ResponseEntity<T>>**: 일관된 반환 타입 사용
- [ ] **404 처리**: defaultIfEmpty()로 리소스 없음 처리
- [ ] **REST Docs**: 모든 API에 document() 추가
- [ ] **KDoc**: 모든 public 함수/클래스에 KDoc 작성
- [ ] **Validation**: Bean Validation 적용 (@Valid)
