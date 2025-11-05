package me.onetwo.growsnap.domain.search.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.domain.feed.repository.FeedRepository
import me.onetwo.growsnap.domain.search.dto.AutocompleteRequest
import me.onetwo.growsnap.domain.search.dto.AutocompleteSuggestion
import me.onetwo.growsnap.domain.search.dto.ContentSearchRequest
import me.onetwo.growsnap.domain.search.dto.SuggestionType
import me.onetwo.growsnap.domain.search.dto.UserSearchRequest
import me.onetwo.growsnap.domain.search.model.SearchSortType
import me.onetwo.growsnap.domain.search.repository.SearchRepository
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
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
    private val feedRepository: FeedRepository = mockk()
    private val dslContext: DSLContext = mockk(relaxed = true)

    private val searchService: SearchService = SearchServiceImpl(
        searchRepository = searchRepository,
        feedRepository = feedRepository,
        dslContext = dslContext
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
    }

    @Nested
    @DisplayName("searchUsers - 사용자 검색")
    inner class SearchUsers {

        @Test
        @DisplayName("검색 키워드로 사용자를 검색하면, UserSearchResponse를 반환한다")
        fun searchUsers_WithQuery_ReturnsUserSearchResponse() {
            // Given: 사용자 ID 목록
            val query = "홍길동"
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()

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

            // When: 사용자 검색
            val result = searchService.searchUsers(request, null)

            // Then: UserSearchResponse 반환
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response).isNotNull()
                }
                .verifyComplete()

            verify(exactly = 1) {
                searchRepository.searchUsers(
                    query = query,
                    cursor = null,
                    limit = 20
                )
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
}
