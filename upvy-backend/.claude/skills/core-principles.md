# Upvy Backend 핵심 원칙

> 모든 개발 작업의 기초가 되는 핵심 원칙입니다.

## 1. TDD 필수

**항상 테스트 코드를 먼저 작성합니다.**

- 순서: Red (실패하는 테스트) → Green (통과하는 최소 코드) → Refactor (리팩토링)
- 모든 public 메서드는 테스트 필수
- **모든 테스트는 시나리오 기반으로 작성**: 테스트만 보고 즉시 기능을 파악할 수 있어야 함
- **Given-When-Then 주석 필수**: 모든 테스트에 `// Given`, `// When`, `// Then` 주석 작성
- **DisplayName 필수**: 테스트 시나리오를 명확히 설명하는 한글 설명
  - 예: "유효한 요청으로 비디오 생성 시, 201과 비디오 정보를 반환한다"

## 2. SOLID 원칙

- **S**ingle Responsibility: 한 클래스는 한 가지 책임만
- **O**pen-Closed: 확장에 열려있고, 수정에 닫혀있게
- **L**iskov Substitution: 인터페이스 구현체는 상호 교체 가능하게
- **I**nterface Segregation: 큰 인터페이스를 작은 것들로 분리
- **D**ependency Inversion: 구현체가 아닌 추상화에 의존

## 3. 문서화 필수

- **모든 public 클래스/함수에 KDoc 작성**
- **모든 API 엔드포인트에 REST Docs 작성**
- AsciiDoc 자동 생성 확인

## 4. 테스트 작성 규칙

- **단위 테스트는 MockK로 모킹 필수**
- **시나리오 기반 테스트**: 테스트 이름만 보고 무엇을 검증하는지 즉시 파악 가능해야 함
- **Given-When-Then 주석 필수**: 모든 테스트에 명시적으로 작성
- **DisplayName 필수**: 한글로 명확한 시나리오 설명
- **테스트 완료 후 빌드/테스트 실행**: 모든 테스트가 통과해야만 작업 완료
- **통합, 단위 테스트 모두 작성할 것 (비중은 단위 테스트: 70%, 통합 테스트: 30%)**
- **사용자 모킹**: 컨트롤러 테스트에서 인증된 사용자 모킹이 필요하면 `mockUser()` helper function 사용

## 5. Git Convention

커밋: /docs/GIT_CONVENTION.md 준수

## 6. 엔티티 Audit Trail 필드 (필수)

**모든 엔티티는 5가지 Audit Trail 필드 필수**

- 물리적 삭제 금지, 논리적 삭제(Soft Delete)만 허용
- 데이터 변경 이력 추적을 위한 감사 필드 필수

### 필수 Audit Trail 필드

모든 엔티티는 다음 5가지 필드를 반드시 포함해야 합니다:

1. **createdAt**: `Instant` - 생성 시각
2. **createdBy**: `UUID?` - 생성한 사용자 ID
3. **updatedAt**: `Instant` - 최종 수정 시각
4. **updatedBy**: `UUID?` - 최종 수정한 사용자 ID
5. **deletedAt**: `Instant?` - 삭제 시각 (Soft Delete)

### Audit Trail 구현 예시

```kotlin
// ✅ GOOD: 완전한 Audit Trail이 적용된 엔티티
data class User(
    val id: UUID,
    val email: String,
    val name: String,
    // Audit Trail 필드 (필수)
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null  // Soft Delete
)

// ✅ GOOD: 조회 시 삭제된 데이터 제외
fun findActiveUsers(): List<User> {
    return dslContext
        .select(USER.ID, USER.EMAIL, USER.NAME)  // 필요한 컬럼만 명시
        .from(USER)
        .where(USER.DELETED_AT.isNull)  // ✅ 삭제된 데이터 제외
        .fetchInto(User::class.java)
}

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

// ✅ GOOD: 수정 시 updatedAt, updatedBy 설정
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

// ❌ BAD: 물리적 삭제
fun deleteUser(userId: UUID) {
    dslContext
        .deleteFrom(USER)
        .where(USER.ID.eq(userId))
        .execute()  // ❌ 물리적 삭제 금지!
}
```

### Audit Trail 체크리스트

- [ ] **모든 엔티티에 5가지 필드 존재**: `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, `deletedAt`
- [ ] **조회 쿼리에 `deletedAt IS NULL` 조건 포함**
- [ ] **생성 시 `createdAt`, `createdBy` 설정**
- [ ] **수정 시 `updatedAt`, `updatedBy` 갱신**
- [ ] **삭제는 UPDATE로 구현**: `deletedAt`, `updatedAt`, `updatedBy` 설정
- [ ] **삭제된 데이터는 복구 가능하도록 보관**
- [ ] **데이터베이스 스키마에 모든 필드 존재 및 인덱스 설정**

### 데이터베이스 스키마 예시

```sql
CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    -- Audit Trail 필드
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by CHAR(36) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by CHAR(36) NULL,
    deleted_at TIMESTAMP NULL
);

-- 성능을 위한 인덱스
CREATE INDEX idx_deleted_at ON users(deleted_at);
```

## 개발 프로세스 (항상 이 순서로)

```
1. 테스트 코드 작성 (Controller + Service)
   ↓
2. 테스트 통과하는 최소 코드 작성 (SOLID 원칙 준수)
   ↓
3. 리팩토링 (SOLID 원칙 적용)
   ↓
4. KDoc + REST Docs + Asciidoc 작성
   ↓
5. 빌드 및 테스트 (모두 정상이여야함, 일부 실패 용인하지 않음)
   ↓
6. 커밋 (feat(scope): message)
```

## SOLID 원칙 적용 예시

### Single Responsibility (단일 책임)

```kotlin
// ❌ BAD: 여러 책임
class UserService {
    fun createUser() { }
    fun sendEmail() { }  // 이메일 발송은 별도 서비스
    fun uploadImage() { }  // 이미지 업로드는 별도 서비스
}

// ✅ GOOD: 단일 책임
class UserService {
    fun createUser() { }
}
class EmailService {
    fun sendEmail() { }
}
class ImageService {
    fun uploadImage() { }
}
```

### Open-Closed (개방-폐쇄)

```kotlin
// ✅ GOOD: 확장에 열려있음
interface NotificationService {
    fun send(message: String)
}

class EmailNotification : NotificationService
class SmsNotification : NotificationService
class PushNotification : NotificationService  // 새로운 구현 추가 가능
```

### Dependency Inversion (의존성 역전)

```kotlin
// ❌ BAD: 구현체에 의존
class UserService(private val mySqlRepository: MySqlUserRepository)

// ✅ GOOD: 인터페이스에 의존
class UserService(private val userRepository: UserRepository)
```

## 핵심 체크리스트

**모든 작업 시작 전 반드시 확인**:

- [ ] **TDD**: 테스트를 먼저 작성했는가?
- [ ] **SOLID**: 단일 책임 원칙을 지켰는가?
- [ ] **문서화**: KDoc과 REST Docs, Asciidoc을 작성했는가?
- [ ] **Audit Trail**: 모든 엔티티에 5가지 필드가 있는가?
- [ ] **Soft Delete**: 물리적 삭제를 사용하지 않았는가?
- [ ] **빌드/테스트**: 모든 테스트 및 빌드가 통과하는가?
