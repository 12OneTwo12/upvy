package me.onetwo.growsnap.domain.feed.controller

import me.onetwo.growsnap.domain.feed.dto.FeedResponse
import me.onetwo.growsnap.domain.feed.service.FeedCacheService
import me.onetwo.growsnap.domain.feed.service.FeedService
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageRequest
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

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
@RequestMapping("/api/v1/feed")
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
     * @param userId 인증된 사용자 ID (Spring Security에서 자동 주입)
     * @param cursor 커서 (마지막 조회 콘텐츠 ID, null이면 첫 페이지)
     * @param limit 페이지당 항목 수 (기본값: 20, 최대: 100)
     * @return 피드 응답 (200 OK)
     */
    @GetMapping
    fun getMainFeed(
        principal: Mono<Principal>,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false, defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<FeedResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                val pageRequest = CursorPageRequest(cursor = cursor, limit = limit)
                feedService.getMainFeed(userId, pageRequest)
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
}
