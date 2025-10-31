package me.onetwo.growsnap.domain.feed.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.feed.dto.CreatorInfoResponse
import me.onetwo.growsnap.domain.feed.dto.FeedItemResponse
import me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse
import me.onetwo.growsnap.domain.feed.service.FeedCacheService
import me.onetwo.growsnap.domain.feed.service.FeedService
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageResponse
import me.onetwo.growsnap.infrastructure.config.RestDocsConfiguration
import me.onetwo.growsnap.util.mockUser
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.util.UUID

@WebFluxTest(FeedController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("피드 Controller 테스트")
class FeedControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var feedService: FeedService

    @MockkBean
    private lateinit var feedCacheService: FeedCacheService

    @Nested
    @DisplayName("GET /api/v1/feed - 메인 피드 조회")
    inner class GetMainFeed {

        @Test
        @DisplayName("유효한 요청으로 조회 시, 200 OK와 피드 목록을 반환한다")
        fun getMainFeed_WithValidRequest_ReturnsOkAndFeedItems() {
            // Given: 테스트 데이터
            val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val feedItems = createMockFeedItems(10)
            val response = CursorPageResponse.of(
                content = feedItems,
                limit = 10,
                getCursor = { it.contentId.toString() }
            )

            every { feedService.getMainFeed(userId, any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path(ApiPaths.API_V1_FEED)
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .consumeWith(
                    document("feed-main",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                            parameterWithName("cursor")
                                .description("커서 (마지막 조회 콘텐츠 ID, 첫 페이지는 null)")
                                .optional(),
                            parameterWithName("limit")
                                .description("페이지당 항목 수 (기본값: 20, 최대: 100)")
                                .optional()
                        ),
                        responseFields(
                            fieldWithPath("content[]").description("피드 아이템 목록"),
                            fieldWithPath("content[].contentId").description("콘텐츠 ID"),
                            fieldWithPath("content[].contentType").description("콘텐츠 타입 (VIDEO, PHOTO)"),
                            fieldWithPath("content[].url").description("콘텐츠 URL"),
                            fieldWithPath("content[].photoUrls").description("사진 URL 목록 (PHOTO 타입인 경우)").optional(),
                            fieldWithPath("content[].thumbnailUrl").description("썸네일 이미지 URL"),
                            fieldWithPath("content[].duration").description("비디오 길이 (초, 사진인 경우 null)").optional(),
                            fieldWithPath("content[].width").description("콘텐츠 가로 크기 (픽셀)"),
                            fieldWithPath("content[].height").description("콘텐츠 세로 크기 (픽셀)"),
                            fieldWithPath("content[].title").description("제목"),
                            fieldWithPath("content[].description").description("설명").optional(),
                            fieldWithPath("content[].category").description("카테고리"),
                            fieldWithPath("content[].tags[]").description("태그 목록"),
                            fieldWithPath("content[].creator").description("크리에이터 정보"),
                            fieldWithPath("content[].creator.userId").description("크리에이터 사용자 ID"),
                            fieldWithPath("content[].creator.nickname").description("크리에이터 닉네임"),
                            fieldWithPath("content[].creator.profileImageUrl").description("크리에이터 프로필 이미지 URL").optional(),
                            fieldWithPath("content[].creator.followerCount").description("크리에이터 팔로워 수"),
                            fieldWithPath("content[].interactions").description("인터랙션 정보"),
                            fieldWithPath("content[].interactions.likeCount").description("좋아요 수"),
                            fieldWithPath("content[].interactions.commentCount").description("댓글 수"),
                            fieldWithPath("content[].interactions.saveCount").description("저장 수"),
                            fieldWithPath("content[].interactions.shareCount").description("공유 수"),
                            fieldWithPath("content[].interactions.viewCount").description("조회수"),
                            fieldWithPath("content[].interactions.isLiked").description("현재 사용자의 좋아요 여부"),
                            fieldWithPath("content[].interactions.isSaved").description("현재 사용자의 저장 여부"),
                            fieldWithPath("content[].subtitles[]").description("자막 정보 목록"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서 (마지막 페이지인 경우 null)").optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("count").description("현재 페이지의 항목 수")
                        )
                    )
                )
        }

        @Test
        @DisplayName("커서와 함께 요청 시, 200 OK와 다음 페이지를 반환한다")
        fun getMainFeed_WithCursor_ReturnsOkAndNextPage() {
            // Given: 커서가 있는 요청
            val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val cursor = UUID.randomUUID().toString()
            val feedItems = createMockFeedItems(10)
            val response = CursorPageResponse.of(
                content = feedItems,
                limit = 10,
                getCursor = { it.contentId.toString() }
            )

            every { feedService.getMainFeed(userId, any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path(ApiPaths.API_V1_FEED)
                        .queryParam("cursor", cursor)
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
        }

        @Test
        @DisplayName("인증되지 않은 요청 시, 401 Unauthorized를 반환한다")
        fun getMainFeed_WithoutAuth_ReturnsUnauthorized() {
            // When & Then: 인증 없이 API 호출
            webTestClient.get()
                .uri(ApiPaths.API_V1_FEED)
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("PHOTO 타입 콘텐츠 조회 시, photoUrls가 응답에 포함된다")
        fun getMainFeed_WithPhotoContent_ReturnsPhotoUrls() {
            // Given: PHOTO 타입 콘텐츠 포함
            val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val feedItems = listOf(
                createMockPhotoFeedItem(
                    UUID.randomUUID(),
                    listOf(
                        "https://example.com/photo1.jpg",
                        "https://example.com/photo2.jpg",
                        "https://example.com/photo3.jpg"
                    )
                ),
                createMockFeedItems(1)[0] // VIDEO 타입
            )
            val response = CursorPageResponse.of(
                content = feedItems,
                limit = 10,
                getCursor = { it.contentId.toString() }
            )

            every { feedService.getMainFeed(userId, any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path(ApiPaths.API_V1_FEED)
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content[0].contentType").isEqualTo("PHOTO")
                .jsonPath("$.content[0].photoUrls").isArray
                .jsonPath("$.content[0].photoUrls.length()").isEqualTo(3)
                .jsonPath("$.content[0].photoUrls[0]").isEqualTo("https://example.com/photo1.jpg")
                .jsonPath("$.content[0].photoUrls[1]").isEqualTo("https://example.com/photo2.jpg")
                .jsonPath("$.content[0].photoUrls[2]").isEqualTo("https://example.com/photo3.jpg")
                .jsonPath("$.content[1].contentType").isEqualTo("VIDEO")
                .jsonPath("$.content[1].photoUrls").doesNotExist()
        }
    }

    @Nested
    @DisplayName("GET /api/v1/feed/following - 팔로잉 피드 조회")
    inner class GetFollowingFeed {

        @Test
        @DisplayName("유효한 요청으로 조회 시, 200 OK와 팔로잉 피드 목록을 반환한다")
        fun getFollowingFeed_WithValidRequest_ReturnsOkAndFeedItems() {
            // Given: 테스트 데이터
            val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val feedItems = createMockFeedItems(10)
            val response = CursorPageResponse.of(
                content = feedItems,
                limit = 10,
                getCursor = { it.contentId.toString() }
            )

            every { feedService.getFollowingFeed(userId, any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path("${ApiPaths.API_V1_FEED}/following")
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .consumeWith(
                    document("feed-following",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                            parameterWithName("cursor")
                                .description("커서 (마지막 조회 콘텐츠 ID, 첫 페이지는 null)")
                                .optional(),
                            parameterWithName("limit")
                                .description("페이지당 항목 수 (기본값: 20, 최대: 100)")
                                .optional()
                        ),
                        responseFields(
                            fieldWithPath("content[]").description("피드 아이템 목록"),
                            fieldWithPath("content[].contentId").description("콘텐츠 ID"),
                            fieldWithPath("content[].contentType").description("콘텐츠 타입 (VIDEO, PHOTO)"),
                            fieldWithPath("content[].url").description("콘텐츠 URL"),
                            fieldWithPath("content[].photoUrls").description("사진 URL 목록 (PHOTO 타입인 경우)").optional(),
                            fieldWithPath("content[].thumbnailUrl").description("썸네일 이미지 URL"),
                            fieldWithPath("content[].duration").description("비디오 길이 (초, 사진인 경우 null)").optional(),
                            fieldWithPath("content[].width").description("콘텐츠 가로 크기 (픽셀)"),
                            fieldWithPath("content[].height").description("콘텐츠 세로 크기 (픽셀)"),
                            fieldWithPath("content[].title").description("제목"),
                            fieldWithPath("content[].description").description("설명").optional(),
                            fieldWithPath("content[].category").description("카테고리"),
                            fieldWithPath("content[].tags[]").description("태그 목록"),
                            fieldWithPath("content[].creator").description("크리에이터 정보"),
                            fieldWithPath("content[].creator.userId").description("크리에이터 사용자 ID"),
                            fieldWithPath("content[].creator.nickname").description("크리에이터 닉네임"),
                            fieldWithPath("content[].creator.profileImageUrl").description("크리에이터 프로필 이미지 URL").optional(),
                            fieldWithPath("content[].creator.followerCount").description("크리에이터 팔로워 수"),
                            fieldWithPath("content[].interactions").description("인터랙션 정보"),
                            fieldWithPath("content[].interactions.likeCount").description("좋아요 수"),
                            fieldWithPath("content[].interactions.commentCount").description("댓글 수"),
                            fieldWithPath("content[].interactions.saveCount").description("저장 수"),
                            fieldWithPath("content[].interactions.shareCount").description("공유 수"),
                            fieldWithPath("content[].interactions.viewCount").description("조회수"),
                            fieldWithPath("content[].interactions.isLiked").description("현재 사용자의 좋아요 여부"),
                            fieldWithPath("content[].interactions.isSaved").description("현재 사용자의 저장 여부"),
                            fieldWithPath("content[].subtitles[]").description("자막 정보 목록"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서 (마지막 페이지인 경우 null)").optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("count").description("현재 페이지의 항목 수")
                        )
                    )
                )
        }

        @Test
        @DisplayName("인증되지 않은 요청 시, 401 Unauthorized를 반환한다")
        fun getFollowingFeed_WithoutAuth_ReturnsUnauthorized() {
            // When & Then: 인증 없이 API 호출
            webTestClient.get()
                .uri("${ApiPaths.API_V1_FEED}/following")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isUnauthorized
        }

        @Test
        @DisplayName("PHOTO 타입 콘텐츠 조회 시, photoUrls가 응답에 포함된다")
        fun getFollowingFeed_WithPhotoContent_ReturnsPhotoUrls() {
            // Given: PHOTO 타입 포함
            val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val feedItems = listOf(
                createMockPhotoFeedItem(
                    UUID.randomUUID(),
                    listOf(
                        "https://example.com/following-photo1.jpg",
                        "https://example.com/following-photo2.jpg"
                    )
                )
            )
            val response = CursorPageResponse.of(
                content = feedItems,
                limit = 10,
                getCursor = { it.contentId.toString() }
            )

            every { feedService.getFollowingFeed(userId, any()) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri { uriBuilder ->
                    uriBuilder.path("${ApiPaths.API_V1_FEED}/following")
                        .queryParam("limit", 10)
                        .build()
                }
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content[0].contentType").isEqualTo("PHOTO")
                .jsonPath("$.content[0].photoUrls").isArray
                .jsonPath("$.content[0].photoUrls.length()").isEqualTo(2)
                .jsonPath("$.content[0].photoUrls[0]").isEqualTo("https://example.com/following-photo1.jpg")
                .jsonPath("$.content[0].photoUrls[1]").isEqualTo("https://example.com/following-photo2.jpg")
        }
    }

    @Nested
    @DisplayName("POST /api/v1/feed/refresh - 피드 새로고침")
    inner class RefreshFeed {

        @Test
        @DisplayName("인증된 사용자가 새로고침 요청 시, 204 No Content를 반환한다")
        fun refreshFeed_WithAuthentication_ReturnsNoContent() {
            // Given: 인증된 사용자
            val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            every { feedCacheService.clearUserCache(userId) } returns Mono.just(true)

            // When & Then: 피드 새로고침 API 호출
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1_FEED}/refresh")
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document("feed-refresh",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint())
                    )
                )
        }

        @Test
        @DisplayName("인증되지 않은 요청 시, 401 Unauthorized를 반환한다")
        fun refreshFeed_WithoutAuth_ReturnsUnauthorized() {
            // When & Then: 인증 없이 API 호출
            webTestClient.post()
                .uri("${ApiPaths.API_V1_FEED}/refresh")
                .exchange()
                .expectStatus().isUnauthorized
        }
    }

    /**
     * 테스트용 피드 아이템 목록 생성 (VIDEO 타입)
     */
    private fun createMockFeedItems(count: Int): List<FeedItemResponse> {
        return (1..count).map {
            FeedItemResponse(
                contentId = UUID.randomUUID(),
                contentType = ContentType.VIDEO,
                url = "https://example.com/video$it.mp4",
                photoUrls = null,
                thumbnailUrl = "https://example.com/thumbnail$it.jpg",
                duration = 60,
                width = 1920,
                height = 1080,
                title = "Test Video $it",
                description = "Test Description $it",
                category = Category.PROGRAMMING,
                tags = listOf("test", "video"),
                creator = CreatorInfoResponse(
                    userId = UUID.randomUUID(),
                    nickname = "Creator $it",
                    profileImageUrl = "https://example.com/profile$it.jpg",
                    followerCount = 1000
                ),
                interactions = InteractionInfoResponse(
                    likeCount = 100,
                    commentCount = 50,
                    saveCount = 30,
                    shareCount = 20,
                    viewCount = 1000,
                    isLiked = false,
                    isSaved = false
                ),
                subtitles = emptyList()
            )
        }
    }

    /**
     * 테스트용 PHOTO 피드 아이템 생성
     */
    private fun createMockPhotoFeedItem(contentId: UUID, photoUrls: List<String>): FeedItemResponse {
        return FeedItemResponse(
            contentId = contentId,
            contentType = ContentType.PHOTO,
            url = photoUrls.firstOrNull() ?: "https://example.com/photo.jpg",
            photoUrls = photoUrls,
            thumbnailUrl = "https://example.com/photo-thumbnail.jpg",
            duration = null, // PHOTO는 duration 없음
            width = 1080,
            height = 1080,
            title = "Test Photo Content",
            description = "Test Photo Description",
            category = Category.ART,
            tags = listOf("test", "photo"),
            creator = CreatorInfoResponse(
                userId = UUID.randomUUID(),
                nickname = "Photo Creator",
                profileImageUrl = "https://example.com/photo-profile.jpg",
                followerCount = 1500
            ),
            interactions = InteractionInfoResponse(
                likeCount = 200,
                commentCount = 100,
                saveCount = 50,
                shareCount = 30,
                viewCount = 2000,
                isLiked = false,
                isSaved = false
            ),
            subtitles = emptyList()
        )
    }
}
