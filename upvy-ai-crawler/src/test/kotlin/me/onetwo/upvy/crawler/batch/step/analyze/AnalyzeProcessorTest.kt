package me.onetwo.upvy.crawler.batch.step.analyze

import io.mockk.coEvery
import io.mockk.junit5.MockKExtension
import me.onetwo.upvy.crawler.client.LlmClient
import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.ContentMetadata
import me.onetwo.upvy.crawler.domain.Difficulty
import me.onetwo.upvy.crawler.domain.EditPlan
import me.onetwo.upvy.crawler.domain.EvaluatedVideo
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.Recommendation
import me.onetwo.upvy.crawler.domain.SearchContext
import me.onetwo.upvy.crawler.domain.SearchQuery
import me.onetwo.upvy.crawler.domain.Segment
import me.onetwo.upvy.crawler.domain.VideoCandidate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("AnalyzeProcessor 테스트")
class AnalyzeProcessorTest {

    private lateinit var mockLlmClient: TestLlmClient
    private lateinit var processor: AnalyzeProcessor

    @BeforeEach
    fun setUp() {
        mockLlmClient = TestLlmClient()
        processor = AnalyzeProcessor(mockLlmClient, "mock", "mock-model")
    }

    @Nested
    @DisplayName("process - 자막 분석 및 메타데이터 생성")
    inner class Process {

        @Test
        @DisplayName("자막이 있는 Job이 주어지면 LLM 분석 후 메타데이터를 생성한다")
        fun process_WithTranscript_GeneratesMetadata() {
            // Given: 자막이 있는 Job
            val job = AiContentJob(
                id = 1L,
                youtubeVideoId = "abc123",
                transcript = "프로그래밍의 기초에 대해 설명합니다. 변수, 함수, 조건문 등을 다룹니다.",
                status = JobStatus.TRANSCRIBED
            )

            mockLlmClient.segmentsResponse = listOf(
                Segment(60000, 180000, "변수와 함수", "변수와 함수의 기초 설명", listOf("변수", "함수"))
            )
            mockLlmClient.metadataResponse = ContentMetadata(
                title = "프로그래밍 기초: 변수와 함수",
                description = "프로그래밍의 기초인 변수와 함수에 대해 알아봅니다.",
                tags = listOf("프로그래밍", "변수", "함수", "초보자"),
                category = "PROGRAMMING",
                difficulty = Difficulty.BEGINNER
            )

            // When: 프로세서 실행
            val result = processor.process(job)

            // Then: 메타데이터가 생성됨
            assertThat(result).isNotNull
            assertThat(result!!.generatedTitle).isEqualTo("프로그래밍 기초: 변수와 함수")
            assertThat(result.generatedDescription).contains("변수와 함수")
            assertThat(result.category).isEqualTo("PROGRAMMING")
            assertThat(result.difficulty).isEqualTo(Difficulty.BEGINNER)
            assertThat(result.status).isEqualTo(JobStatus.ANALYZED)
            assertThat(result.llmProvider).isEqualTo("mock")
            assertThat(result.llmModel).isEqualTo("mock-model")
        }

        @Test
        @DisplayName("자막이 없는 Job이 주어지면 null을 반환한다")
        fun process_WithoutTranscript_ReturnsNull() {
            // Given: 자막이 없는 Job
            val job = AiContentJob(
                id = 2L,
                youtubeVideoId = "xyz789",
                transcript = null,
                status = JobStatus.TRANSCRIBED
            )

            // When: 프로세서 실행
            val result = processor.process(job)

            // Then: null 반환
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("빈 자막이 있는 Job이 주어지면 null을 반환한다")
        fun process_WithEmptyTranscript_ReturnsNull() {
            // Given: 빈 자막이 있는 Job
            val job = AiContentJob(
                id = 3L,
                youtubeVideoId = "empty123",
                transcript = "   ",
                status = JobStatus.TRANSCRIBED
            )

            // When: 프로세서 실행
            val result = processor.process(job)

            // Then: null 반환
            assertThat(result).isNull()
        }
    }

    /**
     * 테스트용 LlmClient 구현
     */
    private class TestLlmClient : LlmClient {
        var segmentsResponse: List<Segment> = emptyList()
        var metadataResponse: ContentMetadata = ContentMetadata(
            title = "Test Title",
            description = "Test Description",
            tags = listOf("test"),
            category = "TEST",
            difficulty = Difficulty.BEGINNER
        )

        override suspend fun analyze(prompt: String): String = "Test analysis"
        override suspend fun extractKeySegments(transcript: String): List<Segment> = segmentsResponse
        override suspend fun generateEditPlan(transcript: String): EditPlan = EditPlan(
            clips = emptyList(),
            totalDurationMs = 0L,
            editingStrategy = "test",
            transitionStyle = "hard_cut"
        )
        override suspend fun generateMetadata(content: String, language: ContentLanguage): ContentMetadata = metadataResponse
        override suspend fun generateSearchQueries(context: SearchContext): List<SearchQuery> = emptyList()
        override suspend fun evaluateVideos(candidates: List<VideoCandidate>): List<EvaluatedVideo> = emptyList()
    }
}
