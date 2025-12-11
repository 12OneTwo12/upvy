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
import org.springframework.beans.factory.annotation.Value
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
    private val aiContentJobRepository: AiContentJobRepository,
    @Value("#{jobParameters['category']}") private val category: String?,
    @Value("#{jobParameters['maxVideos']}") private val maxVideos: Long?,
    @Value("#{jobParameters['skipCrawl']}") private val skipCrawl: String?
) : ItemReader<EvaluatedVideo> {

    companion object {
        private val logger = LoggerFactory.getLogger(AiPoweredSearchReader::class.java)
        private const val MAX_QUERIES_PER_RUN = 10  // 카테고리 다양성을 위해 5 -> 10으로 증가
        private const val MAX_VIDEOS_PER_QUERY = 10
        private const val YOUTUBE_API_DELAY_MS = 1000L  // Rate Limiting 방지용 딜레이

        /**
         * 카테고리 다양성을 보장하며 검색 쿼리를 선택합니다.
         *
         * 알고리즘:
         * 1. 각 카테고리에서 우선순위가 가장 높은 쿼리를 1개씩 선택
         * 2. 남은 슬롯은 우선순위 순으로 채움
         * 3. 이를 통해 한 카테고리로 치우치지 않고 다양한 주제를 검색
         */
        private fun selectDiverseQueries(queries: List<SearchQuery>, maxCount: Int): List<SearchQuery> {
            if (queries.size <= maxCount) return queries

            val selected = mutableListOf<SearchQuery>()
            val remaining = queries.toMutableList()

            // 1단계: 각 카테고리에서 최소 1개씩 선택 (우선순위 높은 것 우선)
            val categoriesProcessed = mutableSetOf<String>()
            for (query in queries.sortedByDescending { it.priority }) {
                if (selected.size >= maxCount) break
                if (!categoriesProcessed.contains(query.targetCategory)) {
                    selected.add(query)
                    remaining.remove(query)
                    categoriesProcessed.add(query.targetCategory)
                    logger.debug("카테고리 다양성: {} 카테고리에서 선택 - {}", query.targetCategory, query.query)
                }
            }

            // 2단계: 남은 슬롯을 우선순위 순으로 채움
            val additionalCount = maxCount - selected.size
            if (additionalCount > 0) {
                val additional = remaining
                    .sortedByDescending { it.priority }
                    .take(additionalCount)
                selected.addAll(additional)
                logger.debug("추가 선택: {}개", additional.size)
            }

            return selected.sortedByDescending { it.priority }
        }
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
        val shouldSkip = skipCrawl?.toBoolean() ?: false

        if (shouldSkip) {
            logger.info("크롤링 스킵: skipCrawl=true")
            evaluatedVideos = emptyList<EvaluatedVideo>().iterator()
            return
        }

        val targetCategory = category?.takeIf { it.isNotBlank() && it != "ALL" }
        val maxVideoLimit = maxVideos?.toInt() ?: 5

        logger.info("AI 기반 검색 시작: category={}, maxVideos={}", targetCategory ?: "ALL", maxVideoLimit)

        runBlocking {
            try {
                // 1. 검색 컨텍스트 수집
                val context = searchContextCollector.collect()
                logger.debug("검색 컨텍스트 수집 완료: {}", context)

                // 2. LLM으로 검색 쿼리 생성
                var generatedQueries = llmClient.generateSearchQueries(context)

                // 카테고리 필터링
                if (targetCategory != null) {
                    generatedQueries = generatedQueries.filter { it.targetCategory == targetCategory }
                    logger.info("카테고리 필터링 적용: category={}, filtered={}", targetCategory, generatedQueries.size)
                }

                // 다양성을 보장하는 쿼리 선택: 각 카테고리에서 골고루 선택
                val searchQueries = selectDiverseQueries(generatedQueries, MAX_QUERIES_PER_RUN)
                logger.info("검색 쿼리 생성 완료: count={}, categories={}",
                    searchQueries.size,
                    searchQueries.map { it.targetCategory }.distinct().joinToString(", ")
                )

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
                    .take(maxVideoLimit)  // 최대 비디오 수 제한

                logger.info("추천 비디오 수: {} (제한: {})", recommended.size, maxVideoLimit)
                evaluatedVideos = recommended.iterator()

            } catch (e: Exception) {
                logger.error("AI 기반 검색 실패", e)
                throw e  // 예외를 다시 던져서 Step을 FAILED로 처리
            }
        }
    }
}
