package me.onetwo.growsnap.domain.feed.repository

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.feed.dto.FeedItemResponse
import reactor.core.publisher.Flux
import java.util.UUID

/**
 * 피드 레포지토리 인터페이스
 *
 * 피드 데이터를 조회하는 레포지토리입니다.
 */
interface FeedRepository {

    /**
     * 팔로잉 피드 조회
     *
     * 사용자가 팔로우한 크리에이터의 최신 콘텐츠를 제공합니다.
     *
     * @param userId 사용자 ID
     * @param cursor 커서 (마지막 조회 콘텐츠 ID, null이면 첫 페이지)
     * @param limit 조회할 항목 수 (limit + 1개를 조회하여 hasNext 판단)
     * @return 피드 아이템 목록
     */
    fun findFollowingFeed(
        userId: UUID,
        cursor: UUID?,
        limit: Int
    ): Flux<FeedItemResponse>

    /**
     * 최근 본 콘텐츠 ID 목록 조회
     *
     * 중복 콘텐츠 방지를 위해 사용자가 최근 본 콘텐츠 ID 목록을 조회합니다.
     *
     * @param userId 사용자 ID
     * @param limit 조회할 항목 수
     * @return 최근 본 콘텐츠 ID 목록
     */
    fun findRecentlyViewedContentIds(userId: UUID, limit: Int): Flux<UUID>

    /**
     * 인기 콘텐츠 ID 목록 조회
     *
     * 인터랙션 가중치 기반 인기도 점수가 높은 콘텐츠를 조회합니다.
     *
     * ### 인기도 계산 공식 (Issue #107: 언어 가중치 적용)
     * ```
     * base_score = view_count * 1.0
     *            + like_count * 5.0
     *            + comment_count * 3.0
     *            + save_count * 7.0
     *            + share_count * 10.0
     *
     * language_multiplier = CASE
     *   WHEN content_language = preferredLanguage THEN 2.0
     *   ELSE 0.5
     * END
     *
     * final_score = base_score * language_multiplier
     * ```
     *
     * @param userId 사용자 ID (차단 필터링용)
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @param category 카테고리 필터 (null이면 전체 조회)
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en) - 언어 가중치 적용
     * @return 인기 콘텐츠 ID 목록 (최종 점수 순 정렬)
     */
    fun findPopularContentIds(
        userId: UUID,
        limit: Int,
        excludeIds: List<UUID>,
        category: Category? = null,
        preferredLanguage: String = "en"
    ): Flux<UUID>

    /**
     * 신규 콘텐츠 ID 목록 조회
     *
     * 최근 업로드된 콘텐츠를 조회합니다.
     *
     * ### 정렬 공식 (Issue #107: 언어 가중치 적용)
     * ```
     * recency_score = UNIX_TIMESTAMP(NOW()) - UNIX_TIMESTAMP(created_at)
     *
     * language_multiplier = CASE
     *   WHEN content_language = preferredLanguage THEN 2.0
     *   ELSE 0.5
     * END
     *
     * final_score = recency_score * language_multiplier
     * ORDER BY final_score DESC (최신 콘텐츠 중 선호 언어 우선)
     * ```
     *
     * @param userId 사용자 ID (차단 필터링용)
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @param category 카테고리 필터 (null이면 전체 조회)
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en) - 언어 가중치 적용
     * @return 신규 콘텐츠 ID 목록 (언어 가중치가 적용된 최신순 정렬)
     */
    fun findNewContentIds(
        userId: UUID,
        limit: Int,
        excludeIds: List<UUID>,
        category: Category? = null,
        preferredLanguage: String = "en"
    ): Flux<UUID>

    /**
     * 랜덤 콘텐츠 ID 목록 조회
     *
     * 무작위 콘텐츠를 조회하여 다양성을 확보합니다.
     *
     * ### 정렬 공식 (Issue #107: 언어 가중치 적용)
     * ```
     * random_score = RAND()
     *
     * language_multiplier = CASE
     *   WHEN content_language = preferredLanguage THEN 2.0
     *   ELSE 0.5
     * END
     *
     * final_score = random_score * language_multiplier
     * ORDER BY final_score DESC (랜덤하되 선호 언어 우선)
     * ```
     *
     * @param userId 사용자 ID (차단 필터링용)
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @param category 카테고리 필터 (null이면 전체 조회)
     * @param preferredLanguage 사용자 선호 언어 (ISO 639-1, 예: ko, en) - 언어 가중치 적용
     * @return 랜덤 콘텐츠 ID 목록 (언어 가중치가 적용된 무작위 정렬)
     */
    fun findRandomContentIds(
        userId: UUID,
        limit: Int,
        excludeIds: List<UUID>,
        category: Category? = null,
        preferredLanguage: String = "en"
    ): Flux<UUID>

    /**
     * 콘텐츠 ID 목록 기반 상세 정보 조회
     *
     * 추천 알고리즘에서 받은 콘텐츠 ID 목록으로 상세 정보를 조회합니다.
     * ID 목록의 순서를 유지하여 반환합니다.
     *
     * @param userId 사용자 ID (좋아요/저장 상태 조회용)
     * @param contentIds 콘텐츠 ID 목록 (순서 유지)
     * @return 피드 아이템 목록 (입력 순서 유지)
     */
    fun findByContentIds(userId: UUID, contentIds: List<UUID>): Flux<FeedItemResponse>

    /**
     * 콘텐츠 ID 목록의 카테고리 조회 (사용자 선호도 분석용)
     *
     * @param contentIds 콘텐츠 ID 목록
     * @return 카테고리 목록 (중복 포함)
     */
    fun findCategoriesByContentIds(contentIds: List<UUID>): Flux<String>

    /**
     * 팔로잉 콘텐츠 ID 목록 조회 (특정 카테고리)
     *
     * 사용자가 팔로우한 크리에이터의 최신 콘텐츠 중 특정 카테고리만 조회합니다.
     * 카테고리별 추천 알고리즘에서 사용됩니다.
     *
     * @param userId 사용자 ID
     * @param category 조회할 카테고리
     * @param limit 조회할 항목 수
     * @param excludeIds 제외할 콘텐츠 ID 목록
     * @return 팔로잉 콘텐츠 ID 목록 (최신순 정렬)
     */
    fun findFollowingContentIdsByCategory(
        userId: UUID,
        category: Category,
        limit: Int,
        excludeIds: List<UUID>
    ): Flux<UUID>
}
