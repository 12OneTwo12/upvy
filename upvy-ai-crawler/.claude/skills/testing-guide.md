# 테스트 작성 가이드

> Spring Batch Step, Service, AI 클라이언트 테스트 규칙을 정의합니다.

## 테스트 작성 필수 계층

**모든 기능 구현 시, 다음 계층의 테스트를 반드시 작성합니다:**

1. **Step 테스트** - ItemReader/Processor/Writer 개별 테스트
2. **Service 테스트** - 비즈니스 로직, 예외 처리
3. **AI 클라이언트 테스트** - Mock AI를 사용한 통합 테스트
4. **Job 통합 테스트** - 전체 배치 플로우 테스트

## 1. Step 테스트

### ItemProcessor 테스트 템플릿

```kotlin
@ExtendWith(MockKExtension::class)
@DisplayName("VideoDownloadProcessor 테스트")
class VideoDownloadProcessorTest {

    @MockK
    private lateinit var ytDlpRunner: YtDlpRunner

    @MockK
    private lateinit var s3Service: S3Service

    @InjectMockKs
    private lateinit var processor: VideoDownloadProcessor

    @Nested
    @DisplayName("process - 비디오 다운로드 및 업로드")
    inner class Process {

        @Test
        @DisplayName("유효한 비디오 ID로 처리 시, AiContentJob을 반환한다")
        fun process_WithValidVideoId_ReturnsAiContentJob() {
            // Given: 테스트 데이터
            val candidate = VideoCandidate(
                videoId = "abc123",
                title = "Test Video",
                channelId = "channel123"
            )
            val localPath = "/tmp/abc123.mp4"
            val s3Key = "raw/abc123.mp4"

            every { ytDlpRunner.download(candidate.videoId) } returns localPath
            every { s3Service.upload(localPath, any()) } returns s3Key

            // When: 프로세서 실행
            val result = processor.process(candidate)

            // Then: 결과 검증
            assertThat(result).isNotNull
            assertThat(result!!.youtubeVideoId).isEqualTo("abc123")
            assertThat(result.status).isEqualTo(JobStatus.CRAWLED)
            assertThat(result.rawVideoS3Key).isEqualTo(s3Key)

            verify(exactly = 1) { ytDlpRunner.download("abc123") }
            verify(exactly = 1) { s3Service.upload(localPath, any()) }
        }

        @Test
        @DisplayName("다운로드 실패 시, null을 반환한다 (스킵 처리)")
        fun process_WhenDownloadFails_ReturnsNull() {
            // Given: 다운로드 실패 상황
            val candidate = VideoCandidate(videoId = "invalid", title = "Invalid")

            every { ytDlpRunner.download(any()) } throws IOException("Download failed")

            // When: 프로세서 실행
            val result = processor.process(candidate)

            // Then: null 반환 (스킵)
            assertThat(result).isNull()
        }
    }
}
```

### ItemReader 테스트 템플릿

```kotlin
@ExtendWith(MockKExtension::class)
@DisplayName("YouTubeCcVideoReader 테스트")
class YouTubeCcVideoReaderTest {

    @MockK
    private lateinit var youTubeService: YouTubeService

    @InjectMockKs
    private lateinit var reader: YouTubeCcVideoReader

    @Test
    @DisplayName("CC 비디오 목록을 순차적으로 읽는다")
    fun read_ReturnsVideosSequentially() {
        // Given: 비디오 목록
        val videos = listOf(
            VideoCandidate("vid1", "Video 1", "ch1"),
            VideoCandidate("vid2", "Video 2", "ch2"),
            VideoCandidate("vid3", "Video 3", "ch3")
        )

        every { youTubeService.searchCcVideos(any(), any()) } returns videos

        // When & Then: 순차적으로 읽기
        assertThat(reader.read()).isEqualTo(videos[0])
        assertThat(reader.read()).isEqualTo(videos[1])
        assertThat(reader.read()).isEqualTo(videos[2])
        assertThat(reader.read()).isNull()  // 더 이상 없음
    }
}
```

### ItemWriter 테스트 템플릿

```kotlin
@ExtendWith(MockKExtension::class)
@DisplayName("AiContentJobWriter 테스트")
class AiContentJobWriterTest {

    @MockK
    private lateinit var jobRepository: AiContentJobRepository

    @InjectMockKs
    private lateinit var writer: AiContentJobWriter

    @Test
    @DisplayName("Job 목록을 데이터베이스에 저장한다")
    fun write_SavesJobsToDatabase() {
        // Given: Job 목록
        val jobs = listOf(
            AiContentJob(youtubeVideoId = "vid1", status = JobStatus.CRAWLED),
            AiContentJob(youtubeVideoId = "vid2", status = JobStatus.CRAWLED)
        )
        val chunk = Chunk(jobs)

        every { jobRepository.save(any()) } returnsArgument 0

        // When: Writer 실행
        writer.write(chunk)

        // Then: 모든 Job이 저장됨
        verify(exactly = 2) { jobRepository.save(any()) }
    }
}
```

## 2. AI 클라이언트 테스트

### Mock AI 클라이언트

```kotlin
/**
 * 테스트용 Mock LLM 클라이언트
 */
class MockLlmClient : LlmClient {

    var analyzeResponse: String = "Mock response"
    var segmentsResponse: List<Segment> = emptyList()
    var metadataResponse: ContentMetadata = ContentMetadata(
        title = "Mock Title",
        description = "Mock Description",
        tags = listOf("mock", "test"),
        category = "PROGRAMMING",
        difficulty = Difficulty.BEGINNER
    )

    override suspend fun analyze(prompt: String): String = analyzeResponse

    override suspend fun extractKeySegments(transcript: String): List<Segment> = segmentsResponse

    override suspend fun generateMetadata(content: String): ContentMetadata = metadataResponse
}

/**
 * 테스트용 Mock STT 클라이언트
 */
class MockSttClient : SttClient {

    var transcribeResponse: TranscriptResult = TranscriptResult(
        text = "Mock transcript text",
        language = "ko-KR"
    )

    override suspend fun transcribe(audioUrl: String): TranscriptResult = transcribeResponse
}
```

### AI 프로세서 테스트

```kotlin
@ExtendWith(MockKExtension::class)
@DisplayName("AnalyzeProcessor 테스트")
class AnalyzeProcessorTest {

    private lateinit var mockLlmClient: MockLlmClient
    private lateinit var processor: AnalyzeProcessor

    @BeforeEach
    fun setUp() {
        mockLlmClient = MockLlmClient()
        processor = AnalyzeProcessor(mockLlmClient)
    }

    @Test
    @DisplayName("자막 분석 후 메타데이터를 생성한다")
    fun process_AnalyzesTranscriptAndGeneratesMetadata() {
        // Given: 자막이 있는 Job
        val job = AiContentJob(
            youtubeVideoId = "abc123",
            transcript = "영상 자막 내용...",
            status = JobStatus.TRANSCRIBED
        )

        mockLlmClient.segmentsResponse = listOf(
            Segment(startTimeMs = 60000, endTimeMs = 180000, title = "핵심 구간 1")
        )
        mockLlmClient.metadataResponse = ContentMetadata(
            title = "AI가 생성한 제목",
            description = "AI가 생성한 설명",
            tags = listOf("kotlin", "programming"),
            category = "PROGRAMMING",
            difficulty = Difficulty.INTERMEDIATE
        )

        // When: 프로세서 실행
        val result = processor.process(job)

        // Then: 메타데이터가 생성됨
        assertThat(result!!.generatedTitle).isEqualTo("AI가 생성한 제목")
        assertThat(result.generatedDescription).isEqualTo("AI가 생성한 설명")
        assertThat(result.generatedTags).contains("kotlin", "programming")
        assertThat(result.status).isEqualTo(JobStatus.ANALYZED)
    }
}
```

## 3. Job 통합 테스트

### 전체 배치 플로우 테스트

```kotlin
@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AI Content Batch Job 통합 테스트")
class AiContentJobIntegrationTest {

    @Autowired
    private lateinit var jobLauncherTestUtils: JobLauncherTestUtils

    @Autowired
    private lateinit var jobRepository: AiContentJobRepository

    @MockBean
    private lateinit var youTubeService: YouTubeService

    @MockBean
    private lateinit var ytDlpRunner: YtDlpRunner

    @MockBean
    private lateinit var s3Service: S3Service

    @Test
    @DisplayName("전체 배치 Job이 성공적으로 완료된다")
    fun aiContentJob_CompletesSuccessfully() {
        // Given: Mock 설정
        every { youTubeService.searchCcVideos(any(), any()) } returns listOf(
            VideoCandidate("vid1", "Test Video 1", "ch1")
        )
        every { ytDlpRunner.download(any()) } returns "/tmp/video.mp4"
        every { s3Service.upload(any(), any()) } returns "s3://bucket/video.mp4"

        // When: Job 실행
        val jobExecution = jobLauncherTestUtils.launchJob()

        // Then: 성공 확인
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)
        assertThat(jobExecution.exitStatus).isEqualTo(ExitStatus.COMPLETED)

        // Step 검증
        val stepExecution = jobExecution.stepExecutions.first()
        assertThat(stepExecution.readCount).isEqualTo(1)
        assertThat(stepExecution.writeCount).isEqualTo(1)
    }

    @Test
    @DisplayName("다운로드 실패 시 해당 아이템을 스킵하고 계속 진행한다")
    fun aiContentJob_SkipsFailedDownloads() {
        // Given: 일부 다운로드 실패
        every { youTubeService.searchCcVideos(any(), any()) } returns listOf(
            VideoCandidate("vid1", "Video 1", "ch1"),
            VideoCandidate("vid2", "Video 2", "ch2"),  // 실패할 것
            VideoCandidate("vid3", "Video 3", "ch3")
        )
        every { ytDlpRunner.download("vid1") } returns "/tmp/vid1.mp4"
        every { ytDlpRunner.download("vid2") } throws IOException("Download failed")
        every { ytDlpRunner.download("vid3") } returns "/tmp/vid3.mp4"
        every { s3Service.upload(any(), any()) } returns "s3://bucket/video.mp4"

        // When: Job 실행
        val jobExecution = jobLauncherTestUtils.launchJob()

        // Then: 완료되고, 1개 스킵
        assertThat(jobExecution.status).isEqualTo(BatchStatus.COMPLETED)

        val stepExecution = jobExecution.stepExecutions.first()
        assertThat(stepExecution.readCount).isEqualTo(3)
        assertThat(stepExecution.writeCount).isEqualTo(2)  // 2개만 성공
        assertThat(stepExecution.skipCount).isEqualTo(1)   // 1개 스킵
    }
}
```

## 4. 테스트 설정

### test/resources/application-test.yml

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false  # 테스트 시 자동 실행 방지

  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver

ai:
  llm:
    provider: mock  # 테스트용 Mock 사용
  stt:
    provider: mock
```

### 테스트 설정 클래스

```kotlin
@TestConfiguration
class BatchTestConfig {

    @Bean
    @Primary
    fun mockLlmClient(): LlmClient = MockLlmClient()

    @Bean
    @Primary
    fun mockSttClient(): SttClient = MockSttClient()
}
```

## 5. 테스트 체크리스트

### 모든 기능 구현 시 필수

- [ ] **ItemProcessor 테스트**: 정상/실패 케이스 모두 테스트
- [ ] **ItemReader 테스트**: 순차 읽기, 종료 조건 테스트
- [ ] **ItemWriter 테스트**: 저장 로직 테스트
- [ ] **AI 클라이언트 테스트**: Mock 클라이언트로 테스트
- [ ] **Job 통합 테스트**: 전체 플로우 테스트
- [ ] **Given-When-Then**: 모든 테스트에 명시적으로 작성
- [ ] **DisplayName**: 한글로 명확한 시나리오 설명
- [ ] **빌드/테스트 통과**: 모든 테스트가 통과해야 작업 완료

### 테스트 품질 확인

- [ ] **시나리오 기반**: 테스트만 보고 기능을 즉시 파악할 수 있는가?
- [ ] **독립성**: 각 테스트가 독립적으로 실행되는가?
- [ ] **재현 가능성**: 테스트 실패 시 동일한 조건으로 재현 가능한가?
- [ ] **빠른 실행**: 테스트가 빠르게 실행되는가?

## 6. 금지 사항

### Thread.sleep() 절대 금지

```kotlin
// BAD: Thread.sleep 사용 금지
Thread.sleep(500)
Thread.sleep(1000)

// GOOD: Awaitility 사용
await.atMost(2, TimeUnit.SECONDS).untilAsserted {
    assertThat(result).isEqualTo(expected)
}
```

### 실제 외부 API 호출 금지

```kotlin
// BAD: 테스트에서 실제 API 호출
val client = OpenAiLlmClient(realConfig)
client.analyze(prompt)  // 실제 OpenAI 호출

// GOOD: Mock 클라이언트 사용
val client = MockLlmClient()
client.analyzeResponse = "Expected response"
client.analyze(prompt)
```
