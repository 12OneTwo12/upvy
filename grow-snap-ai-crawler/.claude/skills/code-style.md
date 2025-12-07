# 코드 스타일 규칙

> 로깅, 네이밍, Kotlin 특성 활용 규칙을 정의합니다.

## 로깅 및 출력 규칙

**절대 준수**: 다음 규칙은 예외 없이 반드시 지켜야 합니다.

### 1. println 사용 금지

**절대 사용 금지**: `println()`, `print()`, `System.out.println()` 등 모든 콘솔 출력

```kotlin
// BAD: println 사용
fun startBatch() {
    println("Batch started")  // 절대 금지!
}

// GOOD: Logger 사용
@Component
class BatchScheduler {
    companion object {
        private val logger = LoggerFactory.getLogger(BatchScheduler::class.java)
    }

    fun startBatch() {
        logger.info("Batch started")
    }
}
```

**이유**:
- println은 로그 레벨 제어 불가
- 프로덕션 환경에서 로그 추적 불가능
- 로그 파일로 저장되지 않음

### 2. 이모티콘 사용 금지

**절대 사용 금지**: 코드, 주석, 로그 메시지에 이모티콘 사용 금지

```kotlin
// BAD: 이모티콘 사용
logger.info("Batch completed successfully")

// GOOD: 텍스트만 사용
logger.info("Batch completed successfully")
```

**허용**: 문서 파일 (README.md 등)에서만 사용 가능

### 3. FQCN 사용 금지

**절대 사용 금지**: Fully Qualified Class Name (FQCN) 사용 금지

```kotlin
// BAD: FQCN 사용
val status = org.springframework.batch.core.BatchStatus.COMPLETED

// GOOD: import 사용
import org.springframework.batch.core.BatchStatus
val status = BatchStatus.COMPLETED
```

### 로깅 체크리스트

- [ ] **println 사용 금지**: `println`, `print` 사용하지 않았는가?
- [ ] **Logger 사용**: SLF4J Logger를 사용했는가?
- [ ] **이모티콘 제거**: 코드, 주석, 로그에 이모티콘이 없는가?
- [ ] **FQCN 사용 금지**: import 문을 사용했는가?

## 네이밍 규칙

```kotlin
// 클래스: PascalCase
class CrawlStep
class VideoDownloadProcessor

// 함수/변수: camelCase
fun processVideo(videoId: String)
val transcriptText = "..."

// 상수: UPPER_SNAKE_CASE
const val MAX_RETRY_COUNT = 3
const val CHUNK_SIZE = 5

// 패키지: lowercase
package me.onetwo.growsnap.crawler.pipeline
package me.onetwo.growsnap.crawler.client
```

### Step/Processor/Client 네이밍

| 유형 | 네이밍 패턴 | 예시 |
|------|-----------|------|
| Step | `{Action}Step` | `CrawlStep`, `TranscribeStep` |
| ItemProcessor | `{Action}Processor` | `VideoDownloadProcessor` |
| ItemReader | `{Source}{Type}Reader` | `YouTubeCcVideoReader` |
| ItemWriter | `{Target}Writer` | `AiContentJobWriter` |
| AI Client | `{Provider}{Type}Client` | `VertexAiLlmClient`, `GoogleSttClient` |
| Service | `{Domain}Service` | `YouTubeService`, `S3Service` |

## Kotlin 특성 활용

### data class 활용

```kotlin
// GOOD: data class 사용
data class VideoCandidate(
    val videoId: String,
    val title: String,
    val channelId: String
)

data class Segment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String
)
```

### null 안전성

```kotlin
// GOOD: null 안전성 활용
val title = job.generatedTitle ?: "Untitled"
val description = job.transcript?.take(200) ?: ""

// GOOD: let 사용
job.transcript?.let { transcript ->
    llmClient.extractKeySegments(transcript)
}
```

### 확장 함수

```kotlin
// GOOD: 확장 함수로 변환 로직 분리
fun AiContentJob.toResponse(): AiContentJobResponse {
    return AiContentJobResponse(
        id = this.id,
        youtubeVideoId = this.youtubeVideoId,
        status = this.status.name,
        qualityScore = this.qualityScore
    )
}

fun VideoCandidate.toJob(): AiContentJob {
    return AiContentJob(
        youtubeVideoId = this.videoId,
        youtubeTitle = this.title,
        status = JobStatus.PENDING
    )
}
```

### when 표현식

```kotlin
// GOOD: when 사용
val nextStep = when (job.status) {
    JobStatus.PENDING -> "crawl"
    JobStatus.CRAWLED -> "transcribe"
    JobStatus.TRANSCRIBED -> "analyze"
    JobStatus.ANALYZED -> "edit"
    JobStatus.EDITED -> "review"
    else -> "complete"
}
```

### scope functions 활용

```kotlin
// GOOD: apply 사용
val job = AiContentJob(videoId = "abc123").apply {
    this.status = JobStatus.CRAWLED
    this.rawVideoS3Key = s3Key
    this.createdAt = Instant.now()
}

// GOOD: also 사용 (로깅)
val result = processor.process(candidate).also {
    logger.info("Processing completed: jobId=${it?.id}")
}

// GOOD: let 사용 (null 처리)
job.transcript?.let { transcript ->
    llmClient.analyze(transcript)
}
```

## 로깅 패턴

### 로그 레벨 사용

```kotlin
// DEBUG: 개발/디버깅 정보
logger.debug("Processing video: videoId={}", videoId)

// INFO: 일반적인 실행 정보
logger.info("Batch job started: jobId={}", jobId)
logger.info("Step completed: readCount={}, writeCount={}", readCount, writeCount)

// WARN: 경고 (복구 가능한 문제)
logger.warn("Retry attempt {} of {} for videoId={}", attempt, maxRetry, videoId)

// ERROR: 에러 (예외 발생)
logger.error("Failed to download video: videoId={}", videoId, exception)
```

### 구조화된 로깅

```kotlin
// GOOD: 파라미터 바인딩 사용
logger.info("Video downloaded: videoId={}, size={}", videoId, fileSize)

// BAD: 문자열 연결
logger.info("Video downloaded: videoId=" + videoId + ", size=" + fileSize)
```

## 코루틴 사용

### runBlocking 사용 (Spring Batch 컨텍스트)

```kotlin
// Spring Batch에서 suspend 함수 호출
override fun process(job: AiContentJob): AiContentJob {
    val result = runBlocking {
        llmClient.analyze(job.transcript!!)
    }
    return job.copy(analysisResult = result)
}
```

### suspend 함수 정의

```kotlin
// AI 클라이언트는 suspend 함수로 정의
interface LlmClient {
    suspend fun analyze(prompt: String): String
    suspend fun extractKeySegments(transcript: String): List<Segment>
}
```

## 체크리스트

### 코드 작성 전 확인

- [ ] **println 금지**: SLF4J Logger 사용
- [ ] **이모티콘 금지**: 코드, 주석, 로그에 없음
- [ ] **FQCN 금지**: import 문 사용
- [ ] **네이밍 규칙**: PascalCase, camelCase, UPPER_SNAKE_CASE 적절히 사용
- [ ] **Kotlin 특성**: data class, null safety, 확장 함수 활용
- [ ] **로그 레벨**: 적절한 레벨 사용 (debug, info, warn, error)
