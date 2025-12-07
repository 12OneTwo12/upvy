package me.onetwo.growsnap.crawler.client.stt

import com.google.cloud.speech.v1.LongRunningRecognizeResponse
import com.google.cloud.speech.v1.RecognizeResponse
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechRecognitionAlternative
import com.google.cloud.speech.v1.SpeechRecognitionResult
import com.google.cloud.speech.v1.WordInfo
import com.google.cloud.speech.v1.LongRunningRecognizeMetadata
import com.google.protobuf.Duration
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VertexAiSttClient 테스트")
class VertexAiSttClientTest {

    private lateinit var client: VertexAiSttClient
    private lateinit var mockSpeechClient: SpeechClient

    @BeforeEach
    fun setUp() {
        mockSpeechClient = mockk(relaxed = true)

        // SpeechClient.create() 모킹
        mockkStatic(SpeechClient::class)
        every { SpeechClient.create() } returns mockSpeechClient
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("transcribe - 음성 텍스트 변환")
    inner class Transcribe {

        @Test
        @DisplayName("오디오 URL이 주어지면 텍스트를 반환한다")
        fun transcribe_WithAudioUrl_ReturnsTranscript() = runBlocking {
            // Given: 모킹된 STT 응답
            val mockResponse = createMockResponse(
                transcript = "안녕하세요 프로그래밍 강좌입니다",
                confidence = 0.95f
            )
            every { mockSpeechClient.recognize(any(), any()) } returns mockResponse

            client = createClient()

            // When: 음성 변환
            // GCS URI를 사용하면 URL 다운로드를 시도하지 않음
            val result = client.transcribe("gs://test-bucket/audio.wav")

            // Then: 텍스트 반환
            assertThat(result.text).contains("안녕하세요")
            assertThat(result.language).isEqualTo("ko-KR")
            assertThat(result.confidence).isGreaterThan(0.9f)
        }

        @Test
        @DisplayName("단어 레벨 타임스탬프가 있으면 세그먼트를 생성한다")
        fun transcribe_WithWordTimeOffsets_CreatesSegments() = runBlocking {
            // Given: 단어 타임스탬프가 있는 응답
            val mockResponse = createMockResponseWithWords(
                transcript = "안녕하세요 프로그래밍 강좌입니다",
                words = listOf(
                    WordInfoData("안녕하세요", 0, 500),
                    WordInfoData("프로그래밍", 600, 1200),
                    WordInfoData("강좌입니다", 1300, 2000)
                ),
                confidence = 0.92f
            )
            every { mockSpeechClient.recognize(any(), any()) } returns mockResponse

            client = createClient()

            // When: 음성 변환
            val result = client.transcribe("gs://test-bucket/audio.wav")

            // Then: 세그먼트가 생성됨
            assertThat(result.text).isNotEmpty()
            assertThat(result.language).isEqualTo("ko-KR")
        }

        @Test
        @DisplayName("여러 결과가 있으면 모두 합쳐서 반환한다")
        fun transcribe_WithMultipleResults_CombinesAll() = runBlocking {
            // Given: 여러 결과가 있는 응답
            val alternative1 = SpeechRecognitionAlternative.newBuilder()
                .setTranscript("첫 번째 문장입니다.")
                .setConfidence(0.9f)
                .build()

            val alternative2 = SpeechRecognitionAlternative.newBuilder()
                .setTranscript("두 번째 문장입니다.")
                .setConfidence(0.85f)
                .build()

            val result1 = SpeechRecognitionResult.newBuilder()
                .addAlternatives(alternative1)
                .build()

            val result2 = SpeechRecognitionResult.newBuilder()
                .addAlternatives(alternative2)
                .build()

            val mockResponse = RecognizeResponse.newBuilder()
                .addResults(result1)
                .addResults(result2)
                .build()

            every { mockSpeechClient.recognize(any(), any()) } returns mockResponse

            client = createClient()

            // When: 음성 변환
            val result = client.transcribe("gs://test-bucket/audio.wav")

            // Then: 모든 텍스트가 합쳐짐
            assertThat(result.text).contains("첫 번째 문장")
            assertThat(result.text).contains("두 번째 문장")
        }

        @Test
        @DisplayName("빈 결과가 반환되면 빈 텍스트를 반환한다")
        fun transcribe_WithEmptyResult_ReturnsEmptyText() = runBlocking {
            // Given: 빈 응답 (LongRunningRecognize 사용 - gs:// URI이므로)
            val mockLongRunningResponse = LongRunningRecognizeResponse.newBuilder().build()
            val mockFuture = mockk<com.google.api.gax.longrunning.OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata>>(relaxed = true)
            every { mockFuture.get() } returns mockLongRunningResponse
            every { mockSpeechClient.longRunningRecognizeAsync(any(), any()) } returns mockFuture

            client = createClient()

            // When: 음성 변환 (gs:// URI는 transcribeLongAudio 사용)
            val result = client.transcribeLongAudio("gs://test-bucket/empty-audio.wav")

            // Then: 빈 텍스트
            assertThat(result.text).isEmpty()
            assertThat(result.segments).isEmpty()
        }
    }

    @Nested
    @DisplayName("transcribeLongAudio - 긴 오디오 비동기 변환")
    inner class TranscribeLongAudio {

        @Test
        @DisplayName("GCS URI가 주어지면 비동기 처리로 텍스트를 반환한다")
        fun transcribeLongAudio_WithGcsUri_ReturnsTranscript() = runBlocking {
            // Given: 비동기 처리 응답 모킹
            val mockLongRunningResponse = createMockLongRunningResponse(
                transcript = "긴 오디오의 텍스트입니다"
            )

            // LongRunningRecognize 모킹
            val mockFuture = mockk<com.google.api.gax.longrunning.OperationFuture<LongRunningRecognizeResponse, LongRunningRecognizeMetadata>>(relaxed = true)
            every { mockFuture.get() } returns mockLongRunningResponse
            every { mockSpeechClient.longRunningRecognizeAsync(any(), any()) } returns mockFuture

            client = createClient()

            // When: 긴 오디오 변환
            val result = client.transcribeLongAudio("gs://test-bucket/long-audio.wav")

            // Then: 텍스트 반환
            assertThat(result.text).contains("긴 오디오")
            assertThat(result.language).isEqualTo("ko-KR")
        }
    }

    // ========== Helper Methods ==========

    private fun createClient(): VertexAiSttClient {
        val client = VertexAiSttClient(
            languageCode = "ko-KR",
            sampleRateHertz = 16000,
            enableWordTimeOffsets = true
        )
        client.init()
        return client
    }

    private fun createMockResponse(transcript: String, confidence: Float): RecognizeResponse {
        val alternative = SpeechRecognitionAlternative.newBuilder()
            .setTranscript(transcript)
            .setConfidence(confidence)
            .build()

        val result = SpeechRecognitionResult.newBuilder()
            .addAlternatives(alternative)
            .build()

        return RecognizeResponse.newBuilder()
            .addResults(result)
            .build()
    }

    private fun createMockResponseWithWords(
        transcript: String,
        words: List<WordInfoData>,
        confidence: Float
    ): RecognizeResponse {
        val alternativeBuilder = SpeechRecognitionAlternative.newBuilder()
            .setTranscript(transcript)
            .setConfidence(confidence)

        words.forEach { wordData ->
            val wordInfo = WordInfo.newBuilder()
                .setWord(wordData.word)
                .setStartTime(Duration.newBuilder().setSeconds(wordData.startMs / 1000).setNanos((wordData.startMs % 1000).toInt() * 1_000_000))
                .setEndTime(Duration.newBuilder().setSeconds(wordData.endMs / 1000).setNanos((wordData.endMs % 1000).toInt() * 1_000_000))
                .build()
            alternativeBuilder.addWords(wordInfo)
        }

        val result = SpeechRecognitionResult.newBuilder()
            .addAlternatives(alternativeBuilder.build())
            .build()

        return RecognizeResponse.newBuilder()
            .addResults(result)
            .build()
    }

    private fun createMockLongRunningResponse(transcript: String): LongRunningRecognizeResponse {
        val alternative = SpeechRecognitionAlternative.newBuilder()
            .setTranscript(transcript)
            .setConfidence(0.9f)
            .build()

        val result = SpeechRecognitionResult.newBuilder()
            .addAlternatives(alternative)
            .build()

        return LongRunningRecognizeResponse.newBuilder()
            .addResults(result)
            .build()
    }

    private data class WordInfoData(
        val word: String,
        val startMs: Long,
        val endMs: Long
    )
}
