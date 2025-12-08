package me.onetwo.upvy.domain.search.controller
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.manticore.ManticoreSearchClient
import me.onetwo.upvy.infrastructure.manticore.dto.Hit
import me.onetwo.upvy.infrastructure.manticore.dto.Hits
import me.onetwo.upvy.infrastructure.manticore.dto.ManticoreSearchResponse
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.search.model.SearchType
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.util.createUserWithProfile
import me.onetwo.upvy.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * SearchController 통합 테스트
 *
 * ## @SpringBootTest
 * - 전체 Spring 컨텍스트 로드
 * - 실제 Service, Repository 사용
 * - ManticoreSearchClient만 @MockkBean으로 모킹
 *
 * ## REST Docs 작성 금지
 * - 통합 테스트에서는 REST Docs 작성하지 않음
 * - 단위 테스트에서만 작성
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("SearchController 통합 테스트")
class SearchControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var searchHistoryRepository: me.onetwo.upvy.domain.search.repository.SearchHistoryRepository

    @MockkBean
    private lateinit var manticoreSearchClient: ManticoreSearchClient

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 사용자 생성 (UUID로 고유한 이메일 사용)
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        val (user, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "search-controller-test-$uuid@example.com",
            providerId = "test-$uuid"
        )
        testUserId = user.id!!
    }

    @Nested
    @DisplayName("GET /api/v1/search/contents - 콘텐츠 검색")
    inner class SearchContents {

        @Test
        @DisplayName("검색 키워드로 콘텐츠를 검색하면, 200과 검색 결과를 반환한다")
        fun searchContents_WithQuery_Returns200AndResults() {
            // Given: Manticore Search 응답 모킹
            val query = "프로그래밍"
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

            // When & Then: 콘텐츠 검색
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/contents")
                        .queryParam("q", query)
                        .queryParam("sortBy", "RELEVANCE")
                        .queryParam("limit", 20)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.hasNext").exists()
                .jsonPath("$.count").exists()
        }

        @Test
        @DisplayName("카테고리 필터로 검색하면, 필터링된 결과를 반환한다")
        fun searchContents_WithCategoryFilter_ReturnsFilteredResults() {
            // Given: 카테고리 필터
            val query = "프로그래밍"
            val category = Category.PROGRAMMING

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = emptyList(),
                    total = 0
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When & Then: 카테고리 필터로 검색
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/contents")
                        .queryParam("q", query)
                        .queryParam("category", category.name)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
        }

        @Test
        @DisplayName("검색 키워드가 빈 문자열이면, 400을 반환한다")
        fun searchContents_WithEmptyQuery_Returns400() {
            // Given: 빈 검색 키워드
            val query = ""

            // When & Then: 400 응답
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/contents")
                        .queryParam("q", query)
                        .build()
                }
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("인증 없이 검색하면, 200과 검색 결과를 반환한다 (공개 API)")
        fun searchContents_WithoutAuth_Returns200() {
            // Given: Manticore Search 응답 모킹
            val query = "프로그래밍"

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = emptyList(),
                    total = 0
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When & Then: 인증 없이 검색
            webTestClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/contents")
                        .queryParam("q", query)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
        }
    }

    @Nested
    @DisplayName("GET /api/v1/search/users - 사용자 검색")
    inner class SearchUsers {

        @Test
        @DisplayName("검색 키워드로 사용자를 검색하면, 200과 검색 결과를 반환한다")
        fun searchUsers_WithQuery_Returns200AndResults() {
            // Given: 검색 키워드 및 Manticore Search 응답 모킹
            val query = "test"
            val userId1 = UUID.randomUUID()

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = listOf(
                        Hit(
                            id = "1",
                            score = 1.5,
                            source = mapOf("user_id" to userId1.toString())
                        )
                    ),
                    total = 1
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When & Then: 사용자 검색
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/users")
                        .queryParam("q", query)
                        .queryParam("limit", 20)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.hasNext").exists()
                .jsonPath("$.count").exists()
        }

        @Test
        @DisplayName("검색 키워드가 빈 문자열이면, 400을 반환한다")
        fun searchUsers_WithEmptyQuery_Returns400() {
            // Given: 빈 검색 키워드
            val query = ""

            // When & Then: 400 응답
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/users")
                        .queryParam("q", query)
                        .build()
                }
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @Nested
    @DisplayName("GET /api/v1/search/autocomplete - 자동완성")
    inner class Autocomplete {

        @Test
        @DisplayName("입력 키워드로 자동완성하면, 200과 제안 목록을 반환한다")
        fun autocomplete_WithQuery_Returns200AndSuggestions() {
            // Given: Manticore Search 응답 모킹
            val query = "프로"

            val manticoreResponse = ManticoreSearchResponse(
                hits = Hits(
                    hits = emptyList(),
                    total = 0
                )
            )

            every { manticoreSearchClient.search(any()) } returns Mono.just(manticoreResponse)

            // When & Then: 자동완성
            webTestClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/autocomplete")
                        .queryParam("q", query)
                        .queryParam("limit", 10)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.suggestions").isArray
        }
    }

    @Nested
    @DisplayName("GET /api/v1/search/trending - 인기 검색어")
    inner class GetTrendingKeywords {

        @Test
        @DisplayName("인기 검색어를 조회하면, 200과 인기 검색어 목록을 반환한다")
        fun getTrendingKeywords_Returns200AndKeywords() {
            // When & Then: 인기 검색어 조회
            webTestClient
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("${ApiPaths.API_V1_SEARCH}/trending")
                        .queryParam("limit", 10)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.keywords").isArray
        }
    }

    @Nested
    @DisplayName("GET /api/v1/search/history - 검색 기록 조회")
    inner class GetSearchHistory {

        @Test
        @DisplayName("사용자의 검색 기록을 조회하면, 200과 검색 기록을 반환한다")
        fun getSearchHistory_WithSearchHistory_ReturnsHistory() {
            // Given: 검색 기록 저장 (setUp()에서 생성된 testUserId 사용)
            searchHistoryRepository.save(testUserId, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(testUserId, "홍길동", SearchType.USER).block()

            // When & Then: 검색 기록 조회
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri("${ApiPaths.API_V1_SEARCH}/history?limit=10")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.keywords").isArray
                .jsonPath("$.keywords[0].keyword").isEqualTo("홍길동")  // 최신순
                .jsonPath("$.keywords[0].searchType").isEqualTo("USER")
                .jsonPath("$.keywords[1].keyword").isEqualTo("Java")
                .jsonPath("$.keywords[1].searchType").isEqualTo("CONTENT")
        }

        @Test
        @DisplayName("검색 기록이 없으면, 빈 리스트를 반환한다")
        fun getSearchHistory_WithNoHistory_ReturnsEmptyList() {
            // Given: 사용자 생성 (검색 기록 없음)
            val (user, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "test2@example.com",
                providerId = "google-456"
            )

            // When & Then: 검색 기록 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_SEARCH}/history")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.keywords").isEmpty
        }

        @Test
        @DisplayName("동일한 키워드는 가장 최근 검색만 반환한다")
        fun getSearchHistory_WithDuplicateKeyword_ReturnsOnlyLatest() {
            // Given: 사용자 생성 및 동일한 키워드로 여러 번 검색
            val (user, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "test3@example.com",
                providerId = "google-789"
            )

            // 동일한 키워드로 여러 번 검색
            searchHistoryRepository.save(user.id!!, "Kotlin", SearchType.CONTENT).block()
            searchHistoryRepository.save(user.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(user.id!!, "Kotlin", SearchType.CONTENT).block()

            // When & Then: 검색 기록 조회 (Kotlin은 1개만 반환됨)
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_SEARCH}/history")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.keywords").isArray
                .jsonPath("$.keywords.length()").isEqualTo(2)
                .jsonPath("$.keywords[0].keyword").isEqualTo("Kotlin")  // 최신
                .jsonPath("$.keywords[1].keyword").isEqualTo("Java")
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/search/history/{keyword} - 특정 검색어 삭제")
    inner class DeleteSearchHistory {

        @Test
        @DisplayName("특정 검색어를 삭제하면, 204를 반환하고 해당 키워드가 삭제된다")
        fun deleteSearchHistory_WithKeyword_Returns204AndDeletesKeyword() {
            // Given: 사용자 생성 및 검색 기록 저장
            val (user, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "test4@example.com",
                providerId = "google-111"
            )

            searchHistoryRepository.save(user.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(user.id!!, "Kotlin", SearchType.CONTENT).block()

            // When: 특정 검색어 삭제
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_SEARCH}/history/{keyword}", "Java")
                .exchange()
                .expectStatus().isNoContent

            // Then: Java는 삭제되고 Kotlin은 남아있음
            val remaining = searchHistoryRepository.findRecentByUserId(user.id!!, 10).block()
            assertThat(remaining).hasSize(1)
            assertThat(remaining!![0].keyword).isEqualTo("Kotlin")
        }

        @Test
        @DisplayName("한글 키워드도 삭제할 수 있다")
        fun deleteSearchHistory_WithKoreanKeyword_Returns204() {
            // Given: 사용자 생성 및 한글 검색 기록 저장
            val (user, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "korean-keyword-test@example.com",
                providerId = "google-korean-keyword-222"
            )

            searchHistoryRepository.save(user.id!!, "프로그래밍", SearchType.CONTENT).block()

            // When & Then: 한글 키워드 삭제
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_SEARCH}/history/{keyword}", "프로그래밍")
                .exchange()
                .expectStatus().isNoContent

            // Then: 검색 기록이 삭제됨
            val remaining = searchHistoryRepository.findRecentByUserId(user.id!!, 10).block()
            assertThat(remaining).isEmpty()
        }

        @Test
        @DisplayName("존재하지 않는 키워드를 삭제해도 204를 반환한다")
        fun deleteSearchHistory_WithNonExistentKeyword_Returns204() {
            // Given: 사용자 생성
            val (user, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "test6@example.com",
                providerId = "google-333"
            )

            // When & Then: 존재하지 않는 키워드 삭제
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_SEARCH}/history/{keyword}", "NonExistent")
                .exchange()
                .expectStatus().isNoContent
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/search/history - 전체 검색 기록 삭제")
    inner class DeleteAllSearchHistory {

        @Test
        @DisplayName("전체 검색 기록을 삭제하면, 204를 반환하고 모든 기록이 삭제된다")
        fun deleteAllSearchHistory_Returns204AndDeletesAll() {
            // Given: 사용자 생성 및 여러 검색 기록 저장
            val (user, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "test7@example.com",
                providerId = "google-444"
            )

            searchHistoryRepository.save(user.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(user.id!!, "Kotlin", SearchType.CONTENT).block()
            searchHistoryRepository.save(user.id!!, "홍길동", SearchType.USER).block()

            // When: 전체 검색 기록 삭제
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_SEARCH}/history")
                .exchange()
                .expectStatus().isNoContent

            // Then: 모든 검색 기록이 삭제됨
            val remaining = searchHistoryRepository.findRecentByUserId(user.id!!, 10).block()
            assertThat(remaining).isEmpty()
        }

        @Test
        @DisplayName("검색 기록이 없어도 204를 반환한다")
        fun deleteAllSearchHistory_WithNoHistory_Returns204() {
            // Given: 사용자 생성 (검색 기록 없음)
            val (user, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "test8@example.com",
                providerId = "google-555"
            )

            // When & Then: 전체 검색 기록 삭제
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_SEARCH}/history")
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("다른 사용자의 검색 기록은 삭제되지 않는다")
        fun deleteAllSearchHistory_DoesNotDeleteOtherUserHistory() {
            // Given: 두 명의 사용자 생성 (timestamp로 고유성 보장)
            val timestamp = System.currentTimeMillis()
            val uuid1 = UUID.randomUUID().toString().substring(0, 8)
            val uuid2 = UUID.randomUUID().toString().substring(0, 8)

            val (user1, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "delete-all-user1-$timestamp-$uuid1@example.com",
                providerId = "google-delete-all-666-$timestamp-$uuid1"
            )

            val (user2, _) = createUserWithProfile(
                userRepository = userRepository,
                userProfileRepository = userProfileRepository,
                email = "delete-all-user2-$timestamp-$uuid2@example.com",
                providerId = "google-delete-all-777-$timestamp-$uuid2"
            )

            // 각 사용자의 검색 기록 저장
            searchHistoryRepository.save(user1.id!!, "Java", SearchType.CONTENT).block()
            searchHistoryRepository.save(user2.id!!, "Python", SearchType.CONTENT).block()

            // When: user1의 검색 기록 삭제
            webTestClient
                .mutateWith(mockUser(user1.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_SEARCH}/history")
                .exchange()
                .expectStatus().isNoContent

            // Then: user1의 검색 기록만 삭제되고 user2의 기록은 유지됨
            val user1Remaining = searchHistoryRepository.findRecentByUserId(user1.id!!, 10).block()
            val user2Remaining = searchHistoryRepository.findRecentByUserId(user2.id!!, 10).block()

            assertThat(user1Remaining).isEmpty()
            assertThat(user2Remaining).hasSize(1)
            assertThat(user2Remaining!![0].keyword).isEqualTo("Python")
        }
    }
}
