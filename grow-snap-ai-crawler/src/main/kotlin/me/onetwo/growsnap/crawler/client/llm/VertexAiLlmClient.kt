package me.onetwo.growsnap.crawler.client.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.GenerationConfig
import com.google.cloud.vertexai.generativeai.GenerativeModel
import com.google.cloud.vertexai.generativeai.ResponseHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.onetwo.growsnap.crawler.client.LlmClient
import me.onetwo.growsnap.crawler.domain.ContentMetadata
import me.onetwo.growsnap.crawler.domain.Difficulty
import me.onetwo.growsnap.crawler.domain.EvaluatedVideo
import me.onetwo.growsnap.crawler.domain.Recommendation
import me.onetwo.growsnap.crawler.domain.SearchContext
import me.onetwo.growsnap.crawler.domain.SearchQuery
import me.onetwo.growsnap.crawler.domain.Segment
import me.onetwo.growsnap.crawler.domain.VideoCandidate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

/**
 * Vertex AI Gemini LLM 클라이언트 구현체
 *
 * Google Cloud Vertex AI의 Gemini 모델을 사용하여
 * 텍스트 분석, 세그먼트 추출, 메타데이터 생성 등을 수행합니다.
 */
@Component
@ConditionalOnProperty(name = ["ai.llm.provider"], havingValue = "vertex-ai")
class VertexAiLlmClient(
    @Value("\${ai.llm.project-id}") private val projectId: String,
    @Value("\${ai.llm.location:us-central1}") private val location: String,
    @Value("\${ai.llm.model:gemini-1.5-pro}") private val modelName: String,
    private val objectMapper: ObjectMapper
) : LlmClient {

    companion object {
        private val logger = LoggerFactory.getLogger(VertexAiLlmClient::class.java)
        private const val MAX_OUTPUT_TOKENS = 8192
        private const val TEMPERATURE = 0.2f
    }

    private lateinit var vertexAi: VertexAI
    private lateinit var model: GenerativeModel

    @PostConstruct
    fun init() {
        // projectId 검증
        require(projectId.isNotBlank()) {
            "GCP_PROJECT_ID 환경변수가 설정되지 않았습니다. Vertex AI를 사용하려면 GCP_PROJECT_ID와 GOOGLE_APPLICATION_CREDENTIALS를 설정하세요."
        }

        // 인증 방식 안내 (GOOGLE_APPLICATION_CREDENTIALS, gcloud CLI, 또는 메타데이터 서버)
        val credentialsEnv = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        if (credentialsEnv.isNullOrBlank()) {
            logger.info("GOOGLE_APPLICATION_CREDENTIALS 미설정. gcloud auth application-default login 또는 메타데이터 서버를 통해 인증합니다.")
        } else {
            logger.info("GOOGLE_APPLICATION_CREDENTIALS를 통해 인증합니다: {}", credentialsEnv)
        }

        logger.info("Vertex AI LLM 클라이언트 초기화: projectId={}, location={}, model={}",
            projectId, location, modelName)

        vertexAi = VertexAI(projectId, location)
        val generationConfig = GenerationConfig.newBuilder()
            .setMaxOutputTokens(MAX_OUTPUT_TOKENS)
            .setTemperature(TEMPERATURE)
            .build()

        model = GenerativeModel(modelName, vertexAi)
            .withGenerationConfig(generationConfig)
    }

    @PreDestroy
    fun destroy() {
        vertexAi.close()
    }

    override suspend fun analyze(prompt: String): String = withContext(Dispatchers.IO) {
        logger.debug("Gemini 분석 요청: prompt length={}", prompt.length)

        try {
            val response = model.generateContent(prompt)
            val result = ResponseHandler.getText(response)

            logger.debug("Gemini 분석 완료: response length={}", result.length)
            result

        } catch (e: Exception) {
            logger.error("Gemini 분석 실패", e)
            throw LlmException("Failed to analyze with Gemini", e)
        }
    }

    override suspend fun extractKeySegments(transcript: String): List<Segment> = withContext(Dispatchers.IO) {
        logger.info("핵심 세그먼트 추출 시작: transcript length={}", transcript.length)

        val prompt = buildSegmentExtractionPrompt(transcript)

        try {
            val response = model.generateContent(prompt)
            val responseText = ResponseHandler.getText(response)
            val jsonResponse = extractJsonFromResponse(responseText.ifEmpty { "[]" })

            val segments = parseSegmentsFromJson(jsonResponse)

            logger.info("핵심 세그먼트 추출 완료: count={}", segments.size)
            segments

        } catch (e: Exception) {
            logger.error("핵심 세그먼트 추출 실패", e)
            throw LlmException("Failed to extract key segments", e)
        }
    }

    override suspend fun generateMetadata(content: String): ContentMetadata = withContext(Dispatchers.IO) {
        logger.info("메타데이터 생성 시작: content length={}", content.length)

        val prompt = buildMetadataGenerationPrompt(content)

        try {
            val response = model.generateContent(prompt)
            val responseText = ResponseHandler.getText(response)
            val jsonResponse = extractJsonFromResponse(responseText.ifEmpty { "{}" })

            val metadata = parseMetadataFromJson(jsonResponse)

            logger.info("메타데이터 생성 완료: title={}", metadata.title)
            metadata

        } catch (e: Exception) {
            logger.error("메타데이터 생성 실패", e)
            throw LlmException("Failed to generate metadata", e)
        }
    }

    override suspend fun generateSearchQueries(context: SearchContext): List<SearchQuery> = withContext(Dispatchers.IO) {
        logger.info("검색 쿼리 생성 시작: categories={}", context.appCategories)

        val prompt = buildSearchQueryPrompt(context)

        try {
            val response = model.generateContent(prompt)
            val responseText = ResponseHandler.getText(response)
            val jsonResponse = extractJsonFromResponse(responseText.ifEmpty { "[]" })

            val queries = parseSearchQueriesFromJson(jsonResponse)

            logger.info("검색 쿼리 생성 완료: count={}", queries.size)
            queries

        } catch (e: Exception) {
            logger.error("검색 쿼리 생성 실패", e)
            throw LlmException("Failed to generate search queries", e)
        }
    }

    override suspend fun evaluateVideos(candidates: List<VideoCandidate>): List<EvaluatedVideo> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()

        logger.info("비디오 평가 시작: candidates count={}", candidates.size)

        val prompt = buildVideoEvaluationPrompt(candidates)

        try {
            val response = model.generateContent(prompt)
            val responseText = ResponseHandler.getText(response)
            val jsonResponse = extractJsonFromResponse(responseText.ifEmpty { "[]" })

            val evaluations = parseEvaluationsFromJson(jsonResponse, candidates)

            logger.info("비디오 평가 완료: count={}", evaluations.size)
            evaluations

        } catch (e: Exception) {
            logger.error("비디오 평가 실패", e)
            throw LlmException("Failed to evaluate videos", e)
        }
    }

    // ========== Prompt Builders ==========

    private fun buildSegmentExtractionPrompt(transcript: String): String = """
        |당신은 교육 콘텐츠 전문가입니다. 다음 영상 자막에서 학습 가치가 높은 핵심 구간을 추출해주세요.
        |
        |자막:
        |$transcript
        |
        |다음 조건을 만족하는 구간을 추출해주세요:
        |1. 각 구간은 30초 ~ 3분 사이
        |2. 완결된 개념이나 주제를 다루는 구간
        |3. 시청자가 쉽게 이해할 수 있는 설명이 포함된 구간
        |4. 최대 5개의 핵심 구간을 추출
        |
        |JSON 형식으로만 응답해주세요:
        |[
        |  {
        |    "startTimeMs": 시작시간(밀리초),
        |    "endTimeMs": 종료시간(밀리초),
        |    "title": "구간 제목",
        |    "description": "구간 설명",
        |    "keywords": ["키워드1", "키워드2"]
        |  }
        |]
    """.trimMargin()

    private fun buildMetadataGenerationPrompt(content: String): String = """
        |당신은 교육 콘텐츠 메타데이터 생성 전문가입니다. 다음 콘텐츠에 대한 메타데이터를 생성해주세요.
        |
        |콘텐츠:
        |$content
        |
        |다음 형식의 JSON으로 응답해주세요:
        |{
        |  "title": "매력적이고 명확한 제목 (30자 이내)",
        |  "description": "SEO에 최적화된 상세 설명 (200자 이내)",
        |  "tags": ["관련 태그", "최대 10개"],
        |  "category": "PROGRAMMING|SCIENCE|LANGUAGE|LIFESTYLE|BUSINESS|HEALTH|ARTS|HISTORY",
        |  "difficulty": "BEGINNER|INTERMEDIATE|ADVANCED"
        |}
    """.trimMargin()

    private fun buildSearchQueryPrompt(context: SearchContext): String = """
        |당신은 교육 콘텐츠 큐레이션 전문가입니다. YouTube에서 양질의 CC 라이선스 교육 콘텐츠를 찾기 위한 검색 쿼리를 생성해주세요.
        |
        |현재 앱 카테고리: ${context.appCategories.joinToString(", ")}
        |인기 키워드: ${context.popularKeywords.joinToString(", ")}
        |최근 잘 된 태그: ${context.topPerformingTags.joinToString(", ")}
        |시즌 컨텍스트: ${context.seasonalContext ?: "없음"}
        |부족한 카테고리: ${context.underrepresentedCategories.joinToString(", ")}
        |최근 게시된 콘텐츠: ${context.recentlyPublished.joinToString(", ")}
        |
        |다음 조건을 고려해주세요:
        |1. 교육적 가치가 높은 콘텐츠
        |2. CC 라이선스로 배포될 가능성이 높은 채널/콘텐츠
        |3. 짧은 클립으로 편집하기 좋은 콘텐츠
        |4. 부족한 카테고리를 우선적으로 채울 수 있는 쿼리
        |
        |JSON 형식으로 5~10개의 검색 쿼리를 생성해주세요:
        |[
        |  {
        |    "query": "검색어",
        |    "targetCategory": "카테고리",
        |    "expectedContentType": "tutorial|lecture|explanation|howto",
        |    "priority": 1-10
        |  }
        |]
    """.trimMargin()

    private fun buildVideoEvaluationPrompt(candidates: List<VideoCandidate>): String {
        val videosJson = candidates.mapIndexed { index, video ->
            """{"index": $index, "title": "${video.title}", "description": "${video.description?.take(200) ?: ""}", "channelTitle": "${video.channelTitle ?: ""}", "viewCount": ${video.viewCount ?: 0}}"""
        }.joinToString(",\n")

        return """
            |당신은 교육 콘텐츠 품질 평가 전문가입니다. 다음 YouTube 비디오들의 교육적 가치를 평가해주세요.
            |
            |비디오 목록:
            |[$videosJson]
            |
            |각 비디오에 대해 다음 기준으로 평가해주세요:
            |1. relevanceScore (0-100): 교육 플랫폼과의 관련성
            |2. educationalValue (0-100): 학습 가치
            |3. predictedQuality (0-100): 예상 품질
            |4. recommendation: HIGHLY_RECOMMENDED, RECOMMENDED, MAYBE, SKIP
            |5. reasoning: 평가 이유 (한 문장)
            |
            |JSON 형식으로 응답해주세요:
            |[
            |  {
            |    "index": 0,
            |    "relevanceScore": 85,
            |    "educationalValue": 80,
            |    "predictedQuality": 82,
            |    "recommendation": "RECOMMENDED",
            |    "reasoning": "평가 이유"
            |  }
            |]
        """.trimMargin()
    }

    // ========== JSON Parsers ==========

    private fun extractJsonFromResponse(response: String): String {
        // Markdown 코드 블록에서 JSON 추출
        val jsonPattern = """```(?:json)?\s*([\s\S]*?)```""".toRegex()
        val match = jsonPattern.find(response)
        return match?.groupValues?.get(1)?.trim() ?: response.trim()
    }

    private fun parseSegmentsFromJson(json: String): List<Segment> {
        return try {
            val segments: List<Map<String, Any>> = objectMapper.readValue(json)
            segments.map { seg ->
                Segment(
                    startTimeMs = (seg["startTimeMs"] as Number).toLong(),
                    endTimeMs = (seg["endTimeMs"] as Number).toLong(),
                    title = seg["title"] as String,
                    description = seg["description"] as? String,
                    keywords = (seg["keywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            }
        } catch (e: Exception) {
            logger.warn("세그먼트 JSON 파싱 실패: {}", e.message)
            emptyList()
        }
    }

    private fun parseMetadataFromJson(json: String): ContentMetadata {
        return try {
            val metadata: Map<String, Any> = objectMapper.readValue(json)
            ContentMetadata(
                title = metadata["title"] as String,
                description = metadata["description"] as String,
                tags = (metadata["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                category = metadata["category"] as String,
                difficulty = Difficulty.valueOf(metadata["difficulty"] as String)
            )
        } catch (e: Exception) {
            logger.warn("메타데이터 JSON 파싱 실패: {}", e.message)
            ContentMetadata(
                title = "Untitled",
                description = "",
                tags = emptyList(),
                category = "PROGRAMMING",
                difficulty = Difficulty.BEGINNER
            )
        }
    }

    private fun parseSearchQueriesFromJson(json: String): List<SearchQuery> {
        return try {
            val queries: List<Map<String, Any>> = objectMapper.readValue(json)
            queries.map { q ->
                SearchQuery(
                    query = q["query"] as String,
                    targetCategory = q["targetCategory"] as String,
                    expectedContentType = q["expectedContentType"] as String,
                    priority = (q["priority"] as Number).toInt()
                )
            }
        } catch (e: Exception) {
            logger.warn("검색 쿼리 JSON 파싱 실패: {}", e.message)
            emptyList()
        }
    }

    private fun parseEvaluationsFromJson(json: String, candidates: List<VideoCandidate>): List<EvaluatedVideo> {
        return try {
            val evaluations: List<Map<String, Any>> = objectMapper.readValue(json)
            evaluations.mapNotNull { eval ->
                val index = (eval["index"] as Number).toInt()
                val candidate = candidates.getOrNull(index) ?: return@mapNotNull null

                EvaluatedVideo(
                    candidate = candidate,
                    relevanceScore = (eval["relevanceScore"] as Number).toInt(),
                    educationalValue = (eval["educationalValue"] as Number).toInt(),
                    predictedQuality = (eval["predictedQuality"] as Number).toInt(),
                    recommendation = Recommendation.valueOf(eval["recommendation"] as String),
                    reasoning = eval["reasoning"] as String
                )
            }
        } catch (e: Exception) {
            logger.warn("평가 JSON 파싱 실패: {}", e.message)
            // 파싱 실패 시 기본값으로 모든 비디오를 MAYBE로 처리
            candidates.map { candidate ->
                EvaluatedVideo(
                    candidate = candidate,
                    relevanceScore = 50,
                    educationalValue = 50,
                    predictedQuality = 50,
                    recommendation = Recommendation.MAYBE,
                    reasoning = "평가 파싱 실패로 기본값 사용"
                )
            }
        }
    }
}

/**
 * LLM 예외
 */
class LlmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
