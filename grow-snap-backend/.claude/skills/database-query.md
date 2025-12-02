# GrowSnap Backend 데이터베이스 쿼리 규칙

> R2DBC + JOOQ 쿼리 작성 규칙, SELECT asterisk 금지, Soft Delete, Audit Trail 적용을 정의합니다.

## 중요 원칙

**⚠️ SELECT 쿼리에서 asterisk (*) 사용 절대 금지**

필요한 컬럼만 명시적으로 선택하여 성능을 최적화하고 코드 명확성을 향상시킵니다.

## R2DBC + JOOQ 아키텍처

GrowSnap Backend는 **완전한 Non-blocking Reactive 스택**을 사용합니다:

- **R2DBC (Reactive Relational Database Connectivity)**: 리액티브 데이터베이스 드라이버
- **JOOQ 3.17+ R2DBC 지원**: Type-safe SQL 쿼리 빌더
- **Reactor (Mono/Flux)**: 리액티브 타입으로 비동기 처리
- **ConnectionFactory**: 데이터베이스 커넥션 팩토리 (DataSource 대신 사용)

### R2DBC vs JDBC 차이점

| 구분 | JDBC (Blocking) | R2DBC (Reactive) |
|------|----------------|------------------|
| **연결** | DataSource | ConnectionFactory |
| **반환 타입** | 직접 반환 (User, List\<User\>) | Mono\<User\>, Flux\<User\> |
| **실행 방식** | 동기 블로킹 | 비동기 Non-blocking |
| **JOOQ 래핑** | Mono.fromCallable { ... } | Mono.from(dslContext...) |
| **쿼리 실행** | .fetch(), .execute() | Mono.from() 또는 Flux.from() |

## Repository 구현 (R2DBC + JOOQ)

### 기본 CRUD 패턴

```kotlin
@Repository
class UserRepository(
    private val dsl: DSLContext  // R2DBC 기반 DSLContext
) {

    /**
     * ID로 사용자 조회 (Reactive)
     *
     * @param id 사용자 ID
     * @return Mono<User> - 비동기 결과
     */
    fun findById(id: UUID): Mono<User> {
        return Mono.from(  // ✅ Mono.from()으로 R2DBC 쿼리 래핑
            dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id.toString()))
                .and(USERS.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> mapToUser(record) }
    }

    /**
     * 사용자 저장 (Reactive)
     *
     * @param user 사용자 정보
     * @return Mono<User> - 저장된 사용자 (ID 포함)
     */
    fun save(user: User): Mono<User> {
        val userId = user.id ?: UUID.randomUUID()
        return Mono.from(  // ✅ Mono.from()으로 INSERT 실행
            dsl.insertInto(USERS)
                .set(USERS.ID, userId.toString())
                .set(USERS.EMAIL, user.email)
                .set(USERS.PROVIDER, user.provider.name)
                .set(USERS.PROVIDER_ID, user.providerId)
                .set(USERS.ROLE, user.role.name)
        ).thenReturn(user.copy(id = userId))  // ✅ thenReturn()으로 결과 반환
    }

    /**
     * 사용자 삭제 (Soft Delete, Reactive)
     *
     * @param id 사용자 ID
     * @param deletedBy 삭제를 수행한 사용자 ID
     * @return Mono<Void> - 완료 신호
     */
    fun softDelete(id: UUID, deletedBy: UUID): Mono<Void> {
        return Mono.from(  // ✅ Mono.from()으로 UPDATE 실행
            dsl.update(USERS)
                .set(USERS.DELETED_AT, Instant.now())
                .set(USERS.UPDATED_AT, Instant.now())
                .set(USERS.UPDATED_BY, deletedBy.toString())
                .where(USERS.ID.eq(id.toString()))
                .and(USERS.DELETED_AT.isNull)
        ).then()  // ✅ .then()으로 Mono<Void> 변환
    }

    /**
     * JOOQ Record를 도메인 모델로 변환
     */
    private fun mapToUser(record: UsersRecord): User {
        return User(
            id = UUID.fromString(record.id!!),
            email = record.email!!,
            provider = OAuthProvider.valueOf(record.provider!!),
            providerId = record.providerId!!,
            role = UserRole.valueOf(record.role!!)
        )
    }
}
```

### R2DBC 쿼리 패턴 정리

#### 1. 단일 결과 조회 (Mono)

```kotlin
// ✅ GOOD: Mono.from()으로 단일 레코드 조회
fun findById(id: UUID): Mono<User> {
    return Mono.from(
        dsl.selectFrom(USERS)
            .where(USERS.ID.eq(id.toString()))
    ).map { record -> mapToUser(record) }
}
```

#### 2. 여러 결과 조회 (Flux)

```kotlin
// ✅ GOOD: Flux.from()으로 여러 레코드 조회
fun findAll(): Flux<User> {
    return Flux.from(
        dsl.selectFrom(USERS)
            .where(USERS.DELETED_AT.isNull)
            .orderBy(USERS.CREATED_AT.desc())
    ).map { record -> mapToUser(record) }
}
```

#### 3. INSERT 쿼리

```kotlin
// ✅ GOOD: Mono.from()으로 INSERT 실행
fun save(user: User): Mono<User> {
    val userId = user.id ?: UUID.randomUUID()
    return Mono.from(
        dsl.insertInto(USERS)
            .set(USERS.ID, userId.toString())
            .set(USERS.EMAIL, user.email)
    ).thenReturn(user.copy(id = userId))
}
```

#### 4. UPDATE 쿼리

```kotlin
// ✅ GOOD: Mono.from()으로 UPDATE 실행
fun update(userId: UUID, email: String): Mono<Void> {
    return Mono.from(
        dsl.update(USERS)
            .set(USERS.EMAIL, email)
            .set(USERS.UPDATED_AT, Instant.now())
            .where(USERS.ID.eq(userId.toString()))
    ).then()
}
```

#### 5. 원자적 카운터 증가 (Atomic Increment)

```kotlin
// ✅ GOOD: 원자적으로 카운터 증가
fun incrementViewCount(contentId: UUID): Mono<Void> {
    return Mono.from(
        dsl.update(CONTENT_INTERACTIONS)
            .set(CONTENT_INTERACTIONS.VIEW_COUNT, CONTENT_INTERACTIONS.VIEW_COUNT.plus(1))
            .set(CONTENT_INTERACTIONS.UPDATED_AT, Instant.now())
            .where(CONTENT_INTERACTIONS.CONTENT_ID.eq(contentId.toString()))
            .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)
    ).then()
}
```

## Database Query 작성 규칙 (JOOQ)

### ❌ 잘못된 예시

```kotlin
// ❌ BAD: asterisk 사용
dslContext
    .select(CONTENTS.asterisk())
    .from(CONTENTS)
    .fetch()

// ❌ BAD: 여러 테이블에 asterisk 사용
dslContext
    .select(
        CONTENTS.asterisk(),
        CONTENT_METADATA.asterisk(),
        CONTENT_INTERACTIONS.asterisk()
    )
    .from(CONTENTS)
    .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
    .fetch()
```

**문제점**:
1. **성능 저하**: 불필요한 컬럼까지 모두 조회하여 DB 및 네트워크 부하 증가
2. **대역폭 낭비**: 사용하지 않는 데이터까지 전송
3. **유지보수 어려움**: 어떤 컬럼을 실제로 사용하는지 코드만 보고 파악 불가
4. **스키마 변경에 취약**: 테이블 컬럼 추가/삭제 시 예상치 못한 오류 발생 가능

### ✅ 올바른 예시

```kotlin
// ✅ GOOD: 필요한 컬럼만 명시적으로 선택
dslContext
    .select(
        CONTENTS.ID,
        CONTENTS.CONTENT_TYPE,
        CONTENTS.URL,
        CONTENTS.THUMBNAIL_URL,
        CONTENTS.DURATION,
        CONTENTS.WIDTH,
        CONTENTS.HEIGHT
    )
    .from(CONTENTS)
    .fetch()

// ✅ GOOD: 조인 쿼리에서도 필요한 컬럼만 명시
dslContext
    .select(
        // CONTENTS 필요 컬럼
        CONTENTS.ID,
        CONTENTS.CONTENT_TYPE,
        CONTENTS.URL,
        CONTENTS.THUMBNAIL_URL,
        // CONTENT_METADATA 필요 컬럼
        CONTENT_METADATA.TITLE,
        CONTENT_METADATA.DESCRIPTION,
        CONTENT_METADATA.CATEGORY,
        // CONTENT_INTERACTIONS 필요 컬럼
        CONTENT_INTERACTIONS.LIKE_COUNT,
        CONTENT_INTERACTIONS.VIEW_COUNT
    )
    .from(CONTENTS)
    .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
    .join(CONTENT_INTERACTIONS).on(CONTENT_INTERACTIONS.CONTENT_ID.eq(CONTENTS.ID))
    .fetch()
```

**장점**:
1. ✅ **성능 최적화**: 필요한 데이터만 조회하여 DB 부하 감소
2. ✅ **명확성**: 코드만 보고 어떤 데이터를 사용하는지 즉시 파악 가능
3. ✅ **안정성**: 스키마 변경 시 영향받는 범위를 명확히 알 수 있음
4. ✅ **대역폭 절약**: 네트워크 트래픽 최소화

## Soft Delete 구현 (논리적 삭제)

**중요**: 물리적 삭제 금지, 논리적 삭제만 허용

### 올바른 Soft Delete 패턴

```kotlin
// ✅ GOOD: 조회 시 삭제된 데이터 제외
fun findActiveUsers(): List<User> {
    return dslContext
        .select(USER.ID, USER.EMAIL, USER.NAME)
        .from(USER)
        .where(USER.DELETED_AT.isNull)  // ✅ 삭제된 데이터 제외
        .fetchInto(User::class.java)
}

// ✅ GOOD: 삭제는 UPDATE로 구현
fun deleteUser(userId: UUID, deletedBy: UUID) {
    dslContext
        .update(USER)
        .set(USER.DELETED_AT, Instant.now())
        .set(USER.UPDATED_AT, Instant.now())
        .set(USER.UPDATED_BY, deletedBy)  // 삭제자 기록
        .where(USER.ID.eq(userId))
        .and(USER.DELETED_AT.isNull)  // 이미 삭제된 데이터는 제외
        .execute()
}

// ❌ BAD: 물리적 삭제
fun deleteUser(userId: UUID) {
    dslContext
        .deleteFrom(USER)
        .where(USER.ID.eq(userId))
        .execute()  // ❌ 물리적 삭제 금지!
}
```

## Audit Trail 필드 적용

**모든 엔티티는 5가지 Audit Trail 필드 필수**

### 필수 Audit Trail 필드

1. **createdAt**: `Instant` - 생성 시각
2. **createdBy**: `UUID?` - 생성한 사용자 ID
3. **updatedAt**: `Instant` - 최종 수정 시각
4. **updatedBy**: `UUID?` - 최종 수정한 사용자 ID
5. **deletedAt**: `Instant?` - 삭제 시각 (Soft Delete)

### 생성 (INSERT) 시 Audit Trail 설정

```kotlin
// ✅ GOOD: 생성 시 createdAt, createdBy 설정
fun createUser(userId: UUID, email: String): User {
    return dslContext
        .insertInto(USER)
        .set(USER.ID, UUID.randomUUID())
        .set(USER.EMAIL, email)
        .set(USER.CREATED_AT, Instant.now())
        .set(USER.CREATED_BY, userId)  // 생성자 기록
        .set(USER.UPDATED_AT, Instant.now())
        .set(USER.UPDATED_BY, userId)
        .returning()
        .fetchOne()!!
        .into(User::class.java)
}
```

### 수정 (UPDATE) 시 Audit Trail 갱신

```kotlin
// ✅ GOOD: 수정 시 updatedAt, updatedBy 갱신
fun updateUser(userId: UUID, updatedBy: String, email: String) {
    dslContext
        .update(USER)
        .set(USER.EMAIL, email)
        .set(USER.UPDATED_AT, Instant.now())
        .set(USER.UPDATED_BY, updatedBy)  // 수정자 기록
        .where(USER.ID.eq(userId))
        .and(USER.DELETED_AT.isNull)
        .execute()
}
```

### 삭제 (DELETE) 시 Soft Delete 적용

```kotlin
// ✅ GOOD: 삭제는 UPDATE로 구현 (Soft Delete)
fun deleteUser(userId: UUID, deletedBy: UUID) {
    dslContext
        .update(USER)
        .set(USER.DELETED_AT, Instant.now())
        .set(USER.UPDATED_AT, Instant.now())
        .set(USER.UPDATED_BY, deletedBy)  // 삭제자 기록
        .where(USER.ID.eq(userId))
        .and(USER.DELETED_AT.isNull)  // 이미 삭제된 데이터는 제외
        .execute()
}
```

## 복잡한 조인 쿼리 예시

```kotlin
/**
 * 사용자별 콘텐츠 정보 조회 (조인 쿼리)
 */
fun findContentsByUserId(userId: UUID): List<ContentResponse> {
    return dslContext
        .select(
            // CONTENTS 테이블 필드
            CONTENTS.ID,
            CONTENTS.CONTENT_TYPE,
            CONTENTS.URL,
            CONTENTS.THUMBNAIL_URL,
            CONTENTS.DURATION,
            // CONTENT_METADATA 테이블 필드
            CONTENT_METADATA.TITLE,
            CONTENT_METADATA.DESCRIPTION,
            CONTENT_METADATA.CATEGORY,
            // CONTENT_INTERACTIONS 테이블 필드
            CONTENT_INTERACTIONS.VIEW_COUNT,
            CONTENT_INTERACTIONS.LIKE_COUNT,
            CONTENT_INTERACTIONS.COMMENT_COUNT,
            // USER 테이블 필드
            USER.EMAIL,
            USER.NAME
        )
        .from(CONTENTS)
        .join(CONTENT_METADATA).on(CONTENT_METADATA.CONTENT_ID.eq(CONTENTS.ID))
        .join(CONTENT_INTERACTIONS).on(CONTENT_INTERACTIONS.CONTENT_ID.eq(CONTENTS.ID))
        .join(USER).on(USER.ID.eq(CONTENTS.CREATOR_ID))
        .where(CONTENTS.CREATOR_ID.eq(userId.toString()))
        .and(CONTENTS.DELETED_AT.isNull)              // ✅ Soft Delete 조건
        .and(CONTENT_METADATA.DELETED_AT.isNull)       // ✅ Soft Delete 조건
        .and(CONTENT_INTERACTIONS.DELETED_AT.isNull)   // ✅ Soft Delete 조건
        .fetch()
        .map { record ->
            ContentResponse(
                id = UUID.fromString(record.getValue(CONTENTS.ID)),
                title = record.getValue(CONTENT_METADATA.TITLE),
                viewCount = record.getValue(CONTENT_INTERACTIONS.VIEW_COUNT),
                // ... 기타 필드 매핑
            )
        }
}
```

### 조인 쿼리 작성 팁

- ✅ **주석 추가**: 각 테이블의 컬럼 그룹에 주석을 추가하여 가독성 향상
- ✅ **명시적 컬럼 선택**: 필요한 컬럼만 명시적으로 선택
- ✅ **Soft Delete 조건**: 모든 조인 테이블에 `DELETED_AT IS NULL` 조건 추가
- ✅ **명확한 조인 조건**: `.on()` 절에 명확한 조인 조건 명시

## 페이징 쿼리 예시 (Reactive)

```kotlin
/**
 * 페이지네이션된 비디오 목록 조회 (Reactive)
 *
 * @param page 페이지 번호
 * @param size 페이지 크기
 * @return Flux<Video> - 비동기 비디오 목록
 */
fun findVideosWithPagination(page: Int, size: Int): Flux<Video> {
    return Flux.from(  // ✅ Flux.from()으로 여러 레코드 조회
        dslContext
            .select(
                VIDEO.ID,
                VIDEO.TITLE,
                VIDEO.URL,
                VIDEO.DURATION
            )
            .from(VIDEO)
            .where(VIDEO.DELETED_AT.isNull)
            .orderBy(VIDEO.CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
    ).map { record -> mapToVideo(record) }
}
```

## 집계 쿼리 예시 (Reactive)

```kotlin
/**
 * 사용자별 콘텐츠 개수 조회 (Reactive)
 *
 * @param userId 사용자 ID
 * @return Mono<Int> - 콘텐츠 개수
 */
fun countContentsByUserId(userId: UUID): Mono<Int> {
    return Mono.from(  // ✅ Mono.from()으로 집계 쿼리 실행
        dslContext
            .selectCount()
            .from(CONTENTS)
            .where(CONTENTS.CREATOR_ID.eq(userId.toString()))
            .and(CONTENTS.DELETED_AT.isNull)
    ).map { record -> record.getValue(0, Int::class.java) ?: 0 }
        .defaultIfEmpty(0)
}

/**
 * 카테고리별 콘텐츠 통계 (Reactive)
 *
 * @return Mono<Map<Category, Int>> - 카테고리별 통계
 */
fun getContentStatsByCategory(): Mono<Map<Category, Int>> {
    return Flux.from(  // ✅ Flux.from()으로 여러 집계 결과 조회
        dslContext
            .select(
                CONTENT_METADATA.CATEGORY,
                DSL.count()
            )
            .from(CONTENT_METADATA)
            .join(CONTENTS).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
            .where(CONTENT_METADATA.DELETED_AT.isNull)
            .and(CONTENTS.DELETED_AT.isNull)
            .groupBy(CONTENT_METADATA.CATEGORY)
    ).collectMap(
        { record -> Category.valueOf(record.getValue(CONTENT_METADATA.CATEGORY)!!) },
        { record -> record.getValue(1, Int::class.java)!! }
    )
}
```

## 예외 처리

```kotlin
// 도메인 예외 정의
sealed class VideoException(message: String) : RuntimeException(message) {
    class VideoNotFoundException(id: String) :
        VideoException("Video not found: $id")
    class VideoCreationException(reason: String) :
        VideoException("Video creation failed: $reason")
}

// 전역 예외 핸들러
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(VideoException.VideoNotFoundException::class)
    fun handleVideoNotFound(ex: VideoException.VideoNotFoundException):
        ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse("VIDEO_NOT_FOUND", ex.message))
    }
}
```

## R2DBC 주의사항 및 베스트 프랙티스

### 1. Mono.from() vs Flux.from() 선택

```kotlin
// ✅ GOOD: 단일 결과는 Mono.from()
fun findById(id: UUID): Mono<User> = Mono.from(dsl.selectFrom(USERS)...)

// ✅ GOOD: 여러 결과는 Flux.from()
fun findAll(): Flux<User> = Flux.from(dsl.selectFrom(USERS)...)

// ❌ BAD: 여러 결과를 Mono.from()으로 감싸면 첫 번째 결과만 반환됨
fun findAll(): Mono<User> = Mono.from(dsl.selectFrom(USERS)...)
```

### 2. 결과 변환 연산자

```kotlin
// ✅ GOOD: .map()으로 레코드를 도메인 모델로 변환
Mono.from(query).map { record -> mapToUser(record) }

// ✅ GOOD: .then()으로 Mono<Void> 반환 (INSERT/UPDATE/DELETE)
Mono.from(updateQuery).then()

// ✅ GOOD: .thenReturn()으로 특정 값 반환
Mono.from(insertQuery).thenReturn(savedUser)

// ✅ GOOD: .defaultIfEmpty()로 기본값 설정
Mono.from(query).defaultIfEmpty(0)
```

### 3. Lazy Evaluation (지연 평가)

```kotlin
// ✅ GOOD: Mono/Flux는 구독(subscribe)될 때까지 실행되지 않음
fun findById(id: UUID): Mono<User> {
    return Mono.from(dsl.selectFrom(USERS)...)  // 이 시점에는 쿼리가 실행되지 않음
}

// ❌ BAD: 직접 .block()을 호출하면 동기 블로킹으로 동작
fun findById(id: UUID): User {
    return Mono.from(dsl.selectFrom(USERS)...).block()!!  // Repository에서 사용 금지
}
```

**중요**: Repository 메서드는 항상 `Mono<T>` 또는 `Flux<T>`를 반환해야 합니다. `.block()`은 테스트 코드에서만 사용합니다.

### 4. 트랜잭션 관리

R2DBC에서는 `ReactiveTransactionManager`를 사용합니다:

```kotlin
@Service
class UserService(
    private val userRepository: UserRepository,
    private val transactionalOperator: TransactionalOperator  // Reactive 트랜잭션
) {
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

## Database Query 체크리스트

**코드 작성 전 반드시 확인**:

### 기본 쿼리 규칙
- [ ] **asterisk 사용 금지**: `.select(TABLE.asterisk())` 사용하지 않았는가?
- [ ] **명시적 컬럼 선택**: 실제 사용하는 컬럼만 명시적으로 선택했는가?
- [ ] **Soft Delete 조건**: 조회 쿼리에 `DELETED_AT IS NULL` 조건 포함했는가?
- [ ] **Audit Trail 설정**: 생성/수정/삭제 시 적절한 Audit Trail 필드 설정했는가?
- [ ] **주석 추가**: 조인이 복잡한 경우, 각 테이블의 컬럼 그룹에 주석을 추가했는가?
- [ ] **불필요한 컬럼 제거**: 조회하지만 사용하지 않는 컬럼은 없는가?
- [ ] **인덱스 활용**: WHERE 절에 사용되는 컬럼에 인덱스가 설정되어 있는가?

### R2DBC 리액티브 규칙
- [ ] **Mono.from() 사용**: R2DBC 쿼리를 `Mono.from()` 또는 `Flux.from()`으로 래핑했는가?
- [ ] **반환 타입**: Repository 메서드가 `Mono<T>` 또는 `Flux<T>`를 반환하는가?
- [ ] **block() 금지**: Repository 코드에서 `.block()`을 사용하지 않았는가?
- [ ] **적절한 변환**: `.map()`, `.then()`, `.thenReturn()` 등을 적절히 사용했는가?
- [ ] **defaultIfEmpty**: 결과가 없을 수 있는 경우 `.defaultIfEmpty()`로 기본값 설정했는가?

## 성능 최적화 팁

### 1. 필요한 컬럼만 조회

```kotlin
// ❌ BAD: 모든 컬럼 조회
dslContext.select(USER.asterisk()).from(USER)

// ✅ GOOD: 필요한 컬럼만 조회
dslContext.select(USER.ID, USER.NAME).from(USER)
```

### 2. 페이징 사용

```kotlin
// ✅ GOOD: LIMIT, OFFSET 사용
dslContext
    .select(VIDEO.ID, VIDEO.TITLE)
    .from(VIDEO)
    .limit(20)
    .offset(0)
```

### 3. 인덱스 활용

```sql
-- deleted_at 컬럼에 인덱스 생성
CREATE INDEX idx_deleted_at ON users(deleted_at);

-- 복합 인덱스 생성
CREATE INDEX idx_creator_deleted ON contents(creator_id, deleted_at);
```

### 4. 배치 처리

```kotlin
// ✅ GOOD: 배치 INSERT
dslContext.batch(
    users.map { user ->
        dslContext.insertInto(USER)
            .set(USER.ID, user.id)
            .set(USER.EMAIL, user.email)
    }
).execute()
```

## 정리

1. **asterisk 금지**: SELECT 쿼리에서 `TABLE.asterisk()` 절대 사용 금지
2. **명시적 컬럼 선택**: 필요한 컬럼만 명시적으로 선택
3. **Soft Delete**: 조회 쿼리에 `DELETED_AT IS NULL` 조건 필수
4. **Audit Trail**: 생성/수정/삭제 시 Audit Trail 필드 설정 필수
5. **성능 최적화**: 인덱스 활용, 페이징, 배치 처리
