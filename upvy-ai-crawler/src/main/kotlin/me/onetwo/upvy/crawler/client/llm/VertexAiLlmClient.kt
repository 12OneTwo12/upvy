package me.onetwo.upvy.crawler.client.llm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.cloud.vertexai.VertexAI
import com.google.cloud.vertexai.api.GenerationConfig
import com.google.cloud.vertexai.generativeai.GenerativeModel
import com.google.cloud.vertexai.generativeai.ResponseHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.onetwo.upvy.crawler.backoffice.domain.Category
import me.onetwo.upvy.crawler.client.LlmClient
import me.onetwo.upvy.crawler.domain.ClipSegment
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.ContentMetadata
import me.onetwo.upvy.crawler.domain.Difficulty
import me.onetwo.upvy.crawler.domain.EditPlan
import me.onetwo.upvy.crawler.domain.EvaluatedVideo
import me.onetwo.upvy.crawler.domain.QuizData
import me.onetwo.upvy.crawler.domain.QuizOption
import me.onetwo.upvy.crawler.domain.Recommendation
import me.onetwo.upvy.crawler.domain.SearchContext
import me.onetwo.upvy.crawler.domain.SearchQuery
import me.onetwo.upvy.crawler.domain.Segment
import me.onetwo.upvy.crawler.domain.VideoCandidate
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
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

    override suspend fun generateEditPlan(transcript: String): EditPlan = withContext(Dispatchers.IO) {
        logger.info("편집 계획 생성 시작: transcript length={}", transcript.length)

        val prompt = buildEditPlanPrompt(transcript)

        try {
            val response = model.generateContent(prompt)
            val responseText = ResponseHandler.getText(response)
            val jsonResponse = extractJsonFromResponse(responseText.ifEmpty { "{}" })

            val editPlan = parseEditPlanFromJson(jsonResponse)

            logger.info("편집 계획 생성 완료: clips={}, strategy={}", editPlan.clips.size, editPlan.editingStrategy)
            editPlan

        } catch (e: Exception) {
            logger.error("편집 계획 생성 실패", e)
            throw LlmException("Failed to generate edit plan", e)
        }
    }

    override suspend fun generateMetadata(
        content: String,
        language: ContentLanguage
    ): ContentMetadata = withContext(Dispatchers.IO) {
        logger.info("메타데이터 생성 시작: content length={}, language={}", content.length, language.code)

        val prompt = buildMetadataGenerationPrompt(content, language)

        try {
            val response = model.generateContent(prompt)
            val responseText = ResponseHandler.getText(response)
            val jsonResponse = extractJsonFromResponse(responseText.ifEmpty { "{}" })

            val metadata = parseMetadataFromJson(jsonResponse)

            logger.info("메타데이터 생성 완료: title={}, language={}", metadata.title, language.code)
            metadata

        } catch (e: Exception) {
            logger.error("메타데이터 생성 실패: language={}", language.code, e)
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

        // 배치 크기: 토큰 제한을 피하기 위해 10개씩 처리
        val batchSize = 10
        val allEvaluations = mutableListOf<EvaluatedVideo>()

        candidates.chunked(batchSize).forEachIndexed { batchIndex, batch ->
            logger.info("배치 {} 평가 시작: {} ~ {} (총 {}개)",
                batchIndex + 1,
                batchIndex * batchSize,
                batchIndex * batchSize + batch.size - 1,
                batch.size)

            val prompt = buildVideoEvaluationPrompt(batch)
            logger.debug("비디오 평가 프롬프트 길이: {} chars", prompt.length)

            try {
                val response = model.generateContent(prompt)
                logger.debug("Gemini 응답 수신 완료")

                // 응답 상세 정보 로깅
                val responseCandidates = response.candidatesList
                if (responseCandidates.isNotEmpty()) {
                    val candidate = responseCandidates[0]
                    logger.debug("finishReason: {}, content parts: {}",
                        candidate.finishReason,
                        candidate.content?.partsList?.size ?: 0)
                }

                val responseText = ResponseHandler.getText(response)
                logger.debug("Gemini 원본 응답 길이: {} chars", responseText.length)

                if (responseText.isNotEmpty()) {
                    val jsonResponse = extractJsonFromResponse(responseText)
                    val evaluations = parseEvaluationsFromJson(jsonResponse, batch)
                    allEvaluations.addAll(evaluations)
                    logger.info("배치 {} 평가 완료: {}개 평가됨", batchIndex + 1, evaluations.size)
                } else {
                    logger.warn("배치 {} 평가 실패: 빈 응답", batchIndex + 1)
                }

            } catch (e: Exception) {
                logger.error("배치 {} 평가 실패", batchIndex + 1, e)
                // 개별 배치 실패는 무시하고 계속 진행
            }
        }

        logger.info("비디오 평가 완료: 전체 {}개 중 {}개 평가됨", candidates.size, allEvaluations.size)
        allEvaluations
    }

    override suspend fun generateQuizFromDescription(
        description: String,
        title: String,
        contentLanguage: ContentLanguage,
        difficulty: Difficulty?
    ): QuizData = withContext(Dispatchers.IO) {
        logger.info("퀴즈 생성 시작: title={}, language={}, difficulty={}",
            title, contentLanguage.code, difficulty?.name ?: "N/A")

        val prompt = buildQuizGenerationPrompt(description, title, contentLanguage, difficulty)

        try {
            val response = model.generateContent(prompt)
            val responseText = ResponseHandler.getText(response)
            val jsonResponse = extractJsonFromResponse(responseText.ifEmpty { "{}" })

            val quiz = parseQuizFromJson(jsonResponse)

            logger.info("퀴즈 생성 완료: question={}, options count={}",
                quiz.question.take(50), quiz.options.size)
            quiz

        } catch (e: Exception) {
            logger.error("퀴즈 생성 실패: title={}", title, e)
            throw LlmException("Failed to generate quiz", e)
        }
    }

    // ========== Prompt Builders ==========

    private fun buildSegmentExtractionPrompt(transcript: String): String = """
        |당신은 교육 숏폼 콘텐츠 전문가입니다. 다음 영상 자막에서 숏폼 콘텐츠로 가장 적합한 핵심 구간을 추출해주세요.
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
        |숏폼 적합성 기준 (중요!):
        |- 빠른 템포와 밀도 있는 설명 (루즈하거나 늘어지지 않는 구간)
        |- 화자의 에너지가 높고 말하는 속도가 적당히 빠른 부분
        |- 핵심 내용이 압축적으로 전달되는 구간
        |- 시각적/청각적 변화가 있어 지루하지 않은 부분
        |- 30초~1분 내에 하나의 완결된 인사이트를 전달하는 구간
        |
        |피해야 할 구간:
        |- 인트로/아웃트로 (구독 요청, 인사 등)
        |- 장황한 설명이나 반복적인 내용
        |- 맥락 없이는 이해하기 어려운 부분
        |
        |가장 숏폼에 적합한 구간부터 순서대로 정렬해주세요.
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

    private fun buildEditPlanPrompt(transcript: String): String = """
        |당신은 교육 숏폼 콘텐츠 편집 전문가입니다. 다음 영상 자막을 분석하여 최적의 숏폼 편집 계획을 제시해주세요.
        |
        |자막:
        |$transcript
        |
        |임무: 이 영상에서 여러 구간을 선택하고 조합하여 하나의 완성도 높은 숏폼(30초~3분)을 만드는 편집 계획을 세우세요.
        |
        |편집 계획 수립 기준:
        |1. **전체 스토리 플로우**: 각 클립이 어떤 순서로 배치되어야 시청자가 자연스럽게 이해할 수 있는가?
        |2. **완결성**: 도입-전개-결론 구조를 갖추거나, 하나의 완결된 개념을 전달하는가?
        |3. **숏폼 최적화**:
        |   - 빠른 템포: 루즈하거나 늘어지는 구간 제외
        |   - 밀도 있는 내용: 30초 내에 하나의 인사이트 전달
        |   - 에너지: 화자의 말하는 속도와 에너지가 높은 부분 우선
        |4. **편집 효율**: 2~5개의 클립을 선택 (너무 많으면 산만함)
        |
        |클립 선택 가이드:
        |- 각 클립은 15초~60초 사이 (최소 10초, 최대 90초)
        |- 인트로/아웃트로, 구독 요청 등은 제외
        |- 문맥 없이 이해 가능한 구간 선택
        |- 시각적/청각적 변화가 있는 부분 우선
        |- 재미와 흥미를 동반한 교육적 가치가 높은 구간 선택 (ex. 재미, 흥미로운 역사, 과학 이야기)
        |
        |편집 전략 (editingStrategy):
        |- "highlight_compilation": 가장 핵심적인 하이라이트 여러 개를 이어붙이기
        |- "story_flow": 순차적으로 이어지는 스토리 만들기
        |- "tutorial_sequence": 튜토리얼 단계를 압축하여 구성
        |- "problem_solution": 문제 제시 → 해결 과정 → 결론
        |- "video_time": 클립 전체 합계, 편집 후 풀 영상이 최소 30초 이상 최대 3분이 되도록 클립 선택
        |
        |JSON 형식으로만 응답해주세요:
        |{
        |  "clips": [
        |    {
        |      "orderIndex": 0,
        |      "startTimeMs": 시작시간(밀리초),
        |      "endTimeMs": 종료시간(밀리초),
        |      "title": "클립 제목",
        |      "description": "이 클립을 선택한 이유",
        |      "keywords": ["키워드1", "키워드2"]
        |    }
        |  ],
        |  "totalDurationMs": 전체_클립_길이_합계,
        |  "editingStrategy": "highlight_compilation|story_flow|tutorial_sequence|problem_solution",
        |  "transitionStyle": "hard_cut"
        |}
    """.trimMargin()

    private fun buildMetadataGenerationPrompt(content: String, language: ContentLanguage): String {
        val languageInstruction = when (language) {
            ContentLanguage.KO -> "한국어로 메타데이터를 생성해주세요."
            ContentLanguage.EN -> "Generate metadata in English."
            ContentLanguage.JA -> "日本語でメタデータを生成してください。"
        }

        val titleExample = when (language) {
            ContentLanguage.KO -> "코틀린 입문자를 위한 핵심 가이드"
            ContentLanguage.EN -> "Essential Kotlin Guide for Beginners"
            ContentLanguage.JA -> "初心者のためのKotlin入門ガイド"
        }

        // Category enum에서 동적으로 카테고리 목록 생성
        val availableCategories = Category.entries
            .joinToString("|") { it.name }

        // Difficulty enum에서 동적으로 난이도 목록 생성
        val availableDifficulties = Difficulty.entries
            .joinToString("|") { it.name }

        return """
        |당신은 글로벌 교육 콘텐츠 메타데이터 생성 전문가입니다.
        |
        |**중요: $languageInstruction**
        |타겟 언어: ${language.nativeName} (${language.code})
        |
        |다음 콘텐츠에 대한 메타데이터를 생성해주세요:
        |$content
        |
        |다음 형식의 JSON으로 응답해주세요 (${language.nativeName}로 작성):
        |{
        |  "title": "$titleExample (30자/단어 이내)",
        |  "description": "SEO에 최적화된 상세 설명 (200자/단어 이내)",
        |  "tags": ["관련 태그", "최대 10개 - ${language.nativeName}로"],
        |  "category": "$availableCategories",
        |  "difficulty": "$availableDifficulties"
        |}
    """.trimMargin()
    }

    private fun buildSearchQueryPrompt(context: SearchContext): String {
        val languageInfo = context.targetLanguages.joinToString(", ") { "${it.code} (${it.nativeName})" }

        return """
        |당신은 글로벌 교육 콘텐츠 큐레이션 전문가입니다. YouTube에서 양질의 CC 라이선스 교육 콘텐츠를 찾기 위한 검색 쿼리를 생성해주세요.
        |
        |**중요: 다국어 검색 쿼리 생성**
        |타겟 언어: $languageInfo
        |각 언어별로 해당 언어에 맞는 자연스러운 검색어를 생성해주세요.
        |예시:
        |- 한국어(ko): "코틀린 프로그래밍 입문"
        |- 영어(en): "kotlin programming beginner tutorial"
        |- 일본어(ja): "Kotlin プログラミング 入門"
        |- 한국어(ko): "운동 동기부여"
        |- 영어(en): "fitness motivation"
        |- 일본어(ja): "フィットネス モチベーション"
        |
        |현재 앱 카테고리: ${context.appCategories.joinToString(", ")}
        |인기 키워드: ${context.popularKeywords.joinToString(", ")}
        |최근 잘 된 태그: ${context.topPerformingTags.joinToString(", ")}
        |시즌 컨텍스트: ${context.seasonalContext ?: "없음"}
        |부족한 카테고리: ${context.underrepresentedCategories.joinToString(", ")}
        |최근 게시된 콘텐츠: ${context.recentlyPublished.joinToString(", ")}
        |현재 년도: ${LocalDate.now()}
        |
        |**콘텐츠 다양성 원칙 (매우 중요!):**
        |1. **균형잡힌 카테고리 믹스**: Hard Skills(프로그래밍, 재테크, 역사, 한국사, 언어 등)와 Soft Skills(동기부여, 마인드셋, 심리학 등)를 50:50 비율로 균형있게 생성
        |2. 동기부여/자기계발/라이프스타일: 이런 콘텐츠도 중요한 교육 콘텐츠입니다
        |   - 동기부여, 습관 형성, 목표 달성, 생산성, 시간 관리
        |   - 자기계발, 마인드셋, 심리학, 인간관계
        |   - 건강, 운동, 다이어트, 명상
        |   - 재미있는 과학, 재미있는 역사 이야기, 예술, 여행
        |3. **실용적 기술 지식**: 강의, 강좌, 분석, 프로그래밍, 디자인, 마케팅, 재테크, 역사, 언어 등
        |4. **부족한 카테고리 우선**: 위 "부족한 카테고리" 리스트를 최우선으로 채우기
        |5. **시즌 컨텍스트 활용**: 위 시즌 키워드를 적극 반영
        |6. **재미있는 역사, 과학 이야기**: 딱딱한 강의보다 스토리텔링이 가미된 콘텐츠 우선
        |7. **구체적인 검색 쿼리**: 포괄적인 검색어 뿐만 아닌 카테고리에 포함될만한 구체적인 검색 쿼리 위주로 생성 (ex. "죽음이란 무엇인가", "시간 여행의 과학", "심리학으로 본 인간관계", "${LocalDate.now()}년 수능 기출문제 풀이", "아인슈타인의 상대성이론", "양자역학이란?" 등)
        |
        |다음 조건을 고려해주세요:
        |1. 교육적 가치가 높은 콘텐츠 (동기부여/자기계발도 교육적 가치가 높습니다!)
        |2. CC 라이선스로 배포될 가능성이 높은 채널/콘텐츠
        |3. 짧은 클립으로 편집하기 좋은 콘텐츠 (숏츠)
        |4. 각 언어별로 균등하게 쿼리 생성 (각 언어당 최소 3개)
        |5. 교육적 가치가 높으면서 재미까지 있으면 더 좋음
        |6. 너무 한쪽 분야로 치우치지 말고 다양한 주제를 골고루 포함
        |
        |JSON 형식으로 15~30개의 검색 쿼리를 생성해주세요 (언어당 5~10개):
        |[
        |  {
        |    "query": "검색어 (해당 언어로 작성)",
        |    "targetCategory": "카테고리",
        |    "expectedContentType": "tutorial|lecture|explanation|howto|motivation|inspiration",
        |    "priority": 1-10,
        |    "language": "ko|en|ja"
        |  }
        |]
    """.trimMargin()
    }

    private fun buildVideoEvaluationPrompt(candidates: List<VideoCandidate>): String {
        // objectMapper를 사용하여 안전하게 JSON 생성 (특수 문자 자동 이스케이핑)
        val videosJson = objectMapper.writeValueAsString(candidates.mapIndexed { index, video ->
            mapOf(
                "index" to index,
                "title" to video.title,
                "description" to (video.description?.take(200) ?: ""),
                "channelTitle" to (video.channelTitle ?: ""),
                "viewCount" to (video.viewCount ?: 0)
            )
        })

        return """
            |당신은 교육 콘텐츠 품질 평가 전문가입니다. 다음 YouTube 비디오들의 교육적 가치를 평가해주세요.
            |
            |비디오 목록:
            |$videosJson
            |
            |각 비디오에 대해 다음 기준으로 평가해주세요:
            |1. relevanceScore (0-100): 교육 플랫폼과의 관련성
            |2. educationalValue (0-100): 학습 가치
            |3. shortFormSuitability (0-100): 숏폼 콘텐츠 적합성
            |   - 빠른 템포와 밀도 있는 편집 (루즈하지 않은지)
            |   - 핵심 내용이 압축적으로 전달되는지
            |   - 시각적 변화가 빈번하고 역동적인지
            |   - 화자의 말하는 속도와 에너지
            |   - 재미, 흥미 요소가 포함되어 있는지
            |   - 30초~3분 클립으로 잘라도 완결성이 있는 구조인지
            |4. predictedQuality (0-100): 예상 품질
            |5. recommendation: HIGHLY_RECOMMENDED, RECOMMENDED, MAYBE, SKIP
            |6. reasoning: 평가 이유 (한 문장)
            |
            |JSON 형식으로 응답해주세요:
            |[
            |  {
            |    "index": 0,
            |    "relevanceScore": 85,
            |    "educationalValue": 80,
            |    "shortFormSuitability": 75,
            |    "predictedQuality": 82,
            |    "recommendation": "RECOMMENDED",
            |    "reasoning": "평가 이유"
            |  }
            |]
        """.trimMargin()
    }

    private fun buildQuizGenerationPrompt(
        description: String,
        title: String,
        contentLanguage: ContentLanguage,
        difficulty: Difficulty?
    ): String {
        val languageInstruction = when (contentLanguage) {
            ContentLanguage.KO -> "한국어로 퀴즈를 생성해주세요."
            ContentLanguage.EN -> "Generate the quiz in English."
            ContentLanguage.JA -> "日本語でクイズを生成してください。"
        }

        val difficultyGuidance = when (difficulty) {
            Difficulty.BEGINNER -> when (contentLanguage) {
                ContentLanguage.KO -> "초급 수준: 기본 개념을 묻는 쉬운 문제"
                ContentLanguage.EN -> "Beginner level: Easy questions about basic concepts"
                ContentLanguage.JA -> "初級レベル：基本概念についての簡単な問題"
            }
            Difficulty.INTERMEDIATE -> when (contentLanguage) {
                ContentLanguage.KO -> "중급 수준: 개념 이해와 적용을 묻는 문제"
                ContentLanguage.EN -> "Intermediate level: Questions about understanding and application"
                ContentLanguage.JA -> "中級レベル：理解と応用についての問題"
            }
            Difficulty.ADVANCED -> when (contentLanguage) {
                ContentLanguage.KO -> "고급 수준: 심화 개념과 분석을 묻는 어려운 문제"
                ContentLanguage.EN -> "Advanced level: Difficult questions about advanced concepts and analysis"
                ContentLanguage.JA -> "上級レベル：高度な概念と分析についての難しい問題"
            }
            null -> when (contentLanguage) {
                ContentLanguage.KO -> "중급 수준: 개념 이해를 묻는 문제"
                ContentLanguage.EN -> "Intermediate level: Questions about understanding concepts"
                ContentLanguage.JA -> "中級レベル：概念理解についての問題"
            }
        }

        val goodExamples = when (contentLanguage) {
            ContentLanguage.KO -> """
                |좋은 예시 (궁금증 유발형):
                |영상: "주피터 노트북 사용법"
                |질문: "데이터 분석가가 가장 많이 쓰는 툴은?"
                |보기: "Jupyter", "Excel", "Tableau", "SQL"
                |→ 사용자: "어? 뭔데? 궁금한데!" → 영상 시청
                |
                |영상: "재귀함수 개념 설명"
                |질문: "함수가 자기 자신을 호출하면?"
                |보기: "무한루프", "스택오버플로", "정상작동", "에러"
                |→ 사용자: "어떻게 되는거지?" → 영상 시청
                |
                |영상: "Git 브랜치 전략"
                |질문: "협업 시 코드 충돌 방지법은?"
                |보기: "브랜치", "복사", "주석", "삭제"
                |→ 사용자: "아 이거 궁금했는데!" → 영상 시청
            """.trimMargin()
            ContentLanguage.EN -> """
                |Good examples (curiosity-driven):
                |Video: "Jupyter Notebook Tutorial"
                |Question: "Data scientists' favorite tool?"
                |Options: "Jupyter", "Excel", "Tableau", "SQL"
                |→ User: "What is it?" → Watch video
                |
                |Video: "Recursion Explained"
                |Question: "Function calls itself, then?"
                |Options: "Infinite loop", "Stack overflow", "Works fine", "Error"
                |→ User: "How does it work?" → Watch video
                |
                |Video: "Git Branching Strategy"
                |Question: "Prevent code conflicts in teams?"
                |Options: "Branching", "Copying", "Comments", "Deleting"
                |→ User: "I wondered about this!" → Watch video
            """.trimMargin()
            ContentLanguage.JA -> """
                |良い例 (好奇心を刺激):
                |動画: "Jupyter Notebook使い方"
                |質問: "データ分析で一番使うツールは？"
                |選択肢: "Jupyter", "Excel", "Tableau", "SQL"
                |→ ユーザー: "何だろう？" → 動画視聴
                |
                |動画: "再帰関数の概念"
                |質問: "関数が自分を呼び出すと？"
                |選択肢: "無限ループ", "スタックオーバーフロー", "正常動作", "エラー"
                |→ ユーザー: "どうなるの？" → 動画視聴
                |
                |動画: "Gitブランチ戦略"
                |質問: "チームでコード競合を防ぐには？"
                |選択肢: "ブランチ", "コピー", "コメント", "削除"
                |→ ユーザー: "気になってた！" → 動画視聴
            """.trimMargin()
        }

        val badExamples = when (contentLanguage) {
            ContentLanguage.KO -> """
                |나쁜 예시 (영상 봐야 답 가능 / 맥락 없음):
                |❌ "실습 환경 구축 도구는?"
                |   → 사용자: "뭔 소리야? 무슨 실습?"
                |
                |❌ "이 영상의 주제는?"
                |   → 사용자: "영상도 안 봤는데?"
                |
                |❌ "커널 재시작 단축키는?"
                |   → 사용자: "몰라... 넘어가야지"
                |
                |❌ "영상에서 설명한 세 번째 기능은?"
                |   → 사용자: "아직 안 봤는데 어떻게 알아?"
            """.trimMargin()
            ContentLanguage.EN -> """
                |Bad examples (requires watching / no context):
                |❌ "Development environment setup tool?"
                |   → User: "What? What environment?"
                |
                |❌ "This video's topic?"
                |   → User: "Haven't watched yet"
                |
                |❌ "Kernel restart shortcut?"
                |   → User: "Don't know... skip"
                |
                |❌ "Third feature explained in video?"
                |   → User: "How would I know before watching?"
            """.trimMargin()
            ContentLanguage.JA -> """
                |悪い例 (視聴必須 / 文脈なし):
                |❌ "実習環境構築ツールは？"
                |   → ユーザー: "何の話？"
                |
                |❌ "この動画のテーマは？"
                |   → ユーザー: "まだ見てないのに"
                |
                |❌ "カーネル再起動のショートカットは？"
                |   → ユーザー: "知らない...スキップ"
                |
                |❌ "動画で説明した3番目の機能は？"
                |   → ユーザー: "見る前にどうやって？"
            """.trimMargin()
        }

        val lengthGuidance = when (contentLanguage) {
            ContentLanguage.KO -> """
                |**길이 제한 (매우 중요!):**
                |- 질문: 15-30자 이내 (짧고 간결하게!)
                |- 각 보기: 2-15자 이내 (단어나 짧은 구절 수준!)
                |- 시험 문제처럼 간결하고 명확하게
                |- 숏폼 콘텐츠이므로 빠르게 읽고 답할 수 있어야 함
            """.trimMargin()
            ContentLanguage.EN -> """
                |**Length limits (CRITICAL!):**
                |- Question: 5-10 words max (short and clear!)
                |- Each option: 1-5 words max (word or short phrase level!)
                |- Like exam questions: concise and clear
                |- Short-form content: must be quick to read and answer
            """.trimMargin()
            ContentLanguage.JA -> """
                |**長さ制限 (非常に重要!):**
                |- 質問: 15-30文字以内 (短く簡潔に!)
                |- 各選択肢: 2-15文字以内 (単語や短いフレーズレベル!)
                |- 試験問題のように簡潔で明確に
                |- ショート動画なので素早く読んで答えられること
            """.trimMargin()
        }

        return """
            |당신은 숏폼 교육 콘텐츠 퀴즈 전문가입니다.
            |
            |**중요: $languageInstruction**
            |타겟 언어: ${contentLanguage.nativeName} (${contentLanguage.code})
            |
            |콘텐츠 제목: $title
            |콘텐츠 설명:
            |$description
            |
            |난이도: $difficultyGuidance
            |
            |$lengthGuidance
            |
            |$goodExamples
            |
            |$badExamples
            |
            |**핵심 원칙 (반드시 준수!):**
            |
            |1. **궁금증 유발이 목적!**
            |   - 사용자는 영상 보기 전에 퀴즈를 먼저 본다
            |   - "어? 뭔데? 궁금한데!" → 영상 시청 동기 부여
            |   - 질문 자체가 흥미롭고 일반적인 호기심 자극
            |
            |2. **영상 안 봐도 이해 가능한 질문**
            |   - 제목과 설명만 봐도 질문의 의미를 알 수 있어야 함
            |   - 맥락 없이 갑자기 나와도 이해 가능
            |   - "이거 나도 궁금했는데!" 느낌
            |
            |3. **답은 영상 보면 알 수 있을 것 같은 기대감**
            |   - "영상 보면 알겠지?" 기대
            |   - 영상 내용과 관련 있지만 직접적이지 않음
            |   - 일반적인 문제/상황 제시
            |
            |4. **극도로 간결**
            |   - 질문: 15-30자 / 5-10 단어
            |   - 보기: 2-15자 / 1-5 단어
            |   - 3초 안에 읽고 이해 가능
            |
            |5. **보기 구성**
            |   - 3-4개 (너무 많으면 안 됨)
            |   - 단일 정답 (복수 정답 X)
            |   - 모두 그럴듯하게 (너무 명백한 오답 X)
            |
            |**절대 금지사항:**
            |❌ "이 영상의..." / "영상에서..." 같은 표현
            |❌ 영상 봐야만 답할 수 있는 세부 질문
            |❌ 맥락 없는 전문 용어 ("커널 재시작은?")
            |❌ 순서/단계 질문 ("세 번째 기능은?")
            |❌ 긴 문장형 질문 / 설명 포함 보기
            |
            |JSON 형식으로만 응답해주세요 (${contentLanguage.nativeName}로 작성):
            |{
            |  "question": "궁금증 유발 질문 (15-30자)",
            |  "allowMultipleAnswers": false,
            |  "options": [
            |    {"optionText": "정답 (2-15자)", "isCorrect": true},
            |    {"optionText": "오답1", "isCorrect": false},
            |    {"optionText": "오답2", "isCorrect": false},
            |    {"optionText": "오답3", "isCorrect": false}
            |  ]
            |}
        """.trimMargin()
    }

    // ========== JSON Parsers ==========

    private fun extractJsonFromResponse(response: String): String {
        val trimmed = response.trim()

        // 1. Markdown 코드 블록 (```json ... ``` 또는 ``` ... ```) 추출 시도
        val codeBlockPattern = """```(?:json)?\s*\n?([\s\S]*?)\n?```""".toRegex()
        val codeBlockMatch = codeBlockPattern.find(trimmed)
        if (codeBlockMatch != null) {
            val extracted = codeBlockMatch.groupValues[1].trim()
            logger.debug("Markdown 코드 블록에서 JSON 추출: {} -> {} chars",
                trimmed.take(50), extracted.length)
            return extracted
        }

        // 2. 단일 백틱으로 감싸진 경우 (` ... `) 처리
        if (trimmed.startsWith("`") && trimmed.endsWith("`")) {
            val extracted = trimmed.removeSurrounding("`").trim()
            logger.debug("단일 백틱 제거: {} chars", extracted.length)
            return extracted
        }

        // 3. 그대로 반환
        logger.debug("코드 블록 없음, 원본 반환: {} chars", trimmed.length)
        return trimmed
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

    private fun parseEditPlanFromJson(json: String): EditPlan {
        return try {
            logger.debug("EditPlan JSON 파싱 시작: {} chars", json.length)
            logger.debug("EditPlan JSON 내용 (첫 200자): {}", json.take(200))

            val plan: Map<String, Any> = objectMapper.readValue(json)
            val clipsData = plan["clips"] as? List<Map<String, Any>> ?: emptyList()

            logger.debug("EditPlan 클립 개수: {}", clipsData.size)

            val clips = clipsData.map { clip ->
                ClipSegment(
                    orderIndex = (clip["orderIndex"] as Number).toInt(),
                    startTimeMs = (clip["startTimeMs"] as Number).toLong(),
                    endTimeMs = (clip["endTimeMs"] as Number).toLong(),
                    title = clip["title"] as String,
                    description = clip["description"] as? String,
                    keywords = (clip["keywords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                )
            }.sortedBy { it.orderIndex }  // orderIndex로 정렬하여 순서 보장

            EditPlan(
                clips = clips,
                totalDurationMs = (plan["totalDurationMs"] as? Number)?.toLong() ?: clips.sumOf { it.endTimeMs - it.startTimeMs },
                editingStrategy = plan["editingStrategy"] as? String ?: "highlight_compilation",
                transitionStyle = plan["transitionStyle"] as? String ?: "hard_cut"
            )
        } catch (e: Exception) {
            logger.warn("EditPlan JSON 파싱 실패: {}", e.message, e)
            logger.debug("파싱 실패한 JSON: {}", json.take(500))
            // Fallback: 빈 편집 계획
            EditPlan(
                clips = emptyList(),
                totalDurationMs = 0L,
                editingStrategy = "none",
                transitionStyle = "hard_cut"
            )
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
                val languageCode = q["language"] as? String ?: "ko"
                val language = ContentLanguage.fromCode(languageCode) ?: ContentLanguage.KO

                SearchQuery(
                    query = q["query"] as String,
                    targetCategory = q["targetCategory"] as String,
                    expectedContentType = q["expectedContentType"] as String,
                    priority = (q["priority"] as Number).toInt(),
                    language = language
                )
            }
        } catch (e: Exception) {
            logger.warn("검색 쿼리 JSON 파싱 실패: {}", e.message)
            emptyList()
        }
    }

    private fun parseEvaluationsFromJson(json: String, candidates: List<VideoCandidate>): List<EvaluatedVideo> {
        logger.debug("평가 JSON 파싱 시작: json length={}, candidates count={}", json.length, candidates.size)
        logger.debug("평가 JSON 내용: {}", json.take(500))

        return try {
            val evaluations: List<Map<String, Any>> = objectMapper.readValue(json)
            logger.debug("파싱된 평가 수: {}", evaluations.size)

            evaluations.mapNotNull { eval ->
                val index = (eval["index"] as Number).toInt()
                val candidate = candidates.getOrNull(index) ?: return@mapNotNull null

                EvaluatedVideo(
                    candidate = candidate,
                    relevanceScore = (eval["relevanceScore"] as Number).toInt(),
                    educationalValue = (eval["educationalValue"] as Number).toInt(),
                    shortFormSuitability = (eval["shortFormSuitability"] as? Number)?.toInt() ?: 50,
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
                    shortFormSuitability = 50,
                    predictedQuality = 50,
                    recommendation = Recommendation.MAYBE,
                    reasoning = "평가 파싱 실패로 기본값 사용"
                )
            }
        }
    }

    private fun parseQuizFromJson(json: String): QuizData {
        return try {
            logger.debug("퀴즈 JSON 파싱 시작: {} chars", json.length)

            val quizMap: Map<String, Any> = objectMapper.readValue(json)
            val optionsData = quizMap["options"] as? List<Map<String, Any>> ?: emptyList()

            logger.debug("퀴즈 보기 개수: {}", optionsData.size)

            val options = optionsData.map { option ->
                QuizOption(
                    optionText = option["optionText"] as String,
                    isCorrect = option["isCorrect"] as Boolean
                )
            }

            QuizData(
                question = quizMap["question"] as String,
                allowMultipleAnswers = quizMap["allowMultipleAnswers"] as? Boolean ?: false,
                options = options
            )
        } catch (e: Exception) {
            logger.warn("퀴즈 JSON 파싱 실패: {}", e.message, e)
            logger.debug("파싱 실패한 JSON: {}", json.take(500))
            // Fallback: 기본 퀴즈 반환 (실패해도 전체 publish 과정은 계속되도록)
            throw LlmException("Failed to parse quiz JSON", e)
        }
    }
}

/**
 * LLM 예외
 */
class LlmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
