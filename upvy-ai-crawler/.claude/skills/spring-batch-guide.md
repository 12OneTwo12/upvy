# Spring Batch 개발 가이드

> Spring Batch 5.x 기반 배치 Job/Step 개발 규칙을 정의합니다.

## Spring Batch 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                         Job                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐ │
│  │  Step 1  │──>│  Step 2  │──>│  Step 3  │──>│  Step 4  │ │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘ │
│       │              │              │              │        │
│       v              v              v              v        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │              Chunk Processing                       │   │
│  │  ItemReader -> ItemProcessor -> ItemWriter          │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 1. Job 구성

### Job 정의 템플릿

```kotlin
@Configuration
class AiContentBatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager
) {

    @Bean
    fun aiContentJob(
        crawlStep: Step,
        transcribeStep: Step,
        analyzeStep: Step,
        editStep: Step,
        reviewStep: Step
    ): Job {
        return JobBuilder("aiContentJob", jobRepository)
            .start(crawlStep)
            .next(transcribeStep)
            .next(analyzeStep)
            .next(editStep)
            .next(reviewStep)
            .build()
    }
}
```

### Job 네이밍 규칙

- **Job 이름**: `{domain}Job` (예: `aiContentJob`)
- **Step 이름**: `{action}Step` (예: `crawlStep`, `transcribeStep`)

## 2. Step 구성

### Chunk 기반 Step 템플릿

```kotlin
@Configuration
class CrawlStepConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val youTubeService: YouTubeService
) {

    @Bean
    fun crawlStep(): Step {
        return StepBuilder("crawlStep", jobRepository)
            .chunk<VideoCandidate, AiContentJob>(5, transactionManager)  // chunk size 5
            .reader(ccVideoReader())
            .processor(videoDownloadProcessor())
            .writer(jobWriter())
            .faultTolerant()
            .retry(IOException::class.java)
            .retryLimit(3)
            .skip(YouTubeQuotaExceededException::class.java)
            .skipLimit(100)
            .listener(stepExecutionListener())
            .build()
    }
}
```

### Tasklet 기반 Step 템플릿

```kotlin
@Bean
fun cleanupStep(): Step {
    return StepBuilder("cleanupStep", jobRepository)
        .tasklet({ contribution, chunkContext ->
            // 임시 파일 정리 로직
            tempFileService.cleanupOldFiles()
            RepeatStatus.FINISHED
        }, transactionManager)
        .build()
}
```

## 3. ItemReader / ItemProcessor / ItemWriter

### ItemReader 템플릿

```kotlin
/**
 * YouTube CC 라이선스 비디오 조회 Reader
 */
@Component
@StepScope
class YouTubeCcVideoReader(
    private val youTubeService: YouTubeService
) : ItemReader<VideoCandidate> {

    private val logger = LoggerFactory.getLogger(javaClass)
    private var candidates: Iterator<VideoCandidate>? = null

    override fun read(): VideoCandidate? {
        if (candidates == null) {
            logger.info("YouTube CC 비디오 목록 조회 시작")
            val list = youTubeService.searchCcVideos(
                category = "education",
                maxResults = 50
            )
            candidates = list.iterator()
            logger.info("조회된 비디오 수: ${list.size}")
        }

        return if (candidates!!.hasNext()) {
            candidates!!.next()
        } else {
            null  // null 반환 시 읽기 종료
        }
    }
}
```

### ItemProcessor 템플릿

```kotlin
/**
 * 비디오 다운로드 Processor
 */
@Component
class VideoDownloadProcessor(
    private val ytDlpRunner: YtDlpRunner,
    private val s3Service: S3Service
) : ItemProcessor<VideoCandidate, AiContentJob> {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun process(candidate: VideoCandidate): AiContentJob? {
        logger.info("비디오 다운로드 시작: videoId=${candidate.videoId}")

        try {
            // 1. yt-dlp로 다운로드
            val localPath = ytDlpRunner.download(candidate.videoId)

            // 2. S3 업로드
            val s3Key = "raw/${candidate.videoId}.mp4"
            s3Service.upload(localPath, s3Key)

            // 3. 로컬 파일 삭제
            File(localPath).delete()

            // 4. Job 엔티티 생성
            return AiContentJob(
                youtubeVideoId = candidate.videoId,
                youtubeTitle = candidate.title,
                status = JobStatus.CRAWLED,
                rawVideoS3Key = s3Key,
                createdAt = Instant.now(),
                createdBy = "SYSTEM"
            )
        } catch (e: Exception) {
            logger.error("비디오 다운로드 실패: videoId=${candidate.videoId}", e)
            return null  // null 반환 시 해당 아이템 스킵
        }
    }
}
```

### ItemWriter 템플릿

```kotlin
/**
 * AiContentJob 저장 Writer
 */
@Component
class AiContentJobWriter(
    private val jobRepository: AiContentJobRepository
) : ItemWriter<AiContentJob> {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun write(chunk: Chunk<out AiContentJob>) {
        logger.info("Job 저장 시작: count=${chunk.size()}")

        chunk.items.forEach { job ->
            jobRepository.save(job)
            logger.debug("Job 저장 완료: jobId=${job.id}")
        }

        logger.info("Job 저장 완료: count=${chunk.size()}")
    }
}
```

## 4. 재시도 및 스킵 전략

### Fault Tolerant 설정

```kotlin
.faultTolerant()
// 재시도 설정
.retry(IOException::class.java)           // 네트워크 오류 재시도
.retry(OpenAiRateLimitException::class.java)  // API Rate Limit 재시도
.retryLimit(3)                            // 최대 3회 재시도
.backOffPolicy(ExponentialBackOffPolicy().apply {
    initialInterval = 1000L   // 1초
    maxInterval = 30000L      // 최대 30초
    multiplier = 2.0          // 2배씩 증가
})

// 스킵 설정
.skip(YouTubeQuotaExceededException::class.java)  // 할당량 초과 시 스킵
.skip(InvalidVideoException::class.java)          // 잘못된 비디오 스킵
.skipLimit(100)                                   // 최대 100개 스킵 허용
```

### 커스텀 RetryPolicy

```kotlin
@Component
class AiApiRetryPolicy : RetryPolicy {

    override fun canRetry(context: RetryContext): Boolean {
        val throwable = context.lastThrowable ?: return true

        return when (throwable) {
            is OpenAiRateLimitException -> true   // Rate Limit은 재시도
            is OpenAiApiException -> false        // 일반 API 오류는 재시도 안 함
            is IOException -> true                // 네트워크 오류 재시도
            else -> false
        }
    }
}
```

## 5. Step Listener

### StepExecutionListener 템플릿

```kotlin
@Component
class CrawlStepListener : StepExecutionListener {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun beforeStep(stepExecution: StepExecution) {
        logger.info("=== Crawl Step 시작 ===")
        logger.info("Job ID: ${stepExecution.jobExecution.jobId}")
    }

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        logger.info("=== Crawl Step 완료 ===")
        logger.info("Read Count: ${stepExecution.readCount}")
        logger.info("Write Count: ${stepExecution.writeCount}")
        logger.info("Skip Count: ${stepExecution.skipCount}")
        logger.info("Status: ${stepExecution.status}")

        return stepExecution.exitStatus
    }
}
```

## 6. 스케줄링

### @Scheduled 사용

```kotlin
@Component
class AiContentScheduler(
    private val jobLauncher: JobLauncher,
    private val aiContentJob: Job
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 매일 새벽 3시에 AI 콘텐츠 배치 실행
     */
    @Scheduled(cron = "0 0 1 * * *")
    fun runDailyBatch() {
        logger.info("AI 콘텐츠 배치 작업 시작")

        val params = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        try {
            val execution = jobLauncher.run(aiContentJob, params)
            logger.info("배치 완료: status=${execution.status}")
        } catch (e: Exception) {
            logger.error("배치 실행 실패", e)
        }
    }
}
```

## 7. Step 간 데이터 전달

### ExecutionContext 사용

```kotlin
// Step 1에서 데이터 저장
override fun afterStep(stepExecution: StepExecution): ExitStatus {
    stepExecution.jobExecution.executionContext
        .put("processedJobIds", processedJobIds)
    return stepExecution.exitStatus
}

// Step 2에서 데이터 읽기
@BeforeStep
fun beforeStep(stepExecution: StepExecution) {
    val jobIds = stepExecution.jobExecution.executionContext
        .get("processedJobIds") as List<Long>
}
```

## 8. 배치 모니터링

### Job 실행 상태 조회

```kotlin
@Service
class BatchMonitoringService(
    private val jobExplorer: JobExplorer
) {
    fun getRecentJobExecutions(): List<JobExecution> {
        return jobExplorer.findJobInstancesByJobName("aiContentJob", 0, 10)
            .flatMap { instance ->
                jobExplorer.getJobExecutions(instance)
            }
    }

    fun getJobExecutionStatus(executionId: Long): BatchStatus? {
        return jobExplorer.getJobExecution(executionId)?.status
    }
}
```

## 체크리스트

### Step 개발 시 확인

- [ ] **Chunk Size**: 적절한 chunk size 설정 (일반적으로 5-20)
- [ ] **재시도 설정**: 네트워크/API 오류에 대한 재시도 설정
- [ ] **스킵 설정**: 복구 불가능한 오류는 스킵 처리
- [ ] **Listener**: 시작/종료 로깅을 위한 Listener 추가
- [ ] **트랜잭션**: transactionManager 올바르게 설정
- [ ] **테스트**: Step 단위 테스트 작성

### Job 구성 시 확인

- [ ] **Step 순서**: 의존성에 따른 올바른 Step 순서
- [ ] **실패 처리**: Step 실패 시 전체 Job 실패 또는 스킵 결정
- [ ] **파라미터**: JobParameters 고유성 보장 (timestamp 등)
- [ ] **스케줄링**: @Scheduled 또는 외부 트리거 설정
