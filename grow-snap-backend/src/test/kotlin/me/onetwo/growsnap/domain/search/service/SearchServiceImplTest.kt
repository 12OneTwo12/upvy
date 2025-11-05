package me.onetwo.growsnap.domain.search.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.domain.search.dto.AutocompleteRequest
import me.onetwo.growsnap.domain.search.dto.AutocompleteSuggestion
import me.onetwo.growsnap.domain.search.dto.ContentSearchRequest
import me.onetwo.growsnap.domain.search.dto.SuggestionType
import me.onetwo.growsnap.domain.search.dto.UserSearchRequest
import me.onetwo.growsnap.domain.search.model.SearchSortType
import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.domain.search.repository.SearchRepository
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.UUID

/**
 * SearchService 단위 테스트
 *
 * ## Given-When-Then 패턴
 * - Given: Repository 모킹
 * - When: Service 메서드 호출
 * - Then: StepVerifier로 Reactive 타입 검증
 */
@ExtendWith(MockKExtension::class)
@DisplayName("SearchService 테스트")
class SearchServiceImplTest {

    private val searchRepository: SearchRepository = mockk()
    private val searchHistoryRepository: me.onetwo.growsnap.domain.search.repository.SearchHistoryRepository = mockk()
    private val userProfileRepository: UserProfileRepository = mockk()
    private val eventPublisher: ReactiveEventPublisher = mockk(relaxed = true)

    private val searchService: SearchService = SearchServiceImpl(
        searchRepository = searchRepository,
        searchHistoryRepository = searchHistoryRepository,
        userProfileRepository = userProfileRepository,
        eventPublisher = eventPublisher
    )

    @Nested
    @DisplayName("searchContents - 콘텐츠 검색")
    inner class SearchContents {

        @Test
        @DisplayName("검색 키워드로 콘텐츠를 검색하면, ContentSearchResponse를 반환한다")
        fun searchContents_WithQuery_ReturnsContentSearchResponse() {
            // Given: 콘텐츠 ID 목록
            val query = "프로그래밍"
            val userId = UUID.randomUUID()
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()

            val request = ContentSearchRequest(
                q = query,
                category = null,
                difficulty = null,
                minDuration = null,
                maxDuration = null,
                startDate = null,
                endDate = null,
                language = null,
                sortBy = SearchSortType.RELEVANCE,
                cursor = null,
                limit = 20
            )

            every {
                searchRepository.searchContents(
                    query = query,
                    category = null,
                    difficulty = null,
                    minDuration = null,
                    maxDuration = null,
                    startDate = null,
                    endDate = null,
                    language = null,
                    sortBy = SearchSortType.RELEVANCE,
                    cursor = null,
                    limit = 20
                )
            } returns Mono.just(listOf(contentId1, contentId2))

            // When: 콘텐츠 검색
            val result = searchService.searchContents(request, userId)

            // Then: ContentSearchResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response).isNotNull()
                    // TODO: FeedItemResponse 조회 구현 후 검증
                }
                .verifyComplete()

            verify(exactly = 1) {
                searchRepository.searchContents(
                    query = query,
                    category = null,
                    difficulty = null,
                    minDuration = null,
                    maxDuration = null,
                    startDate = null,
                    endDate = null,
                    language = null,
                    sortBy = SearchSortType.RELEVANCE,
                    cursor = null,
                    limit = 20
                )
            }
        }

        @Test
        @DisplayName("검색 결과가 없으면, 빈 응답을 반환한다")
        fun searchContents_WithNoResults_ReturnsEmptyResponse() {
            // Given: 검색 결과 없음
            val query = "존재하지않는검색어"
            val request = ContentSearchRequest(
                q = query,
                category = null,
                difficulty = null,
                minDuration = null,
                maxDuration = null,
                startDate = null,
                endDate = null,
                language = null,
                sortBy = SearchSortType.RELEVANCE,
                cursor = null,
                limit = 20
            )

            every {
                searchRepository.searchContents(
                    query = query,
                    category = null,
                    difficulty = null,
                    minDuration = null,
                    maxDuration = null,
                    startDate = null,
                    endDate = null,
                    language = null,
                    sortBy = SearchSortType.RELEVANCE,
                    cursor = null,
                    limit = 20
                )
            } returns Mono.just(emptyList())

            // When: 콘텐츠 검색
            val result = searchService.searchContents(request, null)

            // Then: 빈 응답 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).isEmpty()
                    assertThat(response.hasNext).isFalse()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("카테고리 필터로 검색하면, 필터링된 결과를 반환한다")
        fun searchContents_WithCategoryFilter_ReturnsFilteredResults() {
            // Given: 카테고리 필터
            val query = "프로그래밍"
            val category = Category.PROGRAMMING
            val contentId = UUID.randomUUID()

            val request = ContentSearchRequest(
                q = query,
                category = category,
                difficulty = null,
                minDuration = null,
                maxDuration = null,
                startDate = null,
                endDate = null,
                language = null,
                sortBy = SearchSortType.RELEVANCE,
                cursor = null,
                limit = 20
            )

            every {
                searchRepository.searchContents(
                    query = query,
                    category = category,
                    difficulty = null,
                    minDuration = null,
                    maxDuration = null,
                    startDate = null,
                    endDate = null,
                    language = null,
                    sortBy = SearchSortType.RELEVANCE,
                    cursor = null,
                    limit = 20
                )
            } returns Mono.just(listOf(contentId))

            // When: 카테고리 필터로 검색
            val result = searchService.searchContents(request, null)

            // Then: 필터링된 결과 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response).isNotNull()
                }
                .verifyComplete()

            verify(exactly = 1) {
                searchRepository.searchContents(
                    query = query,
                    category = category,
                    difficulty = null,
                    minDuration = null,
                    maxDuration = null,
                    startDate = null,
                    endDate = null,
                    language = null,
                    sortBy = SearchSortType.RELEVANCE,
                    cursor = null,
                    limit = 20
                )
            }
        }

        @Test
        @DisplayName("비인증 사용자(userId=null)가 검색하면, 검색 결과를 반환한다")
        fun searchContents_WithUnauthenticatedUser_ReturnsResults() {
            // Given: 비인증 사용자 (userId = null)
            val query = "테스트"
            val contentId = UUID.randomUUID()

            val request = ContentSearchRequest(
                q = query,
                category = null,
                difficulty = null,
                minDuration = null,
                maxDuration = null,
                startDate = null,
                endDate = null,
                language = null,
                sortBy = SearchSortType.RELEVANCE,
                cursor = null,
                limit = 20
            )

            every {
                searchRepository.searchContents(
                    query = query,
                    category = null,
                    difficulty = null,
                    minDuration = null,
                    maxDuration = null,
                    startDate = null,
                    endDate = null,
                    language = null,
                    sortBy = SearchSortType.RELEVANCE,
                    cursor = null,
                    limit = 20
                )
            } returns Mono.just(listOf(contentId))

            // When: 비인증 사용자로 검색 (userId = null)
            val result = searchService.searchContents(request, null)

            // Then: 검색 결과 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response).isNotNull()
                    // TODO: FeedItemResponse 조회 구현 후 상세 검증
                }
                .verifyComplete()

            // 검색 기록은 저장되지 않음 (userId가 null이므로)
            verify(exactly = 0) { searchHistoryRepository.save(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("searchUsers - 사용자 검색")
    inner class SearchUsers {

        @Test
        @DisplayName("검색 키워드로 사용자를 검색하면, UserSearchResponse를 반환한다")
        fun searchUsers_WithQuery_ReturnsUserSearchResponse() {
            // Given: 사용자 ID 목록 및 프로필
            val query = "홍길동"
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()

            val userProfile1 = UserProfile(
                id = 1L,
                userId = userId1,
                nickname = "홍길동",
                profileImageUrl = null,
                bio = "안녕하세요",
                followerCount = 10,
                followingCount = 5,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            val userProfile2 = UserProfile(
                id = 2L,
                userId = userId2,
                nickname = "김홍길동",
                profileImageUrl = null,
                bio = null,
                followerCount = 20,
                followingCount = 10,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            val request = UserSearchRequest(
                q = query,
                cursor = null,
                limit = 20
            )

            every {
                searchRepository.searchUsers(
                    query = query,
                    cursor = null,
                    limit = 20
                )
            } returns Mono.just(listOf(userId1, userId2))

            every {
                userProfileRepository.findByUserIds(setOf(userId1, userId2))
            } returns Flux.just(userProfile1, userProfile2)

            // When: 사용자 검색
            val result = searchService.searchUsers(request, null)

            // Then: UserSearchResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response).isNotNull()
                    assertThat(response.content).hasSize(2)
                    assertThat(response.content[0].nickname).isEqualTo("홍길동")
                    assertThat(response.content[1].nickname).isEqualTo("김홍길동")
                }
                .verifyComplete()

            verify(exactly = 1) {
                searchRepository.searchUsers(
                    query = query,
                    cursor = null,
                    limit = 20
                )
            }

            verify(exactly = 1) {
                userProfileRepository.findByUserIds(setOf(userId1, userId2))
            }
        }

        @Test
        @DisplayName("검색 결과가 없으면, 빈 응답을 반환한다")
        fun searchUsers_WithNoResults_ReturnsEmptyResponse() {
            // Given: 검색 결과 없음
            val query = "존재하지않는사용자"
            val request = UserSearchRequest(
                q = query,
                cursor = null,
                limit = 20
            )

            every {
                searchRepository.searchUsers(
                    query = query,
                    cursor = null,
                    limit = 20
                )
            } returns Mono.just(emptyList())

            // When: 사용자 검색
            val result = searchService.searchUsers(request, null)

            // Then: 빈 응답 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.content).isEmpty()
                    assertThat(response.hasNext).isFalse()
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("비인증 사용자(userId=null)가 검색하면, 검색 결과를 반환한다")
        fun searchUsers_WithUnauthenticatedUser_ReturnsResults() {
            // Given: 비인증 사용자 (userId = null)
            val query = "테스트"
            val userId1 = UUID.randomUUID()
            val userProfile1 = UserProfile(
                userId = userId1,
                nickname = "테스터",
                profileImageUrl = null,
                bio = null
            )

            val request = UserSearchRequest(
                q = query,
                cursor = null,
                limit = 20
            )

            every {
                searchRepository.searchUsers(
                    query = query,
                    cursor = null,
                    limit = 20
                )
            } returns Mono.just(listOf(userId1))

            every {
                userProfileRepository.findByUserIds(setOf(userId1))
            } returns Flux.just(userProfile1)

            // When: 비인증 사용자로 검색 (userId = null)
            val result = searchService.searchUsers(request, null)

            // Then: 검색 결과 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response).isNotNull()
                    assertThat(response.content).isNotEmpty()
                    assertThat(response.content[0].nickname).isEqualTo("테스터")
                }
                .verifyComplete()

            // 검색 기록은 저장되지 않음 (userId가 null이므로)
            verify(exactly = 0) { searchHistoryRepository.save(any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("autocomplete - 자동완성")
    inner class Autocomplete {

        @Test
        @DisplayName("입력 키워드로 자동완성하면, 제안 목록을 반환한다")
        fun autocomplete_WithQuery_ReturnsSuggestions() {
            // Given: 자동완성 제안 목록
            val query = "프로"
            val request = AutocompleteRequest(
                q = query,
                limit = 10
            )

            val suggestions = listOf(
                AutocompleteSuggestion(
                    text = "프로그래밍",
                    type = SuggestionType.CONTENT,
                    highlightedText = "<em>프로</em>그래밍"
                )
            )

            every {
                searchRepository.autocomplete(
                    query = query,
                    limit = 10
                )
            } returns Mono.just(suggestions)

            // When: 자동완성
            val result = searchService.autocomplete(request)

            // Then: 제안 목록 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.suggestions).hasSize(1)
                    assertThat(response.suggestions[0].text).isEqualTo("프로그래밍")
                    assertThat(response.suggestions[0].type).isEqualTo(SuggestionType.CONTENT)
                }
                .verifyComplete()

            verify(exactly = 1) {
                searchRepository.autocomplete(
                    query = query,
                    limit = 10
                )
            }
        }

        @Test
        @DisplayName("제안이 없으면, 빈 응답을 반환한다")
        fun autocomplete_WithNoSuggestions_ReturnsEmptyResponse() {
            // Given: 제안 없음
            val query = "xyz"
            val request = AutocompleteRequest(
                q = query,
                limit = 10
            )

            every {
                searchRepository.autocomplete(
                    query = query,
                    limit = 10
                )
            } returns Mono.just(emptyList())

            // When: 자동완성
            val result = searchService.autocomplete(request)

            // Then: 빈 응답 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.suggestions).isEmpty()
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getTrendingKeywords - 인기 검색어")
    inner class GetTrendingKeywords {

        @Test
        @DisplayName("인기 검색어를 조회하면, TrendingSearchResponse를 반환한다")
        fun getTrendingKeywords_ReturnsResponse() {
            // Given: 인기 검색어 조회
            val limit = 10

            // When: 인기 검색어 조회
            val result = searchService.getTrendingKeywords(limit)

            // Then: TrendingSearchResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response).isNotNull()
                    assertThat(response.keywords).isEmpty()  // TODO: Redis 구현 후 검증
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getRecentSearches - 최근 검색어 조회")
    inner class GetRecentSearches {

        @Test
        @DisplayName("사용자의 최근 검색어를 조회하면, SearchHistoryResponse를 반환한다")
        fun getRecentSearches_ReturnsSearchHistoryResponse() {
            // Given: 검색 기록 Repository 모킹
            val userId = UUID.randomUUID()
            val limit = 10
            val searchHistories = listOf(
                me.onetwo.growsnap.domain.search.model.SearchHistory(
                    id = 1L,
                    userId = userId,
                    keyword = "Java",
                    searchType = SearchType.CONTENT
                ),
                me.onetwo.growsnap.domain.search.model.SearchHistory(
                    id = 2L,
                    userId = userId,
                    keyword = "Kotlin",
                    searchType = SearchType.CONTENT
                )
            )

            every {
                searchHistoryRepository.findRecentByUserId(userId, limit)
            } returns Mono.just(searchHistories)

            // When: 최근 검색어 조회
            val result = searchService.getRecentSearches(userId, limit)

            // Then: SearchHistoryResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.keywords).hasSize(2)
                    assertThat(response.keywords[0].keyword).isEqualTo("Java")
                    assertThat(response.keywords[0].searchType).isEqualTo(SearchType.CONTENT)
                    assertThat(response.keywords[1].keyword).isEqualTo("Kotlin")
                }
                .verifyComplete()

            verify(exactly = 1) { searchHistoryRepository.findRecentByUserId(userId, limit) }
        }

        @Test
        @DisplayName("검색 기록이 없으면, 빈 리스트를 반환한다")
        fun getRecentSearches_WithNoHistory_ReturnsEmptyList() {
            // Given: 검색 기록 없음
            val userId = UUID.randomUUID()
            val limit = 10

            every {
                searchHistoryRepository.findRecentByUserId(userId, limit)
            } returns Mono.just(emptyList())

            // When: 최근 검색어 조회
            val result = searchService.getRecentSearches(userId, limit)

            // Then: 빈 리스트 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.keywords).isEmpty()
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("deleteSearchHistory - 특정 검색어 삭제")
    inner class DeleteSearchHistory {

        @Test
        @DisplayName("특정 검색어를 삭제하면, Mono<Void>를 반환한다")
        fun deleteSearchHistory_DeletesKeyword() {
            // Given: Repository 모킹
            val userId = UUID.randomUUID()
            val keyword = "Java"

            every {
                searchHistoryRepository.deleteByUserIdAndKeyword(userId, keyword)
            } returns Mono.empty()

            // When: 검색어 삭제
            val result = searchService.deleteSearchHistory(userId, keyword)

            // Then: 완료
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { searchHistoryRepository.deleteByUserIdAndKeyword(userId, keyword) }
        }

        @Test
        @DisplayName("Repository에서 에러 발생 시, 에러를 전파한다")
        fun deleteSearchHistory_WhenRepositoryFails_PropagatesError() {
            // Given: Repository 에러
            val userId = UUID.randomUUID()
            val keyword = "Java"

            every {
                searchHistoryRepository.deleteByUserIdAndKeyword(userId, keyword)
            } returns Mono.error(RuntimeException("DB error"))

            // When: 검색어 삭제
            val result = searchService.deleteSearchHistory(userId, keyword)

            // Then: 에러 전파
            StepVerifier.create(result)
                .expectError(RuntimeException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("deleteAllSearchHistory - 전체 검색 기록 삭제")
    inner class DeleteAllSearchHistory {

        @Test
        @DisplayName("전체 검색 기록을 삭제하면, Mono<Void>를 반환한다")
        fun deleteAllSearchHistory_DeletesAllHistory() {
            // Given: Repository 모킹
            val userId = UUID.randomUUID()

            every {
                searchHistoryRepository.deleteAllByUserId(userId)
            } returns Mono.empty()

            // When: 전체 검색 기록 삭제
            val result = searchService.deleteAllSearchHistory(userId)

            // Then: 완료
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { searchHistoryRepository.deleteAllByUserId(userId) }
        }

        @Test
        @DisplayName("Repository에서 에러 발생 시, 에러를 전파한다")
        fun deleteAllSearchHistory_WhenRepositoryFails_PropagatesError() {
            // Given: Repository 에러
            val userId = UUID.randomUUID()

            every {
                searchHistoryRepository.deleteAllByUserId(userId)
            } returns Mono.error(RuntimeException("DB error"))

            // When: 전체 검색 기록 삭제
            val result = searchService.deleteAllSearchHistory(userId)

            // Then: 에러 전파
            StepVerifier.create(result)
                .expectError(RuntimeException::class.java)
                .verify()
        }
    }
}
