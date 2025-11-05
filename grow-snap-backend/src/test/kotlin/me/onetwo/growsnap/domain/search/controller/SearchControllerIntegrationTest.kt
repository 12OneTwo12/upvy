package me.onetwo.growsnap.domain.search.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.manticore.ManticoreSearchClient
import me.onetwo.growsnap.infrastructure.manticore.dto.Hit
import me.onetwo.growsnap.infrastructure.manticore.dto.Hits
import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchResponse
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
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
class SearchControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @MockkBean
    private lateinit var manticoreSearchClient: ManticoreSearchClient

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setUp() {
        // Given: 테스트 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository = userRepository,
            userProfileRepository = userProfileRepository,
            email = "test@example.com",
            providerId = "test-123"
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
                        .path("/api/v1/search/contents")
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
                        .path("/api/v1/search/contents")
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
        @DisplayName("검색 키워드가 2글자 미만이면, 400을 반환한다")
        fun searchContents_WithShortQuery_Returns400() {
            // Given: 짧은 검색 키워드
            val query = "a"

            // When & Then: 400 응답
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/v1/search/contents")
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
                        .path("/api/v1/search/contents")
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
            // Given: 검색 키워드
            val query = "test"

            // When & Then: 사용자 검색
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/v1/search/users")
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
        @DisplayName("검색 키워드가 2글자 미만이면, 400을 반환한다")
        fun searchUsers_WithShortQuery_Returns400() {
            // Given: 짧은 검색 키워드
            val query = "a"

            // When & Then: 400 응답
            webTestClient
                .mutateWith(mockUser(testUserId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/v1/search/users")
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
                        .path("/api/v1/search/autocomplete")
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
                        .path("/api/v1/search/trending")
                        .queryParam("limit", 10)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.keywords").isArray
        }
    }
}
