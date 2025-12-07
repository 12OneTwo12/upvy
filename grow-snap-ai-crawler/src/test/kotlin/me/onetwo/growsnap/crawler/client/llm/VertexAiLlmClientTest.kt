package me.onetwo.growsnap.crawler.client.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.Candidate
import com.google.cloud.vertexai.api.Content
import com.google.cloud.vertexai.api.GenerateContentResponse
import com.google.cloud.vertexai.api.Part
import com.google.cloud.vertexai.generativeai.GenerativeModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import me.onetwo.growsnap.crawler.domain.Difficulty
import me.onetwo.growsnap.crawler.domain.Recommendation
import me.onetwo.growsnap.crawler.domain.SearchContext
import me.onetwo.growsnap.crawler.domain.VideoCandidate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VertexAiLlmClient 테스트")
class VertexAiLlmClientTest {

    private lateinit var client: VertexAiLlmClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var mockVertexAI: VertexAI
    private lateinit var mockModel: GenerativeModel

    @BeforeEach
    fun setUp() {
        objectMapper = jacksonObjectMapper()
        mockVertexAI = mockk(relaxed = true)
        mockModel = mockk(relaxed = true)

        // VertexAI와 GenerativeModel 생성자를 모킹
        mockkConstructor(VertexAI::class)
        mockkConstructor(GenerativeModel::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("analyze - 프롬프트 분석")
    inner class Analyze {

        @Test
        @DisplayName("프롬프트가 주어지면 LLM 응답을 반환한다")
        fun analyze_WithPrompt_ReturnsResponse() = runBlocking {
            // Given: 모킹된 응답
            val expectedResponse = "이것은 AI의 분석 결과입니다."
            val mockResponse = createMockResponse(expectedResponse)

            every { anyConstructed<GenerativeModel>().generateContent(any<String>()) } returns mockResponse
            every { anyConstructed<GenerativeModel>().withGenerationConfig(any()) } returns mockk(relaxed = true) {
                every { generateContent(any<String>()) } returns mockResponse
            }

            // Client 생성 및 초기화
            client = createClient()

            // When: 분석 요청
            val result = client.analyze("테스트 프롬프트")

            // Then: 응답 반환
            assertThat(result).isEqualTo(expectedResponse)
        }
    }

    @Nested
    @DisplayName("extractKeySegments - 핵심 세그먼트 추출")
    inner class ExtractKeySegments {

        @Test
        @DisplayName("자막이 주어지면 세그먼트 목록을 반환한다")
        fun extractKeySegments_WithTranscript_ReturnsSegments() = runBlocking {
            // Given: 세그먼트 JSON 응답
            val segmentsJson = """
                [
                  {
                    "startTimeMs": 60000,
                    "endTimeMs": 120000,
                    "title": "변수 소개",
                    "description": "변수의 개념을 설명합니다",
                    "keywords": ["변수", "프로그래밍"]
                  },
                  {
                    "startTimeMs": 180000,
                    "endTimeMs": 240000,
                    "title": "함수 정의",
                    "description": "함수를 정의하는 방법",
                    "keywords": ["함수", "정의"]
                  }
                ]
            """.trimIndent()

            val mockResponse = createMockResponse(segmentsJson)
            every { anyConstructed<GenerativeModel>().withGenerationConfig(any()) } returns mockk(relaxed = true) {
                every { generateContent(any<String>()) } returns mockResponse
            }

            client = createClient()

            // When: 세그먼트 추출
            val transcript = "프로그래밍 강의 자막입니다."
            val segments = client.extractKeySegments(transcript)

            // Then: 세그먼트 목록 반환
            assertThat(segments).hasSize(2)
            assertThat(segments[0].title).isEqualTo("변수 소개")
            assertThat(segments[0].startTimeMs).isEqualTo(60000)
            assertThat(segments[0].endTimeMs).isEqualTo(120000)
            assertThat(segments[1].title).isEqualTo("함수 정의")
        }

        @Test
        @DisplayName("Markdown 코드 블록으로 감싸진 JSON도 파싱한다")
        fun extractKeySegments_WithMarkdownCodeBlock_ParsesCorrectly() = runBlocking {
            // Given: Markdown 코드 블록으로 감싸진 응답
            val response = """
                ```json
                [
                  {
                    "startTimeMs": 30000,
                    "endTimeMs": 90000,
                    "title": "테스트 세그먼트",
                    "description": "설명",
                    "keywords": ["test"]
                  }
                ]
                ```
            """.trimIndent()

            val mockResponse = createMockResponse(response)
            every { anyConstructed<GenerativeModel>().withGenerationConfig(any()) } returns mockk(relaxed = true) {
                every { generateContent(any<String>()) } returns mockResponse
            }

            client = createClient()

            // When: 세그먼트 추출
            val segments = client.extractKeySegments("자막")

            // Then: 정상 파싱
            assertThat(segments).hasSize(1)
            assertThat(segments[0].title).isEqualTo("테스트 세그먼트")
        }
    }

    @Nested
    @DisplayName("generateMetadata - 메타데이터 생성")
    inner class GenerateMetadata {

        @Test
        @DisplayName("콘텐츠가 주어지면 메타데이터를 생성한다")
        fun generateMetadata_WithContent_ReturnsMetadata() = runBlocking {
            // Given: 메타데이터 JSON 응답
            val metadataJson = """
                {
                  "title": "Kotlin 입문 강좌",
                  "description": "Kotlin 프로그래밍 언어의 기초를 배웁니다.",
                  "tags": ["Kotlin", "프로그래밍", "입문"],
                  "category": "PROGRAMMING",
                  "difficulty": "BEGINNER"
                }
            """.trimIndent()

            val mockResponse = createMockResponse(metadataJson)
            every { anyConstructed<GenerativeModel>().withGenerationConfig(any()) } returns mockk(relaxed = true) {
                every { generateContent(any<String>()) } returns mockResponse
            }

            client = createClient()

            // When: 메타데이터 생성
            val metadata = client.generateMetadata("Kotlin에 대한 강의 내용")

            // Then: 메타데이터 반환
            assertThat(metadata.title).isEqualTo("Kotlin 입문 강좌")
            assertThat(metadata.description).contains("Kotlin")
            assertThat(metadata.tags).contains("Kotlin", "프로그래밍")
            assertThat(metadata.category).isEqualTo("PROGRAMMING")
            assertThat(metadata.difficulty).isEqualTo(Difficulty.BEGINNER)
        }
    }

    @Nested
    @DisplayName("generateSearchQueries - 검색 쿼리 생성")
    inner class GenerateSearchQueries {

        @Test
        @DisplayName("검색 컨텍스트가 주어지면 쿼리 목록을 반환한다")
        fun generateSearchQueries_WithContext_ReturnsQueries() = runBlocking {
            // Given: 검색 쿼리 JSON 응답
            val queriesJson = """
                [
                  {
                    "query": "Kotlin 코루틴 튜토리얼",
                    "targetCategory": "PROGRAMMING",
                    "expectedContentType": "tutorial",
                    "priority": 10
                  },
                  {
                    "query": "AI 기초 강좌",
                    "targetCategory": "SCIENCE",
                    "expectedContentType": "lecture",
                    "priority": 8
                  }
                ]
            """.trimIndent()

            val mockResponse = createMockResponse(queriesJson)
            every { anyConstructed<GenerativeModel>().withGenerationConfig(any()) } returns mockk(relaxed = true) {
                every { generateContent(any<String>()) } returns mockResponse
            }

            client = createClient()

            // When: 검색 쿼리 생성
            val context = SearchContext(
                appCategories = listOf("PROGRAMMING", "SCIENCE"),
                popularKeywords = listOf("Kotlin", "AI"),
                topPerformingTags = listOf("튜토리얼"),
                seasonalContext = null,
                recentlyPublished = emptyList(),
                underrepresentedCategories = listOf("HISTORY")
            )
            val queries = client.generateSearchQueries(context)

            // Then: 쿼리 목록 반환
            assertThat(queries).hasSize(2)
            assertThat(queries[0].query).isEqualTo("Kotlin 코루틴 튜토리얼")
            assertThat(queries[0].priority).isEqualTo(10)
        }
    }

    @Nested
    @DisplayName("evaluateVideos - 비디오 평가")
    inner class EvaluateVideos {

        @Test
        @DisplayName("비디오 후보가 주어지면 평가 결과를 반환한다")
        fun evaluateVideos_WithCandidates_ReturnsEvaluations() = runBlocking {
            // Given: 평가 결과 JSON 응답
            val evaluationsJson = """
                [
                  {
                    "index": 0,
                    "relevanceScore": 90,
                    "educationalValue": 85,
                    "predictedQuality": 88,
                    "recommendation": "HIGHLY_RECOMMENDED",
                    "reasoning": "고품질 교육 콘텐츠입니다."
                  },
                  {
                    "index": 1,
                    "relevanceScore": 60,
                    "educationalValue": 55,
                    "predictedQuality": 58,
                    "recommendation": "MAYBE",
                    "reasoning": "교육적 가치가 제한적입니다."
                  }
                ]
            """.trimIndent()

            val mockResponse = createMockResponse(evaluationsJson)
            every { anyConstructed<GenerativeModel>().withGenerationConfig(any()) } returns mockk(relaxed = true) {
                every { generateContent(any<String>()) } returns mockResponse
            }

            client = createClient()

            // When: 비디오 평가
            val candidates = listOf(
                VideoCandidate("vid1", "Kotlin Tutorial", "ch1", "Developer Channel"),
                VideoCandidate("vid2", "Random Video", "ch2", "Random Channel")
            )
            val evaluations = client.evaluateVideos(candidates)

            // Then: 평가 결과 반환
            assertThat(evaluations).hasSize(2)
            assertThat(evaluations[0].relevanceScore).isEqualTo(90)
            assertThat(evaluations[0].recommendation).isEqualTo(Recommendation.HIGHLY_RECOMMENDED)
            assertThat(evaluations[1].recommendation).isEqualTo(Recommendation.MAYBE)
        }

        @Test
        @DisplayName("빈 후보 목록이 주어지면 빈 결과를 반환한다")
        fun evaluateVideos_WithEmptyCandidates_ReturnsEmptyList() = runBlocking {
            // Given: 빈 후보 목록
            client = createClient()

            // When: 비디오 평가
            val evaluations = client.evaluateVideos(emptyList())

            // Then: 빈 결과
            assertThat(evaluations).isEmpty()
        }
    }

    // ========== Helper Methods ==========

    private fun createClient(): VertexAiLlmClient {
        val client = VertexAiLlmClient(
            projectId = "test-project",
            location = "us-central1",
            modelName = "gemini-1.5-pro",
            objectMapper = objectMapper
        )
        client.init()
        return client
    }

    private fun createMockResponse(text: String): GenerateContentResponse {
        val part = Part.newBuilder().setText(text).build()
        val content = Content.newBuilder().addParts(part).build()
        val candidate = Candidate.newBuilder().setContent(content).build()
        return GenerateContentResponse.newBuilder().addCandidates(candidate).build()
    }
}
