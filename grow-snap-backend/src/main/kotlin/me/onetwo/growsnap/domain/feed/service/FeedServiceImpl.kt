package me.onetwo.growsnap.domain.feed.service

import me.onetwo.growsnap.domain.feed.dto.FeedResponse
import me.onetwo.growsnap.domain.feed.repository.FeedRepository
import me.onetwo.growsnap.domain.feed.service.recommendation.RecommendationService
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageRequest
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

/**
 * 피드 서비스 구현체
 *
 * 피드 관련 비즈니스 로직을 처리합니다.
 *
 * ### TikTok/Instagram Reels 방식의 추천 피드
 * - Redis에 250개 배치를 미리 생성하여 캐싱 (TTL: 30분)
 * - 50% 소진 시 다음 배치를 백그라운드에서 prefetch
 * - 일관된 피드 경험 제공 (세션 기반)
 *
 * @property feedRepository 피드 레포지토리
 * @property recommendationService 추천 서비스
 * @property feedCacheService 피드 캐시 서비스
 */
@Service
@Transactional(readOnly = true)
class FeedServiceImpl(
    private val feedRepository: FeedRepository,
    private val recommendationService: RecommendationService,
    private val feedCacheService: FeedCacheService
) : FeedService {

    /**
     * 메인 피드 조회
     *
     * TikTok/Instagram Reels 방식의 숏폼 피드를 제공합니다.
     * Redis를 사용한 세션 기반 캐싱으로 일관된 피드 경험을 보장합니다.
     *
     * ### 처리 흐름 (TikTok/Instagram Reels 방식)
     * 1. cursor를 offset으로 변환 (없으면 0)
     * 2. batchNumber 계산 (offset / 250)
     * 3. Redis에서 캐싱된 배치 조회
     * 4. 캐시 미스 시:
     *    - 최근 본 콘텐츠 제외 목록 조회
     *    - 추천 알고리즘 실행 (250개 생성)
     *    - Redis에 저장 (TTL: 30분)
     * 5. 배치에서 필요한 범위만 조회 (offset ~ offset+limit)
     * 6. 상세 정보 조회
     * 7. Prefetch 체크 (50% 소진 시 다음 배치 백그라운드 생성)
     *
     * @param userId 사용자 ID
     * @param pageRequest 페이지네이션 요청 (cursor는 offset으로 해석)
     * @return 피드 응답
     */
    override fun getMainFeed(userId: UUID, pageRequest: CursorPageRequest): Mono<FeedResponse> {
        // cursor를 offset으로 변환 (cursor가 없으면 0부터 시작)
        val offset = pageRequest.cursor?.toLongOrNull() ?: 0L
        val limit = pageRequest.limit

        // 배치 번호 계산 (offset / BATCH_SIZE)
        val batchNumber = (offset / FeedCacheService.BATCH_SIZE).toInt()
        val offsetInBatch = offset % FeedCacheService.BATCH_SIZE

        return feedCacheService.getRecommendationBatch(userId, batchNumber)
            .switchIfEmpty(
                // 캐시 미스: 새 배치 생성
                generateAndCacheBatch(userId, batchNumber)
            )
            .flatMap { cachedBatch ->
                // 배치에서 필요한 범위만 추출
                val startIndex = offsetInBatch.toInt()
                val endIndex = (startIndex + limit + 1).coerceAtMost(cachedBatch.size)
                val contentIds = cachedBatch.subList(startIndex, endIndex)

                // Prefetch 체크 (50% 소진 시 다음 배치 생성)
                checkAndPrefetchNextBatch(userId, batchNumber, offset, cachedBatch.size)

                // 상세 정보 조회
                feedRepository.findByContentIds(userId, contentIds)
                    .collectList()
                    .map { feedItems ->
                        CursorPageResponse.of(
                            content = feedItems,
                            limit = limit,
                            getCursor = { (offset + feedItems.indexOf(it) + 1).toString() }
                        )
                    }
            }
    }

    /**
     * 새로운 추천 배치 생성 및 캐싱
     *
     * 추천 알고리즘을 실행하여 250개의 콘텐츠 ID를 생성하고 Redis에 저장합니다.
     *
     * @param userId 사용자 ID
     * @param batchNumber 배치 번호
     * @return 생성된 콘텐츠 ID 목록
     */
    private fun generateAndCacheBatch(userId: UUID, batchNumber: Int): Mono<List<UUID>> {
        return feedRepository.findRecentlyViewedContentIds(userId, RECENTLY_VIEWED_LIMIT)
            .collectList()
            .flatMap { recentlyViewedIds ->
                // 추천 알고리즘으로 250개 생성
                recommendationService.getRecommendedContentIds(
                    userId = userId,
                    limit = FeedCacheService.BATCH_SIZE,
                    excludeContentIds = recentlyViewedIds
                )
                    .collectList()
                    .flatMap { recommendedIds ->
                        // Redis에 저장
                        feedCacheService.saveRecommendationBatch(userId, batchNumber, recommendedIds)
                            .thenReturn(recommendedIds)
                    }
            }
    }

    /**
     * Prefetch 체크 및 다음 배치 백그라운드 생성
     *
     * 사용자가 현재 배치의 50%를 소진했으면, 다음 배치를 백그라운드에서 미리 생성합니다.
     * TikTok/Instagram Reels의 부드러운 스크롤 경험을 제공하기 위함입니다.
     *
     * @param userId 사용자 ID
     * @param currentBatch 현재 배치 번호
     * @param currentOffset 현재 offset
     * @param batchSize 현재 배치 크기
     */
    private fun checkAndPrefetchNextBatch(
        userId: UUID,
        currentBatch: Int,
        currentOffset: Long,
        batchSize: Int
    ) {
        val consumedPercentage = (currentOffset % FeedCacheService.BATCH_SIZE) / batchSize.toDouble()

        // 50% 이상 소진했으면 다음 배치 prefetch
        if (consumedPercentage >= FeedCacheService.PREFETCH_THRESHOLD) {
            val nextBatch = currentBatch + 1

            // 이미 캐시되어 있는지 확인
            feedCacheService.getBatchSize(userId, nextBatch)
                .filter { it == 0L }  // 캐시가 없을 때만
                .flatMap {
                    // 백그라운드에서 비동기로 생성 (응답 지연 방지)
                    generateAndCacheBatch(userId, nextBatch)
                }
                .subscribeOn(Schedulers.boundedElastic())  // 백그라운드 스레드
                .subscribe(
                    { },  // onNext: 성공 시 아무것도 하지 않음
                    { error -> logger.error("Failed to prefetch next batch for user {}", userId, error) }  // onError
                )
        }
    }

    /**
     * 팔로잉 피드 조회
     *
     * 사용자가 팔로우한 크리에이터의 최신 콘텐츠를 제공합니다.
     *
     * ### 처리 흐름
     * 1. 팔로잉 피드 조회 (limit + 1개를 조회하여 hasNext 판단)
     * 2. 커서 기반 페이지네이션 응답 생성
     *
     * @param userId 사용자 ID
     * @param pageRequest 페이지네이션 요청
     * @return 피드 응답
     */
    override fun getFollowingFeed(userId: UUID, pageRequest: CursorPageRequest): Mono<FeedResponse> {
        val cursor = pageRequest.cursor?.let { UUID.fromString(it) }
        val limit = pageRequest.limit

        return feedRepository.findFollowingFeed(
            userId = userId,
            cursor = cursor,
            limit = limit + 1  // +1 to check if there are more items
        )
            .collectList()
            .map { feedItems ->
                CursorPageResponse.of(
                    content = feedItems,
                    limit = limit,
                    getCursor = { it.contentId.toString() }
                )
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FeedServiceImpl::class.java)

        /**
         * 최근 본 콘텐츠 조회 개수 (중복 방지용)
         */
        private const val RECENTLY_VIEWED_LIMIT = 100
    }
}
