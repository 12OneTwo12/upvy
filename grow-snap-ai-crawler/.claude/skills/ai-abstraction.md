# AI 추상화 레이어 가이드

> AI 제공자/모델을 유동적으로 교체할 수 있는 인터페이스 기반 설계 가이드입니다.

## 개요

이 프로젝트는 **Strategy Pattern**을 사용하여 AI 제공자를 추상화합니다.
설정만 변경하면 OpenAI, Vertex AI, Claude 등 다양한 AI를 사용할 수 있습니다.

## 1. 인터페이스 정의

### LlmClient (Large Language Model)

```kotlin
/**
 * LLM 클라이언트 인터페이스
 * 텍스트 분석, 핵심 구간 추출, 메타데이터 생성 등에 사용
 */
interface LlmClient {

    /**
     * 프롬프트에 대한 응답 생성
     * @param prompt 입력 프롬프트
     * @return AI 응답 텍스트
     */
    suspend fun analyze(prompt: String): String

    /**
     * 영상 자막에서 핵심 구간 추출
     * @param transcript 전체 자막 텍스트
     * @return 추출된 세그먼트 목록 (시작/종료 시간, 제목)
     */
    suspend fun extractKeySegments(transcript: String): List<Segment>

    /**
     * 콘텐츠 메타데이터 자동 생성
     * @param content 콘텐츠 내용
     * @return 제목, 설명, 태그, 카테고리, 난이도
     */
    suspend fun generateMetadata(content: String): ContentMetadata
}
```

### SttClient (Speech-to-Text)

```kotlin
/**
 * STT (Speech-to-Text) 클라이언트 인터페이스
 * 음성을 텍스트로 변환
 */
interface SttClient {

    /**
     * 오디오 파일을 텍스트로 변환
     * @param audioUrl 오디오 파일 URL (S3 등)
     * @return 변환된 텍스트와 타임스탬프
     */
    suspend fun transcribe(audioUrl: String): TranscriptResult
}
```

### 데이터 클래스

```kotlin
data class Segment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val description: String? = null,
    val keywords: List<String> = emptyList()
)

data class TranscriptResult(
    val text: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val language: String? = null,
    val confidence: Float? = null
)

data class TranscriptSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

data class ContentMetadata(
    val title: String,
    val description: String,
    val tags: List<String>,
    val category: String,
    val difficulty: Difficulty
)

enum class Difficulty {
    BEGINNER, INTERMEDIATE, ADVANCED
}
```

## 2. 구현체

### Vertex AI (Google) 구현

```kotlin
/**
 * Google Vertex AI Gemini 클라이언트
 */
@Component
@ConditionalOnProperty(name = ["ai.llm.provider"], havingValue = "vertex")
class VertexAiLlmClient(
    private val config: VertexAiConfig
) : LlmClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val vertexAi = VertexAI(config.projectId, config.location)

    override suspend fun analyze(prompt: String): String {
        logger.debug("Vertex AI 분석 요청: prompt length=${prompt.length}")

        val model = vertexAi.getGenerativeModel(config.model)
        val response = model.generateContent(prompt)

        logger.debug("Vertex AI 응답 수신: response length=${response.text?.length}")
        return response.text ?: throw AiException("Empty response from Vertex AI")
    }

    override suspend fun extractKeySegments(transcript: String): List<Segment> {
        val prompt = """
            다음 영상 자막에서 학습 가치가 높은 1-3분 구간 3개를 추출하세요.
            JSON 배열 형식으로만 응답하세요:
            [{"startTimeMs": 90000, "endTimeMs": 180000, "title": "...", "keywords": ["...", "..."]}]

            자막:
            $transcript
        """.trimIndent()

        val response = analyze(prompt)
        return parseSegments(response)
    }

    override suspend fun generateMetadata(content: String): ContentMetadata {
        val prompt = """
            다음 콘텐츠에 대한 메타데이터를 생성하세요.
            JSON 형식으로만 응답하세요:
            {"title": "...", "description": "...", "tags": [...], "category": "...", "difficulty": "BEGINNER|INTERMEDIATE|ADVANCED"}

            콘텐츠:
            $content
        """.trimIndent()

        val response = analyze(prompt)
        return parseMetadata(response)
    }
}
```

### Google STT 구현

```kotlin
/**
 * Google Cloud Speech-to-Text 클라이언트
 */
@Component
@ConditionalOnProperty(name = ["ai.stt.provider"], havingValue = "google")
class GoogleSttClient(
    private val config: GoogleSttConfig
) : SttClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    override suspend fun transcribe(audioUrl: String): TranscriptResult {
        logger.info("Google STT 변환 시작: audioUrl=$audioUrl")

        val client = SpeechClient.create()
        val audio = RecognitionAudio.newBuilder()
            .setUri(audioUrl)
            .build()

        val recognitionConfig = RecognitionConfig.newBuilder()
            .setLanguageCode("ko-KR")
            .setModel(config.model)  // "chirp" - 최신 모델
            .setEnableWordTimeOffsets(true)
            .build()

        val response = client.recognize(recognitionConfig, audio)

        val text = response.resultsList
            .joinToString(" ") { it.alternativesList[0].transcript }

        val segments = extractSegments(response)

        logger.info("Google STT 변환 완료: text length=${text.length}")

        return TranscriptResult(
            text = text,
            segments = segments,
            language = "ko-KR"
        )
    }
}
```

### OpenAI 구현 (대안)

```kotlin
/**
 * OpenAI GPT-4 클라이언트
 */
@Component
@ConditionalOnProperty(name = ["ai.llm.provider"], havingValue = "openai")
class OpenAiLlmClient(
    private val config: OpenAiConfig
) : LlmClient {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val openAi = OpenAI(config.apiKey)

    override suspend fun analyze(prompt: String): String {
        logger.debug("OpenAI 분석 요청: prompt length=${prompt.length}")

        val response = openAi.chatCompletion(
            ChatCompletionRequest(
                model = ModelId(config.model),
                messages = listOf(
                    ChatMessage(role = ChatRole.User, content = prompt)
                )
            )
        )

        return response.choices[0].message.content
            ?: throw AiException("Empty response from OpenAI")
    }
}

/**
 * OpenAI Whisper STT 클라이언트
 */
@Component
@ConditionalOnProperty(name = ["ai.stt.provider"], havingValue = "whisper")
class WhisperSttClient(
    private val config: OpenAiConfig
) : SttClient {

    override suspend fun transcribe(audioUrl: String): TranscriptResult {
        val openAi = OpenAI(config.apiKey)

        val tempFile = downloadToTemp(audioUrl)

        val response = openAi.transcription(
            TranscriptionRequest(
                audio = FileSource(tempFile.toPath(), fileSystem = FileSystem.SYSTEM),
                model = ModelId("whisper-1")
            )
        )

        tempFile.delete()

        return TranscriptResult(text = response.text)
    }
}
```

## 3. 설정

### application.yml

```yaml
ai:
  llm:
    provider: vertex          # vertex | openai | claude
    model: gemini-1.5-pro     # 사용할 모델

  stt:
    provider: google          # google | whisper
    model: chirp              # chirp | whisper-1

# Vertex AI 설정 (Google)
vertex:
  project-id: ${GCP_PROJECT_ID}
  location: asia-northeast3   # 서울 리전

# OpenAI 설정 (대안)
openai:
  api-key: ${OPENAI_API_KEY}
  model: gpt-4o
```

### 설정 클래스

```kotlin
@ConfigurationProperties(prefix = "vertex")
data class VertexAiConfig(
    val projectId: String,
    val location: String = "asia-northeast3"
)

@ConfigurationProperties(prefix = "openai")
data class OpenAiConfig(
    val apiKey: String,
    val model: String = "gpt-4o"
)

@ConfigurationProperties(prefix = "ai.llm")
data class LlmConfig(
    val provider: String = "vertex",
    val model: String = "gemini-1.5-pro"
)

@ConfigurationProperties(prefix = "ai.stt")
data class SttConfig(
    val provider: String = "google",
    val model: String = "chirp"
)
```

## 4. 사용법

### Step에서 AI 클라이언트 사용

```kotlin
@Component
class TranscribeProcessor(
    private val sttClient: SttClient,  // 인터페이스만 의존
    private val s3Service: S3Service
) : ItemProcessor<AiContentJob, AiContentJob> {

    override fun process(job: AiContentJob): AiContentJob {
        // S3에서 오디오 URL 생성
        val audioUrl = s3Service.generatePresignedUrl(job.rawVideoS3Key)

        // STT 변환 (어떤 구현체인지 몰라도 됨)
        val result = runBlocking {
            sttClient.transcribe(audioUrl)
        }

        return job.copy(
            transcript = result.text,
            status = JobStatus.TRANSCRIBED
        )
    }
}

@Component
class AnalyzeProcessor(
    private val llmClient: LlmClient  // 인터페이스만 의존
) : ItemProcessor<AiContentJob, AiContentJob> {

    override fun process(job: AiContentJob): AiContentJob {
        val segments = runBlocking {
            llmClient.extractKeySegments(job.transcript!!)
        }

        val metadata = runBlocking {
            llmClient.generateMetadata(job.transcript!!)
        }

        return job.copy(
            generatedTitle = metadata.title,
            generatedDescription = metadata.description,
            generatedTags = metadata.tags,
            category = metadata.category,
            difficulty = metadata.difficulty,
            status = JobStatus.ANALYZED
        )
    }
}
```

## 5. AI 제공자 비교

| 항목 | Vertex AI (Gemini) | OpenAI (GPT-4) |
|------|-------------------|----------------|
| LLM 비용 | $1.25/1M tokens | $10-30/1M tokens |
| STT 비용 | $0.016/분 | $0.006/분 |
| 한국어 | 매우 좋음 | 좋음 |
| GCP 통합 | 네이티브 | 별도 설정 |
| 추천 | 기본값 | 대안 |

## 6. 새로운 AI 제공자 추가

### 1단계: 구현체 작성

```kotlin
@Component
@ConditionalOnProperty(name = ["ai.llm.provider"], havingValue = "claude")
class ClaudeLlmClient(
    private val config: ClaudeConfig
) : LlmClient {
    // 구현...
}
```

### 2단계: 설정 추가

```yaml
ai:
  llm:
    provider: claude  # 새로운 제공자 선택

claude:
  api-key: ${CLAUDE_API_KEY}
  model: claude-3-sonnet
```

### 3단계: 테스트

```kotlin
@Test
fun `Claude 클라이언트가 올바르게 동작한다`() {
    // Given
    val client = ClaudeLlmClient(config)

    // When
    val result = runBlocking { client.analyze("테스트 프롬프트") }

    // Then
    assertThat(result).isNotEmpty()
}
```

## 체크리스트

### AI 클라이언트 구현 시 확인

- [ ] **인터페이스 구현**: LlmClient 또는 SttClient 인터페이스 구현
- [ ] **@ConditionalOnProperty**: 설정에 따라 Bean 등록 조건 설정
- [ ] **로깅**: 요청/응답에 대한 적절한 로깅
- [ ] **에러 처리**: API 오류에 대한 적절한 예외 변환
- [ ] **테스트**: 단위 테스트 및 통합 테스트 작성

### AI 사용 시 확인

- [ ] **인터페이스 의존**: 구현체가 아닌 인터페이스에 의존
- [ ] **suspend 함수**: 코루틴 환경에서 호출 (runBlocking 사용)
- [ ] **Rate Limit**: API 호출 제한 고려
- [ ] **비용 모니터링**: API 사용량 추적
