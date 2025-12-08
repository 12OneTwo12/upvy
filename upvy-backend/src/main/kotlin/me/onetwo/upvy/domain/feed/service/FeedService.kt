package me.onetwo.upvy.domain.feed.service

import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.infrastructure.common.dto.CursorPageRequest
import me.onetwo.upvy.domain.feed.dto.FeedResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 피드 서비스 인터페이스
 *
 * 피드 관련 비즈니스 로직을 처리합니다.
 */
interface FeedService {

    /**
     * 메인 피드 조회
     *
     * 추천 알고리즘 기반으로 사용자에게 맞춤화된 피드를 제공합니다.
     * 최근 본 콘텐츠는 제외하여 중복을 방지합니다.
     *
     * Issue #107: 언어 기반 가중치 적용
     * - 사용자 선호 언어에 따라 콘텐츠에 2.0x (일치) 또는 0.5x (불일치) 가중치 적용
     * - Controller에서 기본값 "en" 보장
     *
     * @param userId 사용자 ID
     * @param pageRequest 페이지네이션 요청
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en)
     * @return 피드 응답
     */
    fun getMainFeed(
        userId: UUID,
        pageRequest: CursorPageRequest,
        preferredLanguage: String
    ): Mono<FeedResponse>

    /**
     * 팔로잉 피드 조회
     *
     * 사용자가 팔로우한 크리에이터의 최신 콘텐츠를 제공합니다.
     *
     * @param userId 사용자 ID
     * @param pageRequest 페이지네이션 요청
     * @return 피드 응답
     */
    fun getFollowingFeed(userId: UUID, pageRequest: CursorPageRequest): Mono<FeedResponse>

    /**
     * 카테고리별 피드 조회
     *
     * 특정 카테고리의 콘텐츠를 추천 알고리즘 기반으로 조회합니다.
     * 메인 피드와 동일한 추천 알고리즘을 사용하되, 해당 카테고리로 필터링합니다.
     * 선택적 인증을 지원하며, 인증되지 않은 사용자도 조회할 수 있습니다.
     *
     * ### TikTok/Instagram Reels 방식의 추천 피드
     * - Redis에 250개 배치를 미리 생성하여 캐싱 (TTL: 30분)
     * - 50% 소진 시 다음 배치를 백그라운드에서 prefetch
     * - 일관된 피드 경험 제공 (세션 기반)
     *
     * ### 추천 전략 (메인 피드와 동일한 비율)
     * - 협업 필터링 (40%): Item-based Collaborative Filtering
     * - 인기 콘텐츠 (30%): 해당 카테고리의 인기 콘텐츠
     * - 신규 콘텐츠 (10%): 해당 카테고리의 최신 콘텐츠
     * - 랜덤 콘텐츠 (20%): 해당 카테고리의 랜덤 콘텐츠
     *
     * ### 처리 흐름
     * 1. cursor를 offset으로 변환
     * 2. batchNumber 계산 (offset / 250)
     * 3. Redis에서 언어별로 캐싱된 배치 조회 (Issue #107)
     * 4. 캐시 미스 시 추천 알고리즘 실행 (250개 생성, 언어 가중치 적용)
     * 5. 배치에서 필요한 범위만 조회
     * 6. 상세 정보 조회
     * 7. Prefetch 체크 (50% 소진 시 다음 배치 생성)
     *
     * Issue #107: 언어 기반 가중치 적용
     * - 사용자 선호 언어에 따라 콘텐츠에 2.0x (일치) 또는 0.5x (불일치) 가중치 적용
     * - Controller에서 기본값 "en" 보장
     *
     * @param userId 사용자 ID (Optional, 비인증 시 null)
     * @param category 조회할 카테고리
     * @param pageRequest 페이지네이션 요청 (cursor는 offset으로 해석)
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en)
     * @return 피드 응답
     */
    fun getCategoryFeed(
        userId: UUID?,
        category: Category,
        pageRequest: CursorPageRequest,
        preferredLanguage: String
    ): Mono<FeedResponse>
}
