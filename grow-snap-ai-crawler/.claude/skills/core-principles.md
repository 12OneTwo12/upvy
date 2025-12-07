# GrowSnap AI Crawler 핵심 원칙

> 모든 개발 작업의 기초가 되는 핵심 원칙입니다.

## 1. TDD 필수

**항상 테스트 코드를 먼저 작성합니다.**

- 순서: Red (실패하는 테스트) -> Green (통과하는 최소 코드) -> Refactor (리팩토링)
- 모든 public 메서드는 테스트 필수
- **모든 테스트는 시나리오 기반으로 작성**: 테스트만 보고 즉시 기능을 파악할 수 있어야 함
- **Given-When-Then 주석 필수**: 모든 테스트에 `// Given`, `// When`, `// Then` 주석 작성
- **DisplayName 필수**: 테스트 시나리오를 명확히 설명하는 한글 설명

## 2. SOLID 원칙

- **S**ingle Responsibility: 한 클래스는 한 가지 책임만
- **O**pen-Closed: 확장에 열려있고, 수정에 닫혀있게
- **L**iskov Substitution: 인터페이스 구현체는 상호 교체 가능하게
- **I**nterface Segregation: 큰 인터페이스를 작은 것들로 분리
- **D**ependency Inversion: 구현체가 아닌 추상화에 의존

### AI 클라이언트 예시

```kotlin
// GOOD: 인터페이스에 의존 (Dependency Inversion)
class AnalyzeStep(
    private val llmClient: LlmClient,  // 인터페이스
    private val sttClient: SttClient   // 인터페이스
)

// BAD: 구현체에 의존
class AnalyzeStep(
    private val openAiClient: OpenAiLlmClient  // 구현체 직접 의존
)
```

## 3. 문서화 필수

- **모든 public 클래스/함수에 KDoc 작성**
- 복잡한 비즈니스 로직에 주석 추가

## 4. 테스트 작성 규칙

- **단위 테스트는 MockK로 모킹 필수**
- **시나리오 기반 테스트**: 테스트 이름만 보고 무엇을 검증하는지 즉시 파악 가능해야 함
- **Given-When-Then 주석 필수**: 모든 테스트에 명시적으로 작성
- **DisplayName 필수**: 한글로 명확한 시나리오 설명
- **테스트 완료 후 빌드/테스트 실행**: 모든 테스트가 통과해야만 작업 완료
- **통합, 단위 테스트 모두 작성할 것 (비중은 단위 테스트: 70%, 통합 테스트: 30%)**

## 5. Git Convention

커밋: Conventional Commits 준수
- `feat(scope): subject`
- `fix(scope): subject`
- `refactor(scope): subject`
- `test(scope): subject`

## 6. 엔티티 Audit Trail 필드 (필수)

**모든 엔티티는 5가지 Audit Trail 필드 필수**

- 물리적 삭제 금지, 논리적 삭제(Soft Delete)만 허용
- 데이터 변경 이력 추적을 위한 감사 필드 필수

### 필수 Audit Trail 필드

1. **createdAt**: `Instant` - 생성 시각
2. **createdBy**: `String?` - 생성한 주체 (시스템 또는 사용자 ID)
3. **updatedAt**: `Instant` - 최종 수정 시각
4. **updatedBy**: `String?` - 최종 수정한 주체
5. **deletedAt**: `Instant?` - 삭제 시각 (Soft Delete)

### AI Content Job 엔티티 예시

```kotlin
data class AiContentJob(
    val id: Long,
    val youtubeVideoId: String,
    val status: JobStatus,
    val qualityScore: Int?,

    // Audit Trail 필드 (필수)
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = "SYSTEM",
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = "SYSTEM",
    val deletedAt: Instant? = null  // Soft Delete
)
```

## 7. 개발 프로세스 (항상 이 순서로)

```
1. 테스트 코드 작성 (Step + Service)
   |
   v
2. 테스트 통과하는 최소 코드 작성 (SOLID 원칙 준수)
   |
   v
3. 리팩토링 (SOLID 원칙 적용)
   |
   v
4. KDoc 작성
   |
   v
5. 빌드 및 테스트 (모두 정상이여야함, 일부 실패 용인하지 않음)
   |
   v
6. 커밋 (feat(scope): message)
```

## 8. SOLID 원칙 적용 예시

### Single Responsibility (단일 책임)

```kotlin
// BAD: 여러 책임
class AiContentProcessor {
    fun downloadVideo() { }
    fun transcribe() { }
    fun analyze() { }
    fun editVideo() { }
}

// GOOD: 단일 책임
class CrawlStep { fun execute() { } }
class TranscribeStep { fun execute() { } }
class AnalyzeStep { fun execute() { } }
class EditStep { fun execute() { } }
```

### Open-Closed (개방-폐쇄)

```kotlin
// GOOD: 확장에 열려있음
interface LlmClient {
    suspend fun analyze(prompt: String): String
}

class VertexAiLlmClient : LlmClient
class OpenAiLlmClient : LlmClient
class ClaudeLlmClient : LlmClient  // 새로운 구현 추가 가능
```

### Dependency Inversion (의존성 역전)

```kotlin
// BAD: 구현체에 의존
class AnalyzeStep(private val vertexAiClient: VertexAiLlmClient)

// GOOD: 인터페이스에 의존
class AnalyzeStep(private val llmClient: LlmClient)
```

## 핵심 체크리스트

**모든 작업 시작 전 반드시 확인**:

- [ ] **TDD**: 테스트를 먼저 작성했는가?
- [ ] **SOLID**: 단일 책임 원칙을 지켰는가?
- [ ] **인터페이스 의존**: AI 클라이언트는 인터페이스에 의존하는가?
- [ ] **문서화**: KDoc을 작성했는가?
- [ ] **Audit Trail**: 모든 엔티티에 5가지 필드가 있는가?
- [ ] **Soft Delete**: 물리적 삭제를 사용하지 않았는가?
- [ ] **빌드/테스트**: 모든 테스트 및 빌드가 통과하는가?
