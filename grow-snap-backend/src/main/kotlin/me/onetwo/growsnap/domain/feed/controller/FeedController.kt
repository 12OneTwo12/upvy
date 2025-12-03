package me.onetwo.growsnap.domain.feed.controller

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.feed.dto.FeedResponse
import me.onetwo.growsnap.domain.feed.service.FeedCacheService
import me.onetwo.growsnap.domain.feed.service.FeedService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageRequest
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import me.onetwo.growsnap.infrastructure.security.util.toUserIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * 피드 컨트롤러
 *
 * 피드 조회 및 새로고침 API를 제공합니다.
 * 요구사항 명세서 섹션 2.2.1 스마트 피드 API를 구현합니다.
 *
 * @property feedService 피드 서비스
 * @property feedCacheService 피드 캐시 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1_FEED)
class FeedController(
    private val feedService: FeedService,
    private val feedCacheService: FeedCacheService
) {

    /**
     * 메인 피드 조회
     *
     * 추천 알고리즘 기반으로 사용자에게 맞춤화된 피드를 제공합니다.
     * 최근 본 콘텐츠는 자동으로 제외됩니다.
     *
     * ### 요구사항
     * - 커서 기반 페이지네이션 지원
     * - 무한 스크롤 지원
     * - 중복 콘텐츠 방지
     *
     * ### Issue #107: 언어 기반 가중치 적용
     * - 사용자 선호 언어에 따라 콘텐츠에 가중치 적용 (일치: 2.0x, 불일치: 0.5x)
     * - 프론트엔드는 사용자의 현재 언어 설정을 필수로 전달 (예: ko, en)
     * - 기본값: "en" (영어)
     *
     * @param userId 인증된 사용자 ID (Spring Security에서 자동 주입)
     * @param cursor 커서 (마지막 조회 콘텐츠 ID, null이면 첫 페이지)
     * @param limit 페이지당 항목 수 (기본값: 20, 최대: 100)
     * @param language 사용자 선호 언어 (ISO 639-1, 예: ko, en) - 기본값: "en"
     * @return 피드 응답 (200 OK)
     */
    @GetMapping
    fun getMainFeed(
        principal: Mono<Principal>,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "en") language: String
    ): Mono<ResponseEntity<FeedResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                val pageRequest = CursorPageRequest(cursor = cursor, limit = limit)
                feedService.getMainFeed(userId, pageRequest, language)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * 팔로잉 피드 조회
     *
     * 사용자가 팔로우한 크리에이터의 최신 콘텐츠를 제공합니다.
     *
     * ### 요구사항
     * - 커서 기반 페이지네이션 지원
     * - 최신 콘텐츠 우선 정렬
     *
     * @param userId 인증된 사용자 ID (Spring Security에서 자동 주입)
     * @param cursor 커서 (마지막 조회 콘텐츠 ID, null이면 첫 페이지)
     * @param limit 페이지당 항목 수 (기본값: 20, 최대: 100)
     * @return 피드 응답 (200 OK)
     */
    @GetMapping("/following")
    fun getFollowingFeed(
        principal: Mono<Principal>,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<FeedResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                val pageRequest = CursorPageRequest(cursor = cursor, limit = limit)
                feedService.getFollowingFeed(userId, pageRequest)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * 피드 새로고침
     *
     * 사용자의 추천 피드 캐시를 삭제하여 다음 조회 시 최신 추천을 제공합니다.
     * TikTok/Instagram Reels의 "Pull to Refresh" 기능과 동일합니다.
     *
     * ### 처리 흐름
     * 1. Redis에서 사용자의 모든 추천 배치 삭제
     * 2. 다음 GET /api/v1/feed 호출 시 새로운 추천 알고리즘 실행
     * 3. 새로운 250개 배치 생성
     *
     * ### 요구사항
     * - 인증된 사용자만 호출 가능
     * - Redis 캐시만 삭제 (성능 영향 최소화)
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @return 204 No Content
     */
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun refreshFeed(
        principal: Mono<Principal>
    ): Mono<Void> {
        return principal
            .toUserId()
            .flatMap { userId ->
                feedCacheService.clearUserCache(userId)
            }
            .then()
    }

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
     * ### 요구사항
     * - 커서 기반 페이지네이션 지원 (offset 방식)
     * - 선택적 인증 (Principal은 Optional)
     * - 인증되지 않은 사용자는 좋아요/저장 상태가 모두 false로 표시됨
     * - 무한 스크롤 지원
     *
     * ### URL 예시
     * - GET /api/v1/feed/categories/PROGRAMMING?cursor=0&limit=20
     * - GET /api/v1/feed/categories/DESIGN?limit=20
     *
     * @param principal 인증된 사용자 Principal (Optional, Spring Security에서 자동 주입)
     * @param category 조회할 카테고리 (PathVariable)
     * @param cursor 커서 (offset으로 해석, null이면 0)
     * @param limit 페이지당 항목 수 (기본값: 20, 최대: 100)
     * @return 피드 응답 (200 OK)
     */
    @GetMapping("/categories/{category}")
    fun getCategoryFeed(
        principal: Mono<Principal>,
        @PathVariable category: Category,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<FeedResponse>> {
        return principal
            .toUserIdOrNull()
            .flatMap { userId ->
                val pageRequest = CursorPageRequest(cursor = cursor, limit = limit)
                feedService.getCategoryFeed(
                    userId = userId,
                    category = category,
                    pageRequest = pageRequest
                )
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * 카테고리 피드 새로고침
     *
     * 사용자의 특정 카테고리 피드 캐시를 삭제하여 다음 조회 시 최신 추천을 제공합니다.
     * TikTok/Instagram Reels의 "Pull to Refresh" 기능과 동일하게,
     * 카테고리별로 독립적인 새로고침을 지원합니다.
     *
     * ### 처리 흐름
     * 1. Redis에서 사용자의 해당 카테고리 배치 삭제
     * 2. 다음 GET /api/v1/feed/categories/{category} 호출 시 새로운 추천 알고리즘 실행
     * 3. 새로운 250개 배치 생성
     *
     * ### 요구사항
     * - 인증된 사용자만 호출 가능
     * - Redis 캐시만 삭제 (성능 영향 최소화)
     * - 카테고리별로 독립적인 캐시 관리
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param category 새로고침할 카테고리 (PathVariable)
     * @return 204 No Content
     */
    @PostMapping("/categories/{category}/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun refreshCategoryFeed(
        principal: Mono<Principal>,
        @PathVariable category: Category
    ): Mono<Void> {
        return principal
            .toUserId()
            .flatMap { userId ->
                feedCacheService.clearCategoryCache(userId, category)
            }
            .then()
    }
}
