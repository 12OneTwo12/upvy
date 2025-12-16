package me.onetwo.upvy.domain.interaction.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.interaction.dto.SaveResponse
import me.onetwo.upvy.domain.interaction.dto.SaveStatusResponse
import me.onetwo.upvy.domain.interaction.service.SaveService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.config.RestDocsConfiguration
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@WebFluxTest(SaveController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("저장 Controller 테스트")
class SaveControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var saveService: SaveService

    @Nested
    @DisplayName("POST /api/v1/videos/{contentId}/save - 콘텐츠 저장")
    inner class SaveVideo {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 저장 시, 200 OK와 저장 응답을 반환한다")
        fun saveVideo_WithValidRequest_ReturnsSaveResponse() {
            // Given: 저장 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val response = SaveResponse(
                contentId = contentId,
                saveCount = 5,
                isSaved = true
            )

            every { saveService.saveContent(userId, UUID.fromString(contentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(contentId)
                .jsonPath("$.isSaved").isEqualTo(true)
                .jsonPath("$.saveCount").isEqualTo(5)
                .consumeWith(
                    document(
                        "save-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("isSaved").description("저장 여부"),
                            fieldWithPath("saveCount").description("저장 수")
                        )
                    )
                )

            verify(exactly = 1) { saveService.saveContent(userId, UUID.fromString(contentId)) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/videos/{contentId}/save - 콘텐츠 저장 취소")
    inner class UnsaveVideo {

        @Test
        @DisplayName("유효한 요청으로 저장 취소 시, 200 OK와 저장 취소 응답을 반환한다")
        fun unsaveVideo_WithValidRequest_ReturnsSaveResponse() {
            // Given: 저장 취소 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val response = SaveResponse(
                contentId = contentId,
                saveCount = 4,
                isSaved = false
            )

            every { saveService.unsaveContent(userId, UUID.fromString(contentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(contentId)
                .jsonPath("$.isSaved").isEqualTo(false)
                .jsonPath("$.saveCount").isEqualTo(4)
                .consumeWith(
                    document(
                        "save-delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("isSaved").description("저장 여부"),
                            fieldWithPath("saveCount").description("저장 수")
                        )
                    )
                )

            verify(exactly = 1) { saveService.unsaveContent(userId, UUID.fromString(contentId)) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/saved-videos - 저장한 콘텐츠 목록 조회")
    inner class GetSavedVideos {

        @Test
        @DisplayName("저장한 콘텐츠 목록 조회 시, 200 OK와 콘텐츠 목록을 반환한다")
        fun getSavedVideos_WithValidRequest_ReturnsSavedContentList() {
            // Given: 저장한 콘텐츠 목록
            val userId = UUID.randomUUID()
            val contentResponse1 = me.onetwo.upvy.domain.content.dto.ContentResponse(
                id = UUID.randomUUID().toString(),
                creatorId = userId.toString(),
                contentType = me.onetwo.upvy.domain.content.model.ContentType.VIDEO,
                url = "https://example.com/video1.mp4",
                photoUrls = null,
                thumbnailUrl = "https://example.com/thumb1.jpg",
                duration = 60,
                width = 1920,
                height = 1080,
                status = me.onetwo.upvy.domain.content.model.ContentStatus.PUBLISHED,
                title = "Saved Video 1",
                description = "Description 1",
                category = me.onetwo.upvy.domain.content.model.Category.PROGRAMMING,
                tags = listOf("test"),
                language = "ko",
                interactions = me.onetwo.upvy.domain.feed.dto.InteractionInfoResponse(
                    likeCount = 10,
                    commentCount = 5,
                    saveCount = 3,
                    shareCount = 2,
                    viewCount = 100,
                    isLiked = false,
                    isSaved = true
                ),
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now()
            )
            val contentResponse2 = me.onetwo.upvy.domain.content.dto.ContentResponse(
                id = UUID.randomUUID().toString(),
                creatorId = userId.toString(),
                contentType = me.onetwo.upvy.domain.content.model.ContentType.VIDEO,
                url = "https://example.com/video2.mp4",
                photoUrls = null,
                thumbnailUrl = "https://example.com/thumb2.jpg",
                duration = 120,
                width = 1920,
                height = 1080,
                status = me.onetwo.upvy.domain.content.model.ContentStatus.PUBLISHED,
                title = "Saved Video 2",
                description = "Description 2",
                category = me.onetwo.upvy.domain.content.model.Category.HEALTH,
                tags = listOf("test"),
                language = "ko",
                interactions = me.onetwo.upvy.domain.feed.dto.InteractionInfoResponse(
                    likeCount = 20,
                    commentCount = 10,
                    saveCount = 5,
                    shareCount = 3,
                    viewCount = 200,
                    isLiked = false,
                    isSaved = true
                ),
                createdAt = java.time.Instant.now(),
                updatedAt = java.time.Instant.now()
            )

            val pageResponse = me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse(
                content = listOf(contentResponse1, contentResponse2),
                nextCursor = null,
                hasNext = false,
                count = 2
            )

            every { saveService.getSavedContentsWithCursor(userId, any()) } returns Mono.just(pageResponse)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1_USERS}/me/saved-contents")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content[0].id").isEqualTo(contentResponse1.id)
                .jsonPath("$.content[0].title").isEqualTo("Saved Video 1")
                .jsonPath("$.content[1].id").isEqualTo(contentResponse2.id)
                .jsonPath("$.content[1].title").isEqualTo("Saved Video 2")
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(2)
                .consumeWith(
                    document(
                        "save-list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        responseFields(
                            fieldWithPath("content").description("콘텐츠 목록"),
                            fieldWithPath("content[].id").description("콘텐츠 ID"),
                            fieldWithPath("content[].creatorId").description("크리에이터 ID"),
                            fieldWithPath("content[].contentType").description("콘텐츠 타입"),
                            fieldWithPath("content[].url").description("콘텐츠 URL"),
                            fieldWithPath("content[].photoUrls").description("사진 URL 목록").optional(),
                            fieldWithPath("content[].thumbnailUrl").description("썸네일 URL"),
                            fieldWithPath("content[].duration").description("비디오 길이 (초)").optional(),
                            fieldWithPath("content[].width").description("가로 해상도"),
                            fieldWithPath("content[].height").description("세로 해상도"),
                            fieldWithPath("content[].status").description("콘텐츠 상태"),
                            fieldWithPath("content[].title").description("제목"),
                            fieldWithPath("content[].description").description("설명").optional(),
                            fieldWithPath("content[].category").description("카테고리"),
                            fieldWithPath("content[].tags").description("태그 목록"),
                            fieldWithPath("content[].language").description("언어"),
                            fieldWithPath("content[].interactions.likeCount").description("좋아요 수"),
                            fieldWithPath("content[].interactions.commentCount").description("댓글 수"),
                            fieldWithPath("content[].interactions.saveCount").description("저장 수"),
                            fieldWithPath("content[].interactions.shareCount").description("공유 수"),
                            fieldWithPath("content[].interactions.viewCount").description("조회수"),
                            fieldWithPath("content[].interactions.isLiked").description("현재 사용자의 좋아요 여부"),
                            fieldWithPath("content[].interactions.isSaved").description("현재 사용자의 저장 여부"),
                            fieldWithPath("content[].createdAt").description("생성 시각"),
                            fieldWithPath("content[].updatedAt").description("수정 시각"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서 (없으면 null)").optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("count").description("현재 페이지의 항목 수")
                        )
                    )
                )

            verify(exactly = 1) { saveService.getSavedContentsWithCursor(userId, any()) }
        }

        @Test
        @DisplayName("저장한 콘텐츠가 없으면, 빈 배열을 반환한다")
        fun getSavedVideos_WithNoSavedContents_ReturnsEmptyArray() {
            // Given: 저장한 콘텐츠가 없음
            val userId = UUID.randomUUID()
            val emptyPageResponse = me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse.empty<me.onetwo.upvy.domain.content.dto.ContentResponse>()

            every { saveService.getSavedContentsWithCursor(userId, any()) } returns Mono.just(emptyPageResponse)

            // When & Then: 빈 배열 반환 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1_USERS}/me/saved-contents")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(0)

            verify(exactly = 1) { saveService.getSavedContentsWithCursor(userId, any()) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/videos/{contentId}/save/status - 저장 상태 조회")
    inner class GetSaveStatus {

        @Test
        @DisplayName("저장 상태 조회 시, 사용자가 저장한 경우 true를 반환한다")
        fun getSaveStatus_WhenUserSaved_ReturnsTrue() {
            // Given: 사용자가 콘텐츠를 저장한 상태
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val response = SaveStatusResponse(
                contentId = contentId,
                isSaved = true
            )

            every { saveService.getSaveStatus(userId, UUID.fromString(contentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save/status", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(contentId)
                .jsonPath("$.isSaved").isEqualTo(true)
                .consumeWith(
                    document(
                        "save-status-check",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("contentId").description("콘텐츠 ID"),
                            fieldWithPath("isSaved").description("저장 여부 (true: 저장, false: 저장 안 함)")
                        )
                    )
                )

            verify(exactly = 1) { saveService.getSaveStatus(userId, UUID.fromString(contentId)) }
        }

        @Test
        @DisplayName("저장 상태 조회 시, 사용자가 저장하지 않은 경우 false를 반환한다")
        fun getSaveStatus_WhenUserNotSaved_ReturnsFalse() {
            // Given: 사용자가 콘텐츠를 저장하지 않은 상태
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID().toString()
            val response = SaveStatusResponse(
                contentId = contentId,
                isSaved = false
            )

            every { saveService.getSaveStatus(userId, UUID.fromString(contentId)) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save/status", contentId)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(contentId)
                .jsonPath("$.isSaved").isEqualTo(false)

            verify(exactly = 1) { saveService.getSaveStatus(userId, UUID.fromString(contentId)) }
        }
    }
}
