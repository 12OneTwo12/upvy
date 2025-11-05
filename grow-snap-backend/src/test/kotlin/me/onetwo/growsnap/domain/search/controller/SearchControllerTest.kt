package me.onetwo.growsnap.domain.search.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.domain.feed.dto.CreatorInfoResponse
import me.onetwo.growsnap.domain.feed.dto.FeedItemResponse
import me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse
import me.onetwo.growsnap.domain.search.dto.AutocompleteRequest
import me.onetwo.growsnap.domain.search.dto.AutocompleteResponse
import me.onetwo.growsnap.domain.search.dto.AutocompleteSuggestion
import me.onetwo.growsnap.domain.search.dto.ContentSearchRequest
import me.onetwo.growsnap.domain.search.dto.SuggestionType
import me.onetwo.growsnap.domain.search.dto.TrendingKeyword
import me.onetwo.growsnap.domain.search.dto.TrendingSearchResponse
import me.onetwo.growsnap.domain.search.dto.UserSearchRequest
import me.onetwo.growsnap.domain.search.dto.UserSearchResult
import me.onetwo.growsnap.domain.search.model.SearchSortType
import me.onetwo.growsnap.domain.search.service.SearchService
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageResponse
import me.onetwo.growsnap.infrastructure.config.RestDocsConfiguration
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.search.dto.SearchHistoryItem
import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * SearchController 단위 테스트
 *
 * ## @WebFluxTest
 * - SearchController만 로드
 * - Service는 @MockkBean으로 모킹
 *
 * ## REST Docs
 * - 모든 API에 document() 추가
 * - queryParameters(), responseFields() 문서화
 */
@WebFluxTest(SearchController::class)
@Import(TestSecurityConfig::class, RestDocsConfiguration::class)
@ActiveProfiles("test")
@AutoConfigureRestDocs
@DisplayName("SearchController 단위 테스트")
class SearchControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var searchService: SearchService

    @Nested
    @DisplayName("GET /api/v1/search/contents - 콘텐츠 검색")
    inner class SearchContents {

        @Test
        @DisplayName("검색 키워드로 콘텐츠를 검색하면, 200과 검색 결과를 반환한다")
        fun searchContents_WithQuery_Returns200AndResults() {
            // Given: 콘텐츠 검색 결과
            val query = "프로그래밍"
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val creatorId = UUID.randomUUID()

            val feedItem = FeedItemResponse(
                contentId = contentId,
                contentType = ContentType.VIDEO,
                url = "https://example.com/video.mp4",
                photoUrls = null,
                thumbnailUrl = "https://example.com/thumbnail.jpg",
                duration = 120,
                width = 1920,
                height = 1080,
                title = "프로그래밍 기초",
                description = "프로그래밍을 배워봅시다",
                category = Category.PROGRAMMING,
                tags = listOf("프로그래밍", "기초"),
                creator = CreatorInfoResponse(
                    userId = creatorId,
                    nickname = "개발자",
                    profileImageUrl = null,
                    followerCount = 50
                ),
                interactions = InteractionInfoResponse(
                    likeCount = 10,
                    commentCount = 5,
                    saveCount = 3,
                    shareCount = 2,
                    viewCount = 100,
                    isLiked = false,
                    isSaved = false
                ),
                subtitles = emptyList()
            )

            val response = CursorPageResponse(
                content = listOf(feedItem),
                nextCursor = null,
                hasNext = false,
                count = 1
            )

            every {
                searchService.searchContents(any(), any())
            } returns Mono.just(response)

            // When & Then: 콘텐츠 검색 및 REST Docs 생성
            webTestClient
                .mutateWith(mockUser(userId))
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
                .jsonPath("$.content[0].contentId").isEqualTo(contentId.toString())
                .jsonPath("$.content[0].title").isEqualTo("프로그래밍 기초")
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(1)
                .consumeWith(
                    document(
                        "search-contents",
                        queryParameters(
                            parameterWithName("q").description("검색 키워드 (필수, 최소 2글자)"),
                            parameterWithName("category").description("카테고리 필터 (선택)").optional(),
                            parameterWithName("difficulty").description("난이도 필터 (선택: BEGINNER, INTERMEDIATE, ADVANCED)").optional(),
                            parameterWithName("minDuration").description("최소 길이 (초, 선택)").optional(),
                            parameterWithName("maxDuration").description("최대 길이 (초, 선택)").optional(),
                            parameterWithName("startDate").description("시작 날짜 (선택, yyyy-MM-dd)").optional(),
                            parameterWithName("endDate").description("종료 날짜 (선택, yyyy-MM-dd)").optional(),
                            parameterWithName("language").description("언어 코드 (선택, 2글자)").optional(),
                            parameterWithName("sortBy").description("정렬 기준 (RELEVANCE, RECENT, POPULAR)"),
                            parameterWithName("cursor").description("페이지네이션 커서 (선택)").optional(),
                            parameterWithName("limit").description("페이지 크기 (기본값: 20)")
                        ),
                        responseFields(
                            fieldWithPath("content[]").description("검색 결과 목록 (FeedItemResponse 형식)"),
                            fieldWithPath("content[].contentId").description("콘텐츠 ID"),
                            fieldWithPath("content[].contentType").description("콘텐츠 타입 (VIDEO, PHOTO)"),
                            fieldWithPath("content[].url").description("콘텐츠 URL"),
                            fieldWithPath("content[].photoUrls").description("사진 URL 목록 (PHOTO 타입인 경우)"),
                            fieldWithPath("content[].thumbnailUrl").description("썸네일 URL"),
                            fieldWithPath("content[].duration").description("비디오 길이 (초)"),
                            fieldWithPath("content[].width").description("콘텐츠 가로 크기 (픽셀)"),
                            fieldWithPath("content[].height").description("콘텐츠 세로 크기 (픽셀)"),
                            fieldWithPath("content[].title").description("제목"),
                            fieldWithPath("content[].description").description("설명"),
                            fieldWithPath("content[].category").description("카테고리"),
                            fieldWithPath("content[].tags[]").description("태그 목록"),
                            fieldWithPath("content[].creator").description("크리에이터 정보"),
                            fieldWithPath("content[].creator.userId").description("크리에이터 ID"),
                            fieldWithPath("content[].creator.followerCount").description("크리에이터 팔로워 수"),
                            fieldWithPath("content[].creator.nickname").description("크리에이터 닉네임"),
                            fieldWithPath("content[].creator.profileImageUrl").description("크리에이터 프로필 이미지 URL"),
                            fieldWithPath("content[].interactions").description("인터랙션 정보"),
                            fieldWithPath("content[].interactions.likeCount").description("좋아요 수"),
                            fieldWithPath("content[].interactions.commentCount").description("댓글 수"),
                            fieldWithPath("content[].interactions.saveCount").description("저장 수"),
                            fieldWithPath("content[].interactions.shareCount").description("공유 수"),
                            fieldWithPath("content[].interactions.viewCount").description("조회수"),
                            fieldWithPath("content[].interactions.isLiked").description("현재 사용자 좋아요 여부"),
                            fieldWithPath("content[].interactions.isSaved").description("현재 사용자 저장 여부"),
                            fieldWithPath("content[].subtitles[]").description("자막 정보 목록"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서 (없으면 null)"),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("count").description("현재 페이지 항목 수")
                        )
                    )
                )
        }

        @Test
        @DisplayName("검색 키워드가 2글자 미만이면, 400을 반환한다")
        fun searchContents_WithShortQuery_Returns400() {
            // Given: 짧은 검색 키워드
            val query = "a"
            val userId = UUID.randomUUID()

            // When & Then: 400 응답
            webTestClient
                .mutateWith(mockUser(userId))
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
    }

    @Nested
    @DisplayName("GET /api/v1/search/users - 사용자 검색")
    inner class SearchUsers {

        @Test
        @DisplayName("검색 키워드로 사용자를 검색하면, 200과 검색 결과를 반환한다")
        fun searchUsers_WithQuery_Returns200AndResults() {
            // Given: 사용자 검색 결과
            val query = "홍길동"
            val currentUserId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val userResult = UserSearchResult(
                userId = userId,
                nickname = "홍길동",
                profileImageUrl = "https://example.com/profile.jpg",
                bio = "안녕하세요",
                followerCount = 100,
                isFollowing = false
            )

            val response = CursorPageResponse(
                content = listOf(userResult),
                nextCursor = null,
                hasNext = false,
                count = 1
            )

            every {
                searchService.searchUsers(any(), any())
            } returns Mono.just(response)

            // When & Then: 사용자 검색 및 REST Docs 생성
            webTestClient
                .mutateWith(mockUser(currentUserId))
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
                .jsonPath("$.content[0].userId").isEqualTo(userId.toString())
                .jsonPath("$.content[0].nickname").isEqualTo("홍길동")
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(1)
                .consumeWith(
                    document(
                        "search-users",
                        queryParameters(
                            parameterWithName("q").description("검색 키워드 (필수, 최소 2글자)"),
                            parameterWithName("cursor").description("페이지네이션 커서 (선택)").optional(),
                            parameterWithName("limit").description("페이지 크기 (기본값: 20)")
                        ),
                        responseFields(
                            fieldWithPath("content[]").description("사용자 검색 결과 목록"),
                            fieldWithPath("content[].userId").description("사용자 ID"),
                            fieldWithPath("content[].nickname").description("닉네임"),
                            fieldWithPath("content[].profileImageUrl").description("프로필 이미지 URL"),
                            fieldWithPath("content[].bio").description("프로필 설명"),
                            fieldWithPath("content[].followerCount").description("팔로워 수"),
                            fieldWithPath("content[].isFollowing").description("현재 사용자가 팔로우 중인지 여부"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서 (없으면 null)"),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("count").description("현재 페이지 항목 수")
                        )
                    )
                )
        }
    }

    @Nested
    @DisplayName("GET /api/v1/search/autocomplete - 자동완성")
    inner class Autocomplete {

        @Test
        @DisplayName("입력 키워드로 자동완성하면, 200과 제안 목록을 반환한다")
        fun autocomplete_WithQuery_Returns200AndSuggestions() {
            // Given: 자동완성 제안 목록
            val query = "프로"

            val response = AutocompleteResponse(
                suggestions = listOf(
                    AutocompleteSuggestion(
                        text = "프로그래밍",
                        type = SuggestionType.CONTENT,
                        highlightedText = "<em>프로</em>그래밍"
                    ),
                    AutocompleteSuggestion(
                        text = "프로젝트",
                        type = SuggestionType.CONTENT,
                        highlightedText = "<em>프로</em>젝트"
                    )
                )
            )

            every {
                searchService.autocomplete(any())
            } returns Mono.just(response)

            // When & Then: 자동완성 및 REST Docs 생성
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
                .jsonPath("$.suggestions[0].text").isEqualTo("프로그래밍")
                .jsonPath("$.suggestions[0].type").isEqualTo("CONTENT")
                .consumeWith(
                    document(
                        "search-autocomplete",
                        queryParameters(
                            parameterWithName("q").description("입력 중인 키워드 (필수, 최소 1글자)"),
                            parameterWithName("limit").description("제안 개수 (기본값: 10)")
                        ),
                        responseFields(
                            fieldWithPath("suggestions[]").description("자동완성 제안 목록"),
                            fieldWithPath("suggestions[].text").description("제안 텍스트"),
                            fieldWithPath("suggestions[].type").description("제안 타입 (CONTENT, USER)"),
                            fieldWithPath("suggestions[].highlightedText").description("매칭된 부분 강조 텍스트 (HTML 태그 포함)")
                        )
                    )
                )
        }
    }

    @Nested
    @DisplayName("GET /api/v1/search/trending - 인기 검색어")
    inner class GetTrendingKeywords {

        @Test
        @DisplayName("인기 검색어를 조회하면, 200과 인기 검색어 목록을 반환한다")
        fun getTrendingKeywords_Returns200AndKeywords() {
            // Given: 인기 검색어 목록
            val response = TrendingSearchResponse(
                keywords = listOf(
                    TrendingKeyword(
                        keyword = "프로그래밍",
                        searchCount = 1000,
                        rank = 1
                    ),
                    TrendingKeyword(
                        keyword = "개발",
                        searchCount = 800,
                        rank = 2
                    )
                )
            )

            every {
                searchService.getTrendingKeywords(any())
            } returns Mono.just(response)

            // When & Then: 인기 검색어 조회 및 REST Docs 생성
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
                .jsonPath("$.keywords[0].keyword").isEqualTo("프로그래밍")
                .jsonPath("$.keywords[0].searchCount").isEqualTo(1000)
                .jsonPath("$.keywords[0].rank").isEqualTo(1)
                .consumeWith(
                    document(
                        "search-trending",
                        queryParameters(
                            parameterWithName("limit").description("인기 검색어 개수 (기본값: 10)")
                        ),
                        responseFields(
                            fieldWithPath("keywords[]").description("인기 검색어 목록"),
                            fieldWithPath("keywords[].keyword").description("검색 키워드"),
                            fieldWithPath("keywords[].searchCount").description("검색 횟수"),
                            fieldWithPath("keywords[].rank").description("순위 (1부터 시작)")
                        )
                    )
                )
        }
    }

    @Nested
    @DisplayName("GET /api/v1/search/history - 검색 기록 조회")
    inner class GetSearchHistory {

        @Test
        @DisplayName("인증된 사용자의 검색 기록을 조회하면, 200과 검색 기록을 반환한다")
        fun getSearchHistory_WithAuthenticatedUser_ReturnsHistory() {
            // Given: Service 모킹
            val userId = UUID.randomUUID()
            val response = me.onetwo.growsnap.domain.search.dto.SearchHistoryResponse(
                keywords = listOf(
                    SearchHistoryItem(
                        keyword = "프로그래밍",
                        searchType = SearchType.CONTENT
                    ),
                    SearchHistoryItem(
                        keyword = "홍길동",
                        searchType = SearchType.USER
                    )
                )
            )

            every { searchService.getRecentSearches(userId, 10) } returns Mono.just(response)

            // When & Then: API 호출 및 REST Docs 생성
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("/api/v1/search/history?limit=10")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.keywords").isArray
                .jsonPath("$.keywords[0].keyword").isEqualTo("프로그래밍")
                .jsonPath("$.keywords[0].searchType").isEqualTo("CONTENT")
                .jsonPath("$.keywords[1].keyword").isEqualTo("홍길동")
                .jsonPath("$.keywords[1].searchType").isEqualTo("USER")
                .consumeWith(
                    document(
                        "search-history-get",
                        queryParameters(
                            parameterWithName("limit").description("최대 개수 (기본값: 10)").optional()
                        ),
                        responseFields(
                            fieldWithPath("keywords[]").description("검색 기록 목록"),
                            fieldWithPath("keywords[].keyword").description("검색 키워드"),
                            fieldWithPath("keywords[].searchType").description("검색 타입 (CONTENT, USER)")
                        )
                    )
                )

            verify(exactly = 1) { searchService.getRecentSearches(userId, 10) }
        }

        @Test
        @DisplayName("검색 기록이 없으면, 빈 리스트를 반환한다")
        fun getSearchHistory_WithNoHistory_ReturnsEmptyList() {
            // Given: Service 모킹 (빈 리스트)
            val userId = UUID.randomUUID()
            val response = me.onetwo.growsnap.domain.search.dto.SearchHistoryResponse(
                keywords = emptyList()
            )

            every { searchService.getRecentSearches(userId, 10) } returns Mono.just(response)

            // When & Then: API 호출
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("/api/v1/search/history")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.keywords").isEmpty
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/search/history/{keyword} - 특정 검색어 삭제")
    inner class DeleteSearchHistory {

        @Test
        @DisplayName("특정 검색어를 삭제하면, 204를 반환한다")
        fun deleteSearchHistory_WithKeyword_Returns204() {
            // Given: Service 모킹
            val userId = UUID.randomUUID()
            val keyword = "Java"

            every { searchService.deleteSearchHistory(userId, keyword) } returns Mono.empty()

            // When & Then: API 호출 및 REST Docs 생성
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("/api/v1/search/history/{keyword}", keyword)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document(
                        "search-history-delete-keyword",
                        org.springframework.restdocs.request.RequestDocumentation.pathParameters(
                            org.springframework.restdocs.request.RequestDocumentation.parameterWithName("keyword").description("삭제할 검색 키워드")
                        )
                    )
                )

            verify(exactly = 1) { searchService.deleteSearchHistory(userId, keyword) }
        }

        @Test
        @DisplayName("한글 키워드도 삭제할 수 있다")
        fun deleteSearchHistory_WithKoreanKeyword_Returns204() {
            // Given: Service 모킹
            val userId = UUID.randomUUID()
            val keyword = "프로그래밍"

            every { searchService.deleteSearchHistory(userId, keyword) } returns Mono.empty()

            // When & Then: API 호출
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("/api/v1/search/history/{keyword}", keyword)
                .exchange()
                .expectStatus().isNoContent

            verify(exactly = 1) { searchService.deleteSearchHistory(userId, keyword) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/search/history - 전체 검색 기록 삭제")
    inner class DeleteAllSearchHistory {

        @Test
        @DisplayName("전체 검색 기록을 삭제하면, 204를 반환한다")
        fun deleteAllSearchHistory_Returns204() {
            // Given: Service 모킹
            val userId = UUID.randomUUID()

            every { searchService.deleteAllSearchHistory(userId) } returns Mono.empty()

            // When & Then: API 호출 및 REST Docs 생성
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("/api/v1/search/history")
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document("search-history-delete-all")
                )

            verify(exactly = 1) { searchService.deleteAllSearchHistory(userId) }
        }
    }
}
