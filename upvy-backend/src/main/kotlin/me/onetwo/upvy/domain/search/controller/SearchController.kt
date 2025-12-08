package me.onetwo.upvy.domain.search.controller

import jakarta.validation.Valid
import me.onetwo.upvy.domain.search.dto.AutocompleteRequest
import me.onetwo.upvy.domain.search.dto.AutocompleteResponse
import me.onetwo.upvy.domain.search.dto.ContentSearchRequest
import me.onetwo.upvy.domain.search.dto.ContentSearchResponse
import me.onetwo.upvy.domain.search.dto.SearchHistoryResponse
import me.onetwo.upvy.domain.search.dto.TrendingSearchResponse
import me.onetwo.upvy.domain.search.dto.UserSearchRequest
import me.onetwo.upvy.domain.search.dto.UserSearchResponse
import me.onetwo.upvy.domain.search.service.SearchService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * 검색 Controller
 *
 * 인스타그램 스타일의 콘텐츠 및 사용자 검색 API를 제공합니다.
 *
 * ## API 엔드포인트
 * - GET /api/v1/search/contents - 콘텐츠 검색
 * - GET /api/v1/search/users - 사용자 검색
 * - GET /api/v1/search/autocomplete - 자동완성
 * - GET /api/v1/search/trending - 인기 검색어
 * - GET /api/v1/search/history - 검색 기록 조회
 * - DELETE /api/v1/search/history/{keyword} - 특정 검색어 삭제
 * - DELETE /api/v1/search/history - 전체 검색 기록 삭제
 *
 * @property searchService 검색 Service
 */
@RestController
@RequestMapping(ApiPaths.API_V1_SEARCH)
class SearchController(
    private val searchService: SearchService
) {

    /**
     * 콘텐츠 검색
     *
     * 콘텐츠 제목, 설명, 태그, 크리에이터 이름에서 검색합니다.
     *
     * @param request 콘텐츠 검색 요청
     * @param principal 현재 사용자 (선택)
     * @return 콘텐츠 검색 응답
     */
    @GetMapping("/contents")
    fun searchContents(
        @Valid request: ContentSearchRequest,
        principal: Mono<Principal>?
    ): Mono<ResponseEntity<ContentSearchResponse>> {
        logger.debug("Searching contents: query={}", request.q)

        return (principal?.toUserId() ?: Mono.empty())
            .flatMap { userId ->
                searchService.searchContents(request, userId)
            }
            .switchIfEmpty(
                searchService.searchContents(request, null)
            )
            .map { ResponseEntity.ok(it) }
            .doOnSuccess {
                logger.debug("Content search completed")
            }
    }

    /**
     * 사용자 검색
     *
     * 사용자 닉네임에서 검색합니다.
     *
     * @param request 사용자 검색 요청
     * @param principal 현재 사용자 (선택)
     * @return 사용자 검색 응답
     */
    @GetMapping("/users")
    fun searchUsers(
        @Valid request: UserSearchRequest,
        principal: Mono<Principal>?
    ): Mono<ResponseEntity<UserSearchResponse>> {
        logger.debug("Searching users: query={}", request.q)

        return (principal?.toUserId() ?: Mono.empty())
            .flatMap { userId ->
                searchService.searchUsers(request, userId)
            }
            .switchIfEmpty(
                searchService.searchUsers(request, null)
            )
            .map { ResponseEntity.ok(it) }
            .doOnSuccess {
                logger.debug("User search completed")
            }
    }

    /**
     * 자동완성
     *
     * 입력 중인 키워드에 대한 자동완성 제안을 반환합니다.
     *
     * @param request 자동완성 요청
     * @return 자동완성 응답
     */
    @GetMapping("/autocomplete")
    fun autocomplete(
        @Valid request: AutocompleteRequest
    ): Mono<ResponseEntity<AutocompleteResponse>> {
        logger.debug("Autocomplete: query={}", request.q)

        return searchService.autocomplete(request)
            .map { ResponseEntity.ok(it) }
            .doOnSuccess {
                logger.debug("Autocomplete completed")
            }
    }

    /**
     * 인기 검색어
     *
     * 현재 인기 있는 검색 키워드 목록을 반환합니다.
     *
     * @param limit 인기 검색어 개수 (기본값: 10)
     * @return 인기 검색어 응답
     */
    @GetMapping("/trending")
    fun getTrendingKeywords(
        @RequestParam(defaultValue = "10") limit: Int
    ): Mono<ResponseEntity<TrendingSearchResponse>> {
        logger.debug("Getting trending keywords: limit={}", limit)

        return searchService.getTrendingKeywords(limit)
            .map { ResponseEntity.ok(it) }
            .doOnSuccess {
                logger.debug("Trending keywords completed")
            }
    }

    /**
     * 검색 기록 조회
     *
     * 사용자의 최근 검색어 목록을 반환합니다.
     *
     * @param principal 현재 사용자 (필수)
     * @param limit 최대 개수 (기본값: 10)
     * @return 검색 기록 응답
     */
    @GetMapping("/history")
    fun getSearchHistory(
        @RequestParam(defaultValue = "10") limit: Int,
        principal: Mono<Principal>
    ): Mono<ResponseEntity<SearchHistoryResponse>> {
        logger.debug("Getting search history: limit={}", limit)

        return principal
            .toUserId()
            .flatMap { userId ->
                searchService.getRecentSearches(userId, limit)
            }
            .map { ResponseEntity.ok(it) }
            .doOnSuccess {
                logger.debug("Search history completed")
            }
    }

    /**
     * 특정 검색어 삭제
     *
     * @param keyword 검색 키워드
     * @param principal 현재 사용자 (필수)
     * @return 204 No Content
     */
    @DeleteMapping("/history/{keyword}")
    fun deleteSearchHistory(
        @PathVariable keyword: String,
        principal: Mono<Principal>
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                searchService.deleteSearchHistory(userId, keyword)
                    .doOnSuccess { logger.debug("Search history deleted: keyword={}", keyword) }
            }
            .thenReturn(ResponseEntity.noContent().build())
    }

    /**
     * 전체 검색 기록 삭제
     *
     * @param principal 현재 사용자 (필수)
     * @return 204 No Content
     */
    @DeleteMapping("/history")
    fun deleteAllSearchHistory(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                searchService.deleteAllSearchHistory(userId)
                    .doOnSuccess { logger.debug("All search history deleted for userId: {}", userId) }
            }
            .thenReturn(ResponseEntity.noContent().build())
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SearchController::class.java)
    }
}
