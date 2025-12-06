package me.onetwo.growsnap.crawler.batch.step.crawl

import kotlinx.coroutines.runBlocking
import me.onetwo.growsnap.crawler.client.LlmClient
import me.onetwo.growsnap.crawler.client.youtube.YouTubeClient
import me.onetwo.growsnap.crawler.domain.AiContentJobRepository
import me.onetwo.growsnap.crawler.domain.EvaluatedVideo
import me.onetwo.growsnap.crawler.domain.Recommendation
import me.onetwo.growsnap.crawler.domain.VideoCandidate
import me.onetwo.growsnap.crawler.search.SearchContextCollector
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

                // 3. YouTube 검색
                val allCandidates = mutableListOf<VideoCandidate>()
                for (query in searchQueries) {
                    try {
                        val candidates = youTubeClient.searchCcVideos(query.query, MAX_VIDEOS_PER_QUERY)
                        // 이미 처리된 비디오 제외
                        val newCandidates = candidates.filter { candidate ->
                            aiContentJobRepository.findByYoutubeVideoId(candidate.videoId) == null
                        }
                        allCandidates.addAll(newCandidates)
                        logger.debug("검색 완료: query={}, results={}, new={}",
                            query.query, candidates.size, newCandidates.size)
                    } catch (e: Exception) {
                        logger.warn("검색 실패: query={}", query.query, e)
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
