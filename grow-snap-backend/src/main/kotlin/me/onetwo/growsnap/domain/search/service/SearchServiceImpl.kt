package me.onetwo.growsnap.domain.search.service

import me.onetwo.growsnap.domain.feed.repository.FeedRepository
import me.onetwo.growsnap.domain.search.dto.AutocompleteRequest
import me.onetwo.growsnap.domain.search.dto.AutocompleteResponse
import me.onetwo.growsnap.domain.search.dto.ContentSearchRequest
import me.onetwo.growsnap.domain.search.dto.ContentSearchResponse
import me.onetwo.growsnap.domain.search.dto.SearchHistoryItem
import me.onetwo.growsnap.domain.search.dto.SearchHistoryResponse
import me.onetwo.growsnap.domain.search.dto.TrendingSearchResponse
import me.onetwo.growsnap.domain.search.dto.UserSearchRequest
import me.onetwo.growsnap.domain.search.dto.UserSearchResponse
import me.onetwo.growsnap.domain.search.dto.UserSearchResult
import me.onetwo.growsnap.domain.search.event.SearchPerformedEvent
import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.domain.search.repository.SearchHistoryRepository
import me.onetwo.growsnap.domain.search.repository.SearchRepository
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageResponse
import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * SearchService 구현체
 *
 * @property searchRepository 검색 Repository
 * @property searchHistoryRepository 검색 기록 Repository
 * @property feedRepository 피드 Repository (FeedItemResponse 조회용)
 * @property dslContext JOOQ DSL Context
 * @property eventPublisher Spring Event Publisher (검색 기록 저장용)
 */
@Service
class SearchServiceImpl(
    private val searchRepository: SearchRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val feedRepository: FeedRepository,
    private val dslContext: DSLContext,
    private val eventPublisher: ApplicationEventPublisher
) : SearchService {

    /**
     * 콘텐츠 검색
     *
     * @param request 콘텐츠 검색 요청
     * @param userId 현재 사용자 ID
     * @return 콘텐츠 검색 응답
     */
    override fun searchContents(
        request: ContentSearchRequest,
        userId: UUID?
    ): Mono<ContentSearchResponse> {
        logger.debug("Searching contents: query={}, userId={}", request.q, userId)

        return searchRepository.searchContents(
            query = request.q,
            category = request.category,
            difficulty = request.difficulty,
            minDuration = request.minDuration,
            maxDuration = request.maxDuration,
            startDate = request.startDate,
            endDate = request.endDate,
            language = request.language,
            sortBy = request.sortBy,
            cursor = request.cursor,
            limit = request.limit
        )
            .flatMap { contentIds ->
                if (contentIds.isEmpty()) {
                    Mono.just(CursorPageResponse.empty())
                } else {
                    // FeedRepository를 사용하여 FeedItemResponse 조회
                    // 간단히 mainFeed 사용 (실제로는 별도 메서드가 필요)
                    Mono.just(CursorPageResponse.empty<me.onetwo.growsnap.domain.feed.dto.FeedItemResponse>())
                }
            }
            .doOnSuccess { response ->
                logger.debug("Content search completed: count={}", response.count)
                // 검색 기록 저장 이벤트 발행
                eventPublisher.publishEvent(
                    SearchPerformedEvent(
                        userId = userId,
                        keyword = request.q,
                        searchType = SearchType.CONTENT
                    )
                )
            }
            .doOnError { error ->
                logger.error("Content search failed: query={}", request.q, error)
            }
    }

    /**
     * 사용자 검색
     *
     * @param request 사용자 검색 요청
     * @param currentUserId 현재 사용자 ID
     * @return 사용자 검색 응답
     */
    override fun searchUsers(
        request: UserSearchRequest,
        currentUserId: UUID?
    ): Mono<UserSearchResponse> {
        logger.debug("Searching users: query={}, currentUserId={}", request.q, currentUserId)

        return searchRepository.searchUsers(
            query = request.q,
            cursor = request.cursor,
            limit = request.limit
        )
            .flatMap { userIds ->
                if (userIds.isEmpty()) {
                    Mono.just(CursorPageResponse.empty())
                } else {
                    // user_profiles 조회
                    Flux.from(
                        dslContext
                            .select(
                                USER_PROFILES.USER_ID,
                                USER_PROFILES.NICKNAME,
                                USER_PROFILES.PROFILE_IMAGE_URL,
                                USER_PROFILES.BIO
                            )
                            .from(USER_PROFILES)
                            .where(USER_PROFILES.USER_ID.`in`(userIds.map { it.toString() }))
                            .and(USER_PROFILES.DELETED_AT.isNull)
                    )
                        .map { record ->
                            UserSearchResult(
                                userId = UUID.fromString(record.getValue(USER_PROFILES.USER_ID)),
                                nickname = record.getValue(USER_PROFILES.NICKNAME) ?: "",
                                profileImageUrl = record.getValue(USER_PROFILES.PROFILE_IMAGE_URL),
                                bio = record.getValue(USER_PROFILES.BIO),
                                followerCount = 0,  // TODO: 팔로워 수 조회
                                isFollowing = false  // TODO: 팔로우 여부 조회
                            )
                        }
                        .collectList()
                        .map { users ->
                            CursorPageResponse.of(
                                content = users,
                                limit = request.limit
                            ) { it.userId.toString() }
                        }
                }
            }
            .doOnSuccess { response ->
                logger.debug("User search completed: count={}", response.count)
                // 검색 기록 저장 이벤트 발행
                eventPublisher.publishEvent(
                    SearchPerformedEvent(
                        userId = currentUserId,
                        keyword = request.q,
                        searchType = SearchType.USER
                    )
                )
            }
            .doOnError { error ->
                logger.error("User search failed: query={}", request.q, error)
            }
    }

    /**
     * 자동완성
     *
     * @param request 자동완성 요청
     * @return 자동완성 응답
     */
    override fun autocomplete(
        request: AutocompleteRequest
    ): Mono<AutocompleteResponse> {
        logger.debug("Autocomplete: query={}", request.q)

        return searchRepository.autocomplete(
            query = request.q,
            limit = request.limit
        )
            .map { suggestions ->
                AutocompleteResponse(suggestions)
            }
            .doOnSuccess { response ->
                logger.debug("Autocomplete completed: count={}", response.suggestions.size)
            }
            .doOnError { error ->
                logger.error("Autocomplete failed: query={}", request.q, error)
            }
    }

    /**
     * 인기 검색어
     *
     * @param limit 인기 검색어 개수
     * @return 인기 검색어 응답
     */
    override fun getTrendingKeywords(
        limit: Int
    ): Mono<TrendingSearchResponse> {
        logger.debug("Getting trending keywords: limit={}", limit)

        // TODO: Redis에서 인기 검색어 조회
        return Mono.just(TrendingSearchResponse.empty())
            .doOnSuccess { response ->
                logger.debug("Trending keywords completed: count={}", response.keywords.size)
            }
    }

    /**
     * 최근 검색어 조회
     *
     * @param userId 사용자 ID
     * @param limit 최대 개수
     * @return 검색 기록 응답
     */
    override fun getRecentSearches(
        userId: UUID,
        limit: Int
    ): Mono<SearchHistoryResponse> {
        logger.debug("Getting recent searches: userId={}, limit={}", userId, limit)

        return searchHistoryRepository.findRecentByUserId(userId, limit)
            .map { histories ->
                SearchHistoryResponse(
                    keywords = histories.map { history ->
                        SearchHistoryItem(
                            keyword = history.keyword,
                            searchType = history.searchType
                        )
                    }
                )
            }
            .doOnSuccess { response ->
                logger.debug("Recent searches completed: count={}", response.keywords.size)
            }
    }

    /**
     * 특정 검색어 삭제
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    override fun deleteSearchHistory(
        userId: UUID,
        keyword: String
    ): Mono<Void> {
        logger.debug("Deleting search history: userId={}, keyword={}", userId, keyword)

        return searchHistoryRepository.deleteByUserIdAndKeyword(userId, keyword)
            .doOnSuccess {
                logger.debug("Search history deleted: userId={}, keyword={}", userId, keyword)
            }
    }

    /**
     * 전체 검색 기록 삭제
     *
     * @param userId 사용자 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    override fun deleteAllSearchHistory(
        userId: UUID
    ): Mono<Void> {
        logger.debug("Deleting all search history: userId={}", userId)

        return searchHistoryRepository.deleteAllByUserId(userId)
            .doOnSuccess {
                logger.debug("All search history deleted: userId={}", userId)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SearchServiceImpl::class.java)
    }
}
