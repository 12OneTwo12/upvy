package me.onetwo.growsnap.domain.feed.service.recommendation

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.feed.repository.FeedRepository
import me.onetwo.growsnap.domain.feed.service.collaborative.CollaborativeFilteringService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 추천 서비스 구현체
 *
 * 요구사항 명세서 섹션 2.2.1의 추천 알고리즘을 구현합니다.
 * - 협업 필터링 (40%): Item-based Collaborative Filtering
 * - 인기 콘텐츠 (30%): 높은 인터랙션
 * - 신규 콘텐츠 (10%): 최근 업로드
 * - 랜덤 콘텐츠 (20%): 다양성 확보
 *
 * Issue #107: 언어 기반 가중치 적용
 * - Repository 레벨에서 SQL CASE WHEN을 통해 언어 가중치 적용
 * - 사용자 선호 언어와 일치하는 콘텐츠: 2.0x 가중치
 * - 사용자 선호 언어와 불일치하는 콘텐츠: 0.5x 가중치
 *
 * @property feedRepository 피드 데이터 조회를 위한 레포지토리
 * @property collaborativeFilteringService Item-based 협업 필터링 서비스
 */
@Service
@Transactional(readOnly = true)
class RecommendationServiceImpl(
    private val feedRepository: FeedRepository,
    private val collaborativeFilteringService: CollaborativeFilteringService
) : RecommendationService {

    /**
     * 추천 콘텐츠 ID 목록 조회
     *
     * 여러 추천 전략을 병렬로 실행하여 콘텐츠 ID 목록을 반환합니다.
     * 언어 가중치는 Repository 레벨에서 SQL로 적용됩니다.
     *
     * ### 추천 전략 (비율)
     * - 협업 필터링 (40%): Item-based Collaborative Filtering
     * - 인기 콘텐츠 (30%): 높은 인터랙션
     * - 신규 콘텐츠 (10%): 최근 업로드
     * - 랜덤 콘텐츠 (20%): 다양성 확보
     *
     * ### 처리 흐름
     * 1. 각 전략별로 가져올 콘텐츠 수 계산
     * 2. **병렬로** 각 전략별 콘텐츠 조회 (Mono.zip 사용)
     *    - Repository에서 언어 가중치가 적용된 결과 반환
     * 3. 결과 합치기 및 무작위 섞기
     *
     * ### 성능 최적화
     * - Mono.zip을 사용하여 4개 전략을 동시에 실행
     * - 순차 처리(Flux.concat) 대비 약 4배 빠른 응답 시간
     * - Repository에서 DB 레벨 언어 가중치 적용 (단일 쿼리)
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en) - 기본값: "en"
     * @param category 필터링할 카테고리 (null이면 전체 피드)
     * @return 추천 콘텐츠 ID 목록 (Repository에서 언어 가중치 적용됨)
     */
    override fun getRecommendedContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>,
        preferredLanguage: String,
        category: Category?
    ): Flux<UUID> {
        val strategyLimits = RecommendationStrategy.calculateLimits(limit)

        // 병렬로 모든 전략 실행 (Repository가 언어 가중치를 적용하여 반환)
        return Mono.zip(
            getCollaborativeContentIds(userId, strategyLimits[RecommendationStrategy.COLLABORATIVE]!!, excludeContentIds, preferredLanguage, category).collectList(),
            feedRepository.findPopularContentIds(userId, strategyLimits[RecommendationStrategy.POPULAR]!!, excludeContentIds, category, preferredLanguage).collectList(),
            feedRepository.findNewContentIds(userId, strategyLimits[RecommendationStrategy.NEW]!!, excludeContentIds, category, preferredLanguage).collectList(),
            feedRepository.findRandomContentIds(userId, strategyLimits[RecommendationStrategy.RANDOM]!!, excludeContentIds, category, preferredLanguage).collectList()
        ).flatMapMany { tuple ->
            // 모든 결과 합치기 및 무작위 섞기
            val allIds = tuple.t1 + tuple.t2 + tuple.t3 + tuple.t4
            Flux.fromIterable(allIds.shuffled())
        }
    }

    /**
     * 협업 필터링 콘텐츠 ID 조회
     *
     * **Item-based Collaborative Filtering (Issue #10)**
     *
     * "이 콘텐츠를 좋아한 사람들은 이것도 좋아했습니다"
     *
     * ### 처리 흐름
     * 1. Item-based CF 알고리즘으로 추천 콘텐츠 조회
     *    - 내가 좋아요/저장/공유한 콘텐츠 조회 (seed items)
     *    - 각 seed item을 좋아한 다른 사용자 찾기
     *    - 그 사용자들이 좋아한 다른 콘텐츠 추천
     * 2. 추천 결과가 없으면 (신규 사용자) 인기 콘텐츠로 fallback (언어 가중치 적용)
     * 3. 추천 결과가 부족하면 인기 콘텐츠로 보충 (언어 가중치 적용)
     *
     * ### Best Practice 근거
     * - Netflix, YouTube, TikTok 등이 사용하는 방식
     * - User-based CF보다 확장성과 성능이 우수
     * - 실시간 계산 가능 (배치 처리 불필요)
     *
     * ### 향후 개선 계획 (Phase 2)
     * - Cosine Similarity / Jaccard Similarity 적용
     * - Matrix Factorization
     * - Neural Collaborative Filtering
     *
     * @param userId 사용자 ID
     * @param limit 조회할 콘텐츠 수
     * @param excludeContentIds 제외할 콘텐츠 ID 목록
     * @param preferredLanguage 사용자 선호 언어 (fallback 시 사용)
     * @param category 필터링할 카테고리 (null이면 전체)
     * @return 협업 필터링 추천 콘텐츠 ID 목록
     */
    private fun getCollaborativeContentIds(
        userId: UUID,
        limit: Int,
        excludeContentIds: List<UUID>,
        preferredLanguage: String,
        category: Category?
    ): Flux<UUID> {
        return collaborativeFilteringService.getRecommendedContents(userId, limit, preferredLanguage, category)
            .filter { !excludeContentIds.contains(it) }  // 제외 목록 필터링
            .collectList()
            .flatMapMany { recommendedIds ->
                if (recommendedIds.isEmpty()) {
                    // CF 추천 결과가 없으면 (신규 사용자) 인기 콘텐츠로 fallback (언어 가중치 적용)
                    logger.debug("No CF recommendations for user {}, falling back to popular content", userId)
                    feedRepository.findPopularContentIds(userId, limit, excludeContentIds, category, preferredLanguage)
                } else if (recommendedIds.size < limit) {
                    // CF 추천 결과가 부족하면 인기 콘텐츠로 보충 (언어 가중치 적용)
                    val remaining = limit - recommendedIds.size
                    logger.debug(
                        "CF recommendations ({}) < limit ({}), adding {} popular contents",
                        recommendedIds.size,
                        limit,
                        remaining
                    )
                    Flux.concat(
                        Flux.fromIterable(recommendedIds),
                        feedRepository.findPopularContentIds(userId, remaining, excludeContentIds + recommendedIds, category, preferredLanguage)
                    )
                } else {
                    // CF 추천 결과가 충분함
                    logger.debug("Returning {} CF recommendations for user {}", recommendedIds.size, userId)
                    Flux.fromIterable(recommendedIds.take(limit))
                }
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RecommendationServiceImpl::class.java)
    }
}
