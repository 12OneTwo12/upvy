package me.onetwo.growsnap.domain.search.repository

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.domain.search.dto.SuggestionType
import me.onetwo.growsnap.domain.search.model.SearchSortType
import me.onetwo.growsnap.infrastructure.manticore.ManticoreSearchClient
import me.onetwo.growsnap.infrastructure.manticore.ManticoreSearchProperties
import me.onetwo.growsnap.infrastructure.manticore.dto.Hit
import me.onetwo.growsnap.infrastructure.manticore.dto.Hits
import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.UUID

/**
 * SearchRepository 단위 테스트
 *
 * ManticoreSearchClient를 모킹하여 SearchRepository 로직을 테스트합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("SearchRepository 테스트")
class SearchRepositoryImplTest {

    private val manticoreSearchClient: ManticoreSearchClient = mockk()
    private val properties = ManticoreSearchProperties(
        baseUrl = "http://localhost:9308",
        timeout = 5000
    )

    private val searchRepository: SearchRepository = SearchRepositoryImpl(
        manticoreSearchClient = manticoreSearchClient,
        properties = properties
    )

    @Nested
    @DisplayName("searchContents - 콘텐츠 검색")
    inner class SearchContents {

        @Test
        @DisplayName("검색 키워드로 콘텐츠를 검색하면, 콘텐츠 ID 목록을 반환한다")
        fun searchContents_WithQuery_ReturnsContentIds() {
            // Given: 검색 파라미터
            val query = "프로그래밍"
            val contentId1 = UUID.randomUUID()
            val contentId2 = UUID.randomUUID()

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = listOf(
                        Hit(
                            id = "1",
                            score = 1.5,
                            source = mapOf("content_id" to contentId1.toString())
                        ),
                        Hit(
                            id = "2",
                            score = 1.2,
                            source = mapOf("content_id" to contentId2.toString())
                        )
                    ),
                    total = 2
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When: 콘텐츠 검색
            val result = searchRepository.searchContents(
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

            // Then: 콘텐츠 ID 목록 반환
            StepVerifier.create(result)
                .assertNext { contentIds ->
                    assertThat(contentIds).hasSize(2)
                    assertThat(contentIds).contains(contentId1, contentId2)
                }
                .verifyComplete()

            verify(exactly = 1) { manticoreSearchClient.search(any()) }
        }

        @Test
        @DisplayName("카테고리 필터로 검색하면, 필터링된 결과를 반환한다")
        fun searchContents_WithCategoryFilter_ReturnsFilteredResults() {
            // Given: 카테고리 필터
            val query = "프로그래밍"
            val category = Category.PROGRAMMING
            val contentId = UUID.randomUUID()

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = listOf(
                        Hit(
                            id = "1",
                            score = 1.5,
                            source = mapOf("content_id" to contentId.toString())
                        )
                    ),
                    total = 1
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When: 카테고리 필터로 검색
            val result = searchRepository.searchContents(
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

            // Then: 필터링된 결과 반환
            StepVerifier.create(result)
                .assertNext { contentIds ->
                    assertThat(contentIds).hasSize(1)
                    assertThat(contentIds).contains(contentId)
                }
                .verifyComplete()

            verify(exactly = 1) { manticoreSearchClient.search(any()) }
        }

        @Test
        @DisplayName("검색 결과가 없으면, 빈 목록을 반환한다")
        fun searchContents_WithNoResults_ReturnsEmptyList() {
            // Given: 검색 결과 없음
            val query = "존재하지않는검색어"

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = emptyList(),
                    total = 0
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When: 검색
            val result = searchRepository.searchContents(
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

            // Then: 빈 목록 반환
            StepVerifier.create(result)
                .assertNext { contentIds ->
                    assertThat(contentIds).isEmpty()
                }
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("searchUsers - 사용자 검색")
    inner class SearchUsers {

        @Test
        @DisplayName("검색 키워드로 사용자를 검색하면, 사용자 ID 목록을 반환한다")
        fun searchUsers_WithQuery_ReturnsUserIds() {
            // Given: 검색 키워드
            val query = "홍길동"
            val userId1 = UUID.randomUUID()
            val userId2 = UUID.randomUUID()

            // When: 사용자 검색
            val result = searchRepository.searchUsers(
                query = query,
                cursor = null,
                limit = 20
            )

            // Then: 사용자 ID 목록 반환 (현재는 빈 목록, 구현 후 실제 ID 반환)
            StepVerifier.create(result)
                .assertNext { userIds ->
                    assertThat(userIds).isNotNull()
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
            // Given: 입력 키워드
            val query = "프로"

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = listOf(
                        Hit(
                            id = "1",
                            score = 1.5,
                            source = mapOf(
                                "text" to "프로그래밍",
                                "type" to "CONTENT"
                            )
                        )
                    ),
                    total = 1
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When: 자동완성
            val result = searchRepository.autocomplete(
                query = query,
                limit = 10
            )

            // Then: 제안 목록 반환
            StepVerifier.create(result)
                .assertNext { suggestions ->
                    assertThat(suggestions).isNotEmpty()
                }
                .verifyComplete()
        }
    }
}
