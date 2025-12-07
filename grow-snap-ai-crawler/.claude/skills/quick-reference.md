# 빠른 참조 가이드

> 모든 개발 작업 시 빠르게 참조할 수 있는 체크리스트와 핵심 규칙 요약입니다.

## Claude가 반드시 지킬 것 (15가지 핵심 규칙)

1. **TDD**: 테스트 -> 구현 -> 리팩토링 (시나리오 기반, Given-When-Then 주석 필수)
2. **테스트 검증**: 구현 후 반드시 빌드/테스트 실행, 통과해야만 완료
3. **SOLID**: 단일 책임, 인터페이스 분리, 의존성 역전
4. **AI 추상화**: AI 클라이언트는 인터페이스에 의존 (LlmClient, SttClient)
5. **KDoc**: 모든 public 함수/클래스
6. **DisplayName**: 시나리오를 명확히 설명하는 한글 설명
7. **MockK**: 단위 테스트 모킹
8. **Git Convention**: `feat(scope): subject`
9. **Spring Batch 패턴**: Job -> Step -> Reader/Processor/Writer
10. **Audit Trail**: 모든 엔티티에 5가지 필드 필수 (createdAt, createdBy, updatedAt, updatedBy, deletedAt)
11. **Soft Delete**: 물리적 삭제 금지
12. **로깅 규칙**: println 절대 금지, SLF4J Logger 필수 사용
13. **이모티콘 금지**: 코드, 주석, 로그에 이모티콘 절대 사용 금지
14. **FQCN 금지**: Fully Qualified Class Name 사용 금지, 반드시 import 문 사용
15. **Thread.sleep() 절대 금지**: 테스트에서 Awaitility 사용

## 개발 프로세스 (항상 이 순서로)

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

## 금지 사항 (절대 준수)

### 1. println 사용 금지

```kotlin
// BAD
println("Batch started")

// GOOD
logger.info("Batch started")
```

### 2. 이모티콘 사용 금지

```kotlin
// BAD
logger.info("Success")

// GOOD
logger.info("Success")
```

### 3. FQCN 사용 금지

```kotlin
// BAD
org.springframework.batch.core.BatchStatus.COMPLETED

// GOOD
import org.springframework.batch.core.BatchStatus
BatchStatus.COMPLETED
```

### 4. 물리적 삭제 금지

```kotlin
// BAD
repository.delete(entity)

// GOOD
entity.copy(deletedAt = Instant.now())
repository.save(entity)
```

### 5. 구현체 직접 의존 금지

```kotlin
// BAD
class AnalyzeProcessor(
    private val vertexAiClient: VertexAiLlmClient  // 구현체
)

// GOOD
class AnalyzeProcessor(
    private val llmClient: LlmClient  // 인터페이스
)
```

## 필수 패턴

### 1. Audit Trail (5가지 필드)

```kotlin
data class AiContentJob(
    val id: Long,
    val youtubeVideoId: String,
    val status: JobStatus,
    // Audit Trail 필드 (필수)
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = "SYSTEM",
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = "SYSTEM",
    val deletedAt: Instant? = null  // Soft Delete
)
```

### 2. Spring Batch Step 구성

```kotlin
@Bean
fun crawlStep(): Step {
    return StepBuilder("crawlStep", jobRepository)
        .chunk<VideoCandidate, AiContentJob>(5, transactionManager)
        .reader(ccVideoReader())
        .processor(videoDownloadProcessor())
        .writer(jobWriter())
        .faultTolerant()
        .retry(IOException::class.java)
        .retryLimit(3)
        .build()
}
```

### 3. AI 클라이언트 사용

```kotlin
// AI 클라이언트는 인터페이스만 의존
class AnalyzeProcessor(
    private val llmClient: LlmClient
) : ItemProcessor<AiContentJob, AiContentJob> {

    override fun process(job: AiContentJob): AiContentJob {
        val result = runBlocking {
            llmClient.analyze(job.transcript!!)
        }
        return job.copy(analysisResult = result)
    }
}
```

### 4. 로깅 패턴

```kotlin
companion object {
    private val logger = LoggerFactory.getLogger(CrawlStep::class.java)
}

fun process() {
    logger.info("Processing started: videoId={}", videoId)
    logger.debug("Details: {}", details)
    logger.warn("Retry attempt: {}", attempt)
    logger.error("Failed to process", exception)
}
```

## 테스트 작성 필수 계층

**모든 기능 구현 시 필수**:

1. **Step 테스트** - ItemReader/Processor/Writer 테스트
2. **Service 테스트** - 비즈니스 로직, 예외 처리
3. **AI 클라이언트 테스트** - Mock 클라이언트 사용
4. **Job 통합 테스트** - 전체 배치 플로우

## 네이밍 규칙

| 유형 | 패턴 | 예시 |
|------|------|------|
| Step | `{Action}Step` | `CrawlStep`, `TranscribeStep` |
| Processor | `{Action}Processor` | `VideoDownloadProcessor` |
| Reader | `{Source}Reader` | `YouTubeCcVideoReader` |
| Writer | `{Target}Writer` | `AiContentJobWriter` |
| AI Client | `{Provider}{Type}Client` | `VertexAiLlmClient` |
| Service | `{Domain}Service` | `YouTubeService` |

## 코드 리뷰 체크리스트

**PR 전에 반드시 확인**:

- [ ] **TDD**: 테스트 코드를 먼저 작성했는가?
- [ ] **테스트 통과**: 모든 테스트가 통과하는가?
- [ ] **빌드 성공**: ./gradlew build가 성공하는가?
- [ ] **SOLID 원칙**: 단일 책임, 의존성 역전 원칙을 지켰는가?
- [ ] **AI 추상화**: 인터페이스에 의존하는가?
- [ ] **KDoc**: 모든 public 함수/클래스에 KDoc이 있는가?
- [ ] **Audit Trail**: 모든 엔티티에 5가지 필드가 있는가?
- [ ] **Soft Delete**: 물리적 삭제를 사용하지 않았는가?
- [ ] **println 금지**: SLF4J Logger를 사용했는가?
- [ ] **이모티콘 금지**: 코드, 주석, 로그에 이모티콘이 없는가?

## 정리

**모든 개발 작업 시 이 파일을 먼저 읽고 체크리스트를 확인하세요!**

- **개발 시작 전**: 15가지 핵심 규칙 확인
- **코드 작성 중**: 금지 사항, 필수 패턴 확인
- **PR 전**: 코드 리뷰 체크리스트 확인
