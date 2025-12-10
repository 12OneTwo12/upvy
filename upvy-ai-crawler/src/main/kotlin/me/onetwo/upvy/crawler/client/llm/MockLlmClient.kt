package me.onetwo.upvy.crawler.client.llm

import me.onetwo.upvy.crawler.client.LlmClient
import me.onetwo.upvy.crawler.domain.ClipSegment
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.ContentMetadata
import me.onetwo.upvy.crawler.domain.Difficulty
import me.onetwo.upvy.crawler.domain.EditPlan
import me.onetwo.upvy.crawler.domain.EvaluatedVideo
import me.onetwo.upvy.crawler.domain.Recommendation
import me.onetwo.upvy.crawler.domain.SearchContext
import me.onetwo.upvy.crawler.domain.SearchQuery
import me.onetwo.upvy.crawler.domain.Segment
import me.onetwo.upvy.crawler.domain.VideoCandidate
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * 테스트용 Mock LLM 클라이언트
 *
 * 테스트 환경에서 실제 AI API 호출 없이 동작을 시뮬레이션합니다.
 * application-test.yml에서 ai.llm.provider=mock으로 설정하면 활성화됩니다.
 */
@Component
@ConditionalOnProperty(name = ["ai.llm.provider"], havingValue = "mock")
class MockLlmClient : LlmClient {

    companion object {
        private val logger = LoggerFactory.getLogger(MockLlmClient::class.java)
    }

    var analyzeResponse: String = "Mock analysis response"
    var segmentsResponse: List<Segment> = listOf(
        Segment(
            startTimeMs = 60000,
            endTimeMs = 120000,
            title = "Mock Segment 1",
            description = "This is a mock segment for testing",
            keywords = listOf("mock", "test", "segment")
        )
    )
    var metadataResponse: ContentMetadata = ContentMetadata(
        title = "Mock Title",
        description = "Mock Description for testing purposes",
        tags = listOf("mock", "test"),
        category = "PROGRAMMING",
        difficulty = Difficulty.BEGINNER
    )
    var searchQueriesResponse: List<SearchQuery> = listOf(
        SearchQuery(
            query = "productivity tips for developers",
            targetCategory = "PRODUCTIVITY",
            expectedContentType = "tutorial",
            priority = 10
        ),
        SearchQuery(
            query = "kotlin programming best practices",
            targetCategory = "PROGRAMMING",
            expectedContentType = "educational",
            priority = 9
        )
    )

    override suspend fun analyze(prompt: String): String {
        logger.debug("MockLlmClient.analyze called: prompt length={}", prompt.length)
        return analyzeResponse
    }

    override suspend fun extractKeySegments(transcript: String): List<Segment> {
        logger.debug("MockLlmClient.extractKeySegments called: transcript length={}", transcript.length)
        return segmentsResponse
    }

    override suspend fun generateEditPlan(transcript: String): EditPlan {
        logger.debug("MockLlmClient.generateEditPlan called: transcript length={}", transcript.length)
        // Mock: 2개 클립으로 구성된 편집 계획 반환
        return EditPlan(
            clips = listOf(
                ClipSegment(
                    orderIndex = 0,
                    startTimeMs = 30000,  // 30초
                    endTimeMs = 60000,    // 60초
                    title = "Mock Clip 1: 도입부",
                    description = "테스트용 첫 번째 클립",
                    keywords = listOf("intro", "mock")
                ),
                ClipSegment(
                    orderIndex = 1,
                    startTimeMs = 90000,  // 90초
                    endTimeMs = 120000,   // 120초
                    title = "Mock Clip 2: 핵심 내용",
                    description = "테스트용 두 번째 클립",
                    keywords = listOf("main", "mock")
                )
            ),
            totalDurationMs = 60000,  // 총 60초 (30초 + 30초)
            editingStrategy = "highlight_compilation",
            transitionStyle = "hard_cut"
        )
    }

    override suspend fun generateMetadata(content: String, language: ContentLanguage): ContentMetadata {
        logger.debug("MockLlmClient.generateMetadata called: content length={}, language={}",
            content.length, language.code)
        return when (language) {
            ContentLanguage.KO -> metadataResponse
            ContentLanguage.EN -> ContentMetadata(
                title = "Mock Title in English",
                description = "Mock Description for testing purposes in English",
                tags = listOf("mock", "test", "english"),
                category = "PROGRAMMING",
                difficulty = Difficulty.BEGINNER
            )
            ContentLanguage.JA -> ContentMetadata(
                title = "モックタイトル",
                description = "テスト用のモック説明",
                tags = listOf("モック", "テスト", "日本語"),
                category = "PROGRAMMING",
                difficulty = Difficulty.BEGINNER
            )
        }
    }

    override suspend fun generateSearchQueries(context: SearchContext): List<SearchQuery> {
        logger.debug(
            "MockLlmClient.generateSearchQueries called: categories={}, underrepresented={}",
            context.appCategories,
            context.underrepresentedCategories
        )
        return searchQueriesResponse
    }

    override suspend fun evaluateVideos(candidates: List<VideoCandidate>): List<EvaluatedVideo> {
        logger.debug("MockLlmClient.evaluateVideos called: candidates count={}", candidates.size)
        return candidates.map { candidate ->
            EvaluatedVideo(
                candidate = candidate,
                relevanceScore = 85,
                educationalValue = 80,
                shortFormSuitability = 75,
                predictedQuality = 82,
                recommendation = Recommendation.RECOMMENDED,
                reasoning = "Mock evaluation: Good educational content"
            )
        }
    }
}
