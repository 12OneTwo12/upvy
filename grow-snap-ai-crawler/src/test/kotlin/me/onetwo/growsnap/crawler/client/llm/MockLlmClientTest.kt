package me.onetwo.growsnap.crawler.client.llm

import kotlinx.coroutines.runBlocking
import me.onetwo.growsnap.crawler.domain.Recommendation
import me.onetwo.growsnap.crawler.domain.SearchContext
import me.onetwo.growsnap.crawler.domain.VideoCandidate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MockLlmClient 테스트")
class MockLlmClientTest {

    private lateinit var client: MockLlmClient

    @BeforeEach
    fun setUp() {
        client = MockLlmClient()
    }

    @Nested
    @DisplayName("generateSearchQueries - AI 검색 쿼리 생성")
    inner class GenerateSearchQueries {

        @Test
        @DisplayName("검색 컨텍스트가 주어지면 검색 쿼리 목록을 반환한다")
        fun generateSearchQueries_WithContext_ReturnsQueries() = runBlocking {
            // Given: 검색 컨텍스트
            val context = SearchContext(
                appCategories = listOf("프로그래밍", "과학"),
                popularKeywords = listOf("AI", "코틀린"),
                topPerformingTags = listOf("꿀팁", "초보자"),
                seasonalContext = "새해 목표",
                recentlyPublished = listOf("이전 콘텐츠"),
                underrepresentedCategories = listOf("역사", "예술")
            )

            // When: 검색 쿼리 생성
            val queries = client.generateSearchQueries(context)

            // Then: 쿼리 목록 반환
            assertThat(queries).isNotEmpty
            assertThat(queries.first().query).isNotBlank()
            assertThat(queries.first().targetCategory).isNotBlank()
            assertThat(queries.first().priority).isGreaterThan(0)
        }
    }

    @Nested
    @DisplayName("evaluateVideos - 비디오 사전 평가")
    inner class EvaluateVideos {

        @Test
        @DisplayName("비디오 후보 목록이 주어지면 평가 결과를 반환한다")
        fun evaluateVideos_WithCandidates_ReturnsEvaluations() = runBlocking {
            // Given: 비디오 후보 목록
            val candidates = listOf(
                VideoCandidate(
                    videoId = "vid1",
                    title = "Kotlin Tutorial",
                    channelId = "ch1"
                ),
                VideoCandidate(
                    videoId = "vid2",
                    title = "Python Basics",
                    channelId = "ch2"
                )
            )

            // When: 비디오 평가
            val evaluations = client.evaluateVideos(candidates)

            // Then: 평가 결과 반환
            assertThat(evaluations).hasSize(2)
            evaluations.forEach { evaluation ->
                assertThat(evaluation.relevanceScore).isGreaterThan(0)
                assertThat(evaluation.educationalValue).isGreaterThan(0)
                assertThat(evaluation.recommendation).isEqualTo(Recommendation.RECOMMENDED)
                assertThat(evaluation.reasoning).isNotBlank()
            }
        }

        @Test
        @DisplayName("빈 후보 목록이 주어지면 빈 결과를 반환한다")
        fun evaluateVideos_WithEmptyCandidates_ReturnsEmptyList() = runBlocking {
            // Given: 빈 후보 목록
            val candidates = emptyList<VideoCandidate>()

            // When: 비디오 평가
            val evaluations = client.evaluateVideos(candidates)

            // Then: 빈 결과
            assertThat(evaluations).isEmpty()
        }
    }

    @Nested
    @DisplayName("extractKeySegments - 핵심 세그먼트 추출")
    inner class ExtractKeySegments {

        @Test
        @DisplayName("자막이 주어지면 세그먼트 목록을 반환한다")
        fun extractKeySegments_WithTranscript_ReturnsSegments() = runBlocking {
            // Given: 자막 텍스트
            val transcript = "프로그래밍의 기초에 대해 설명합니다. 변수, 함수, 조건문을 다룹니다."

            // When: 세그먼트 추출
            val segments = client.extractKeySegments(transcript)

            // Then: 세그먼트 반환
            assertThat(segments).isNotEmpty
            segments.forEach { segment ->
                assertThat(segment.startTimeMs).isGreaterThanOrEqualTo(0)
                assertThat(segment.endTimeMs).isGreaterThan(segment.startTimeMs)
                assertThat(segment.title).isNotBlank()
            }
        }
    }

    @Nested
    @DisplayName("generateMetadata - 메타데이터 생성")
    inner class GenerateMetadata {

        @Test
        @DisplayName("콘텐츠가 주어지면 메타데이터를 반환한다")
        fun generateMetadata_WithContent_ReturnsMetadata() = runBlocking {
            // Given: 콘텐츠
            val content = "프로그래밍 입문자를 위한 기초 강좌입니다."

            // When: 메타데이터 생성
            val metadata = client.generateMetadata(content)

            // Then: 메타데이터 반환
            assertThat(metadata.title).isNotBlank()
            assertThat(metadata.description).isNotBlank()
            assertThat(metadata.tags).isNotEmpty
            assertThat(metadata.category).isNotBlank()
            assertThat(metadata.difficulty).isNotNull
        }
    }
}
