package me.onetwo.growsnap.domain.feed.service

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.feed.model.CategoryFeedSortType
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageRequest
import me.onetwo.growsnap.domain.feed.dto.FeedResponse
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
     * @param userId 사용자 ID
     * @param pageRequest 페이지네이션 요청
     * @return 피드 응답
     */
    fun getMainFeed(userId: UUID, pageRequest: CursorPageRequest): Mono<FeedResponse>

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
     * 특정 카테고리의 콘텐츠를 정렬 옵션에 따라 조회합니다.
     * 선택적 인증을 지원하며, 인증되지 않은 사용자도 조회할 수 있습니다.
     *
     * ### 정렬 옵션
     * - POPULAR: 인기순 (인터랙션 가중치 기반 인기도 점수)
     * - RECENT: 최신순 (created_at DESC)
     *
     * ### 처리 흐름
     * 1. Repository에서 카테고리별 콘텐츠 ID 조회 (정렬 옵션 적용)
     * 2. findByContentIds()로 상세 정보 조회
     * 3. CursorPageResponse로 변환
     *
     * @param userId 사용자 ID (Optional, 비인증 시 null)
     * @param category 조회할 카테고리
     * @param sortBy 정렬 타입 (POPULAR 또는 RECENT)
     * @param pageRequest 페이지네이션 요청
     * @return 피드 응답
     */
    fun getCategoryFeed(
        userId: UUID?,
        category: Category,
        sortBy: CategoryFeedSortType,
        pageRequest: CursorPageRequest
    ): Mono<FeedResponse>
}
