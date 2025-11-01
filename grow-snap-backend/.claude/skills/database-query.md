# GrowSnap Backend 데이터베이스 쿼리 규칙

> JOOQ 쿼리 작성 규칙, SELECT asterisk 금지, Soft Delete, Audit Trail 적용을 정의합니다.

## 중요 원칙

**⚠️ SELECT 쿼리에서 asterisk (*) 사용 절대 금지**

필요한 컬럼만 명시적으로 선택하여 성능을 최적화하고 코드 명확성을 향상시킵니다.

## Repository 구현 (JOOQ)

```kotlin
@Repository
class VideoRepositoryImpl(
    private val dslContext: DSLContext
) : VideoRepository {

    override fun findById(id: String): Mono<Video> {
        return Mono.fromCallable {
            dslContext
                .select(
                    VIDEO.ID,
                    VIDEO.TITLE,
                    VIDEO.URL,
                    VIDEO.DURATION
                )  // ✅ 필요한 컬럼만 명시적으로 선택
                .from(VIDEO)
                .where(VIDEO.ID.eq(id))
                .fetchOneInto(Video::class.java)
        }
    }

    override fun save(video: Video): Mono<Video> {
        return Mono.fromCallable {
            dslContext.insertInto(VIDEO)
                .set(VIDEO.ID, video.id)
                .set(VIDEO.TITLE, video.title)
                .execute()
            video
        }
    }
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
        .set(USER.DELETED_AT, LocalDateTime.now())
        .set(USER.UPDATED_AT, LocalDateTime.now())
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

1. **createdAt**: `LocalDateTime` - 생성 시각
2. **createdBy**: `UUID?` - 생성한 사용자 ID
3. **updatedAt**: `LocalDateTime` - 최종 수정 시각
4. **updatedBy**: `UUID?` - 최종 수정한 사용자 ID
5. **deletedAt**: `LocalDateTime?` - 삭제 시각 (Soft Delete)

### 생성 (INSERT) 시 Audit Trail 설정

```kotlin
// ✅ GOOD: 생성 시 createdAt, createdBy 설정
fun createUser(userId: UUID, email: String): User {
    return dslContext
        .insertInto(USER)
        .set(USER.ID, UUID.randomUUID())
        .set(USER.EMAIL, email)
        .set(USER.CREATED_AT, LocalDateTime.now())
        .set(USER.CREATED_BY, userId)  // 생성자 기록
        .set(USER.UPDATED_AT, LocalDateTime.now())
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
        .set(USER.UPDATED_AT, LocalDateTime.now())
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
        .set(USER.DELETED_AT, LocalDateTime.now())
        .set(USER.UPDATED_AT, LocalDateTime.now())
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

## 페이징 쿼리 예시

```kotlin
/**
 * 페이지네이션된 비디오 목록 조회
 */
fun findVideosWithPagination(page: Int, size: Int): List<Video> {
    return dslContext
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
        .fetchInto(Video::class.java)
}
```

## 집계 쿼리 예시

```kotlin
/**
 * 사용자별 콘텐츠 개수 조회
 */
fun countContentsByUserId(userId: UUID): Int {
    return dslContext
        .selectCount()
        .from(CONTENTS)
        .where(CONTENTS.CREATOR_ID.eq(userId.toString()))
        .and(CONTENTS.DELETED_AT.isNull)
        .fetchOne(0, Int::class.java) ?: 0
}

/**
 * 카테고리별 콘텐츠 통계
 */
fun getContentStatsByCategory(): Map<Category, Int> {
    return dslContext
        .select(
            CONTENT_METADATA.CATEGORY,
            DSL.count()
        )
        .from(CONTENT_METADATA)
        .join(CONTENTS).on(CONTENTS.ID.eq(CONTENT_METADATA.CONTENT_ID))
        .where(CONTENT_METADATA.DELETED_AT.isNull)
        .and(CONTENTS.DELETED_AT.isNull)
        .groupBy(CONTENT_METADATA.CATEGORY)
        .fetch()
        .associate { record ->
            Category.valueOf(record.getValue(CONTENT_METADATA.CATEGORY)!!) to
                record.getValue(1, Int::class.java)!!
        }
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

## Database Query 체크리스트

**코드 작성 전 반드시 확인**:

- [ ] **asterisk 사용 금지**: `.select(TABLE.asterisk())` 사용하지 않았는가?
- [ ] **명시적 컬럼 선택**: 실제 사용하는 컬럼만 명시적으로 선택했는가?
- [ ] **Soft Delete 조건**: 조회 쿼리에 `DELETED_AT IS NULL` 조건 포함했는가?
- [ ] **Audit Trail 설정**: 생성/수정/삭제 시 적절한 Audit Trail 필드 설정했는가?
- [ ] **주석 추가**: 조인이 복잡한 경우, 각 테이블의 컬럼 그룹에 주석을 추가했는가?
- [ ] **불필요한 컬럼 제거**: 조회하지만 사용하지 않는 컬럼은 없는가?
- [ ] **인덱스 활용**: WHERE 절에 사용되는 컬럼에 인덱스가 설정되어 있는가?

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
