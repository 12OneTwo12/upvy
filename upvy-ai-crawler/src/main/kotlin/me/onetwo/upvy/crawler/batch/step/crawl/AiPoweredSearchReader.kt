package me.onetwo.upvy.crawler.batch.step.crawl

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.onetwo.upvy.crawler.client.LlmClient
import me.onetwo.upvy.crawler.client.youtube.YouTubeClient
import me.onetwo.upvy.crawler.domain.AiContentJobRepository
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.EvaluatedVideo
import me.onetwo.upvy.crawler.domain.Recommendation
import me.onetwo.upvy.crawler.domain.SearchQuery
import me.onetwo.upvy.crawler.domain.VideoCandidate
import me.onetwo.upvy.crawler.search.SearchContextCollector
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemReader
import org.springframework.stereotype.Component

/**
 * AI 기반 YouTube 비디오 검색 Reader
 *
 * LLM을 사용하여 검색 쿼리를 생성하고,
 * YouTube에서 CC 라이선스 비디오를 검색한 후
 * LLM으로 품질을 사전 평가하여 추천 비디오만 반환합니다.
 */
@Component
@StepScope
class AiPoweredSearchReader(
    private val llmClient: LlmClient,
    private val youTubeClient: YouTubeClient,
    private val searchContextCollector: SearchContextCollector,
    private val aiContentJobRepository: AiContentJobRepository
) : ItemReader<EvaluatedVideo> {

    companion object {
        private val logger = LoggerFactory.getLogger(AiPoweredSearchReader::class.java)
        private const val MAX_QUERIES_PER_RUN = 5
        private const val MAX_VIDEOS_PER_QUERY = 10
        private const val YOUTUBE_API_DELAY_MS = 1000L  // Rate Limiting 방지용 딜레이
    }

    private var evaluatedVideos: Iterator<EvaluatedVideo>? = null
    private var initialized = false

    override fun read(): EvaluatedVideo? {
        if (!initialized) {
            initialize()
            initialized = true
        }

        return if (evaluatedVideos?.hasNext() == true) {
            evaluatedVideos?.next()
        } else {
            null
        }
    }

    /**
     * 초기화: 검색 컨텍스트 수집 -> 검색 쿼리 생성 -> YouTube 검색 -> LLM 평가
     */
    private fun initialize() {
        logger.info("AI 기반 검색 시작")

        runBlocking {
            try {
                // 1. 검색 컨텍스트 수집
                val context = searchContextCollector.collect()
                logger.debug("검색 컨텍스트 수집 완료: {}", context)

                // 2. LLM으로 검색 쿼리 생성
                val searchQueries = llmClient.generateSearchQueries(context)
                    .sortedByDescending { it.priority }
                    .take(MAX_QUERIES_PER_RUN)
                logger.info("검색 쿼리 생성 완료: count={}", searchQueries.size)

                // 3. YouTube 검색 (언어별로 검색)
                // 비디오 ID -> 언어 매핑 (같은 비디오가 여러 언어로 검색될 수 있음)
                val videoLanguageMap = mutableMapOf<String, ContentLanguage>()
                val allCandidates = mutableListOf<VideoCandidate>()

                for ((index, query) in searchQueries.withIndex()) {
                    try {
                        // Rate Limiting 방지: 첫 번째 요청 이후부터 딜레이 적용
                        if (index > 0) {
                            delay(YOUTUBE_API_DELAY_MS)
                        }

                        val candidates = youTubeClient.searchCcVideos(
                            query = query.query,
                            maxResults = MAX_VIDEOS_PER_QUERY,
                            language = query.language
                        )
                        // 이미 처리된 비디오 제외
                        val newCandidates = candidates.filter { candidate ->
                            aiContentJobRepository.findByYoutubeVideoId(candidate.videoId) == null
                        }

                        // 비디오-언어 매핑 저장 (첫 번째 검색 결과의 언어 우선)
                        newCandidates.forEach { candidate ->
                            if (!videoLanguageMap.containsKey(candidate.videoId)) {
                                videoLanguageMap[candidate.videoId] = query.language
                            }
                        }

                        allCandidates.addAll(newCandidates)
                        logger.debug("검색 완료: query={}, language={}, results={}, new={}",
                            query.query, query.language.code, candidates.size, newCandidates.size)
                    } catch (e: Exception) {
                        logger.warn("검색 실패: query={}, language={}", query.query, query.language.code, e)
                    }
                }

                // 중복 제거
                val uniqueCandidates = allCandidates.distinctBy { it.videoId }
                logger.info("검색 결과 수집 완료: total={}, unique={}", allCandidates.size, uniqueCandidates.size)

                if (uniqueCandidates.isEmpty()) {
                    logger.info("새로운 비디오 후보 없음")
                    evaluatedVideos = emptyList<EvaluatedVideo>().iterator()
                    return@runBlocking
                }

                // 4. LLM으로 비디오 품질 사전 평가
                val evaluated = llmClient.evaluateVideos(uniqueCandidates)
                    .map { video ->
                        // 언어 정보 추가
                        val language = videoLanguageMap[video.candidate.videoId] ?: ContentLanguage.KO
                        video.copy(language = language)
                    }
                logger.info("비디오 평가 완료: count={}", evaluated.size)

                // 5. 추천 등급이 RECOMMENDED 이상인 비디오만 필터링
                val recommended = evaluated.filter { video ->
                    video.recommendation == Recommendation.HIGHLY_RECOMMENDED ||
                    video.recommendation == Recommendation.RECOMMENDED
                }.sortedByDescending { it.predictedQuality }

                logger.info("추천 비디오 수: {}", recommended.size)
                evaluatedVideos = recommended.iterator()

            } catch (e: Exception) {
                logger.error("AI 기반 검색 실패", e)
                throw e  // 예외를 다시 던져서 Step을 FAILED로 처리
            }
        }
    }
}
