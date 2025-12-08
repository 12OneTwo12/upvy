package me.onetwo.upvy.domain.block.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.block.dto.BlockedContentItemResponse
import me.onetwo.upvy.domain.block.dto.ContentBlockResponse
import me.onetwo.upvy.domain.block.exception.BlockException
import me.onetwo.upvy.domain.block.service.ContentBlockService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse
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
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 차단 Controller 테스트
 *
 * ContentBlockController의 HTTP 요청 처리를 테스트하고 REST Docs 문서를 생성합니다.
 */
@WebFluxTest(ContentBlockController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("콘텐츠 차단 Controller 테스트")
class ContentBlockControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var contentBlockService: ContentBlockService

    @Nested
    @DisplayName("POST /api/v1/contents/{contentId}/block - 콘텐츠 차단")
    inner class BlockContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠를 차단하면 201 Created를 반환한다")
        fun blockContent_WithValidRequest_Returns201Created() {
            // Given: 차단할 콘텐츠 정보
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val response = ContentBlockResponse(
                id = 1L,
                userId = userId.toString(),
                contentId = contentId.toString(),
                createdAt = Instant.now()
            )

            every { contentBlockService.blockContent(userId, contentId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/block", contentId)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.userId").isEqualTo(userId.toString())
                .jsonPath("$.contentId").isEqualTo(contentId.toString())
                .jsonPath("$.createdAt").isNotEmpty
                .consumeWith(
                    document(
                        "content-block-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("차단할 콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("id").description("차단 ID"),
                            fieldWithPath("userId").description("차단한 사용자 ID"),
                            fieldWithPath("contentId").description("차단된 콘텐츠 ID"),
                            fieldWithPath("createdAt").description("차단 시각")
                        )
                    )
                )

            verify(exactly = 1) { contentBlockService.blockContent(userId, contentId) }
        }

        @Test
        @DisplayName("이미 차단한 콘텐츠인 경우 409 Conflict를 반환한다")
        fun blockContent_WhenAlreadyBlocked_Returns409Conflict() {
            // Given: 이미 차단한 콘텐츠
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentBlockService.blockContent(userId, contentId) } returns
                Mono.error(BlockException.DuplicateContentBlockException(userId.toString(), contentId.toString()))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/block", contentId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_CONTENT_BLOCK")
                .consumeWith(
                    document(
                        "content-block-duplicate-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("차단할 콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("timestamp").description("오류 발생 시각"),
                            fieldWithPath("status").description("HTTP 상태 코드"),
                            fieldWithPath("error").description("HTTP 상태 메시지"),
                            fieldWithPath("message").description("에러 메시지"),
                            fieldWithPath("path").description("요청 경로"),
                            fieldWithPath("code").description("에러 코드")
                        )
                    )
                )

            verify(exactly = 1) { contentBlockService.blockContent(userId, contentId) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId}/block - 콘텐츠 차단 해제")
    inner class UnblockContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 차단을 해제하면 204 No Content를 반환한다")
        fun unblockContent_WithValidRequest_Returns204NoContent() {
            // Given: 차단 해제할 콘텐츠 정보
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentBlockService.unblockContent(userId, contentId) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/block", contentId)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document(
                        "content-block-delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("차단 해제할 콘텐츠 ID")
                        )
                    )
                )

            verify(exactly = 1) { contentBlockService.unblockContent(userId, contentId) }
        }

        @Test
        @DisplayName("차단하지 않은 콘텐츠인 경우 404 Not Found를 반환한다")
        fun unblockContent_WhenNotBlocked_Returns404NotFound() {
            // Given: 차단하지 않은 콘텐츠
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            every { contentBlockService.unblockContent(userId, contentId) } returns
                Mono.error(BlockException.ContentBlockNotFoundException(userId.toString(), contentId.toString()))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .delete()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/block", contentId)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.code").isEqualTo("CONTENT_BLOCK_NOT_FOUND")
                .consumeWith(
                    document(
                        "content-block-not-found-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("contentId").description("차단 해제할 콘텐츠 ID")
                        ),
                        responseFields(
                            fieldWithPath("timestamp").description("오류 발생 시각"),
                            fieldWithPath("status").description("HTTP 상태 코드"),
                            fieldWithPath("error").description("HTTP 상태 메시지"),
                            fieldWithPath("message").description("에러 메시지"),
                            fieldWithPath("path").description("요청 경로"),
                            fieldWithPath("code").description("에러 코드")
                        )
                    )
                )

            verify(exactly = 1) { contentBlockService.unblockContent(userId, contentId) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/blocks - 차단한 콘텐츠 목록 조회")
    inner class GetBlockedContents {

        @Test
        @DisplayName("차단한 콘텐츠 목록을 조회하면 200 OK를 반환한다")
        fun getBlockedContents_ReturnsBlockedContentsList() {
            // Given: 차단한 콘텐츠 목록
            val userId = UUID.randomUUID()
            val blockedContent1 = BlockedContentItemResponse(
                blockId = 1L,
                contentId = UUID.randomUUID().toString(),
                title = "Blocked Content 1",
                thumbnailUrl = "https://example.com/thumb1.jpg",
                creatorNickname = "Creator 1",
                blockedAt = Instant.now()
            )
            val blockedContent2 = BlockedContentItemResponse(
                blockId = 2L,
                contentId = UUID.randomUUID().toString(),
                title = "Blocked Content 2",
                thumbnailUrl = "https://example.com/thumb2.jpg",
                creatorNickname = "Creator 2",
                blockedAt = Instant.now()
            )

            val response = CursorPageResponse(
                content = listOf(blockedContent1, blockedContent2),
                nextCursor = null,
                hasNext = false,
                count = 2
            )

            every { contentBlockService.getBlockedContents(userId, null, 20) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/contents/blocks")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].blockId").isEqualTo(1)
                .jsonPath("$.content[0].contentId").isNotEmpty
                .jsonPath("$.content[0].title").isEqualTo("Blocked Content 1")
                .jsonPath("$.content[0].thumbnailUrl").isEqualTo("https://example.com/thumb1.jpg")
                .jsonPath("$.content[0].creatorNickname").isEqualTo("Creator 1")
                .jsonPath("$.content[0].blockedAt").isNotEmpty
                .jsonPath("$.content[1].blockId").isEqualTo(2)
                .jsonPath("$.content[1].title").isEqualTo("Blocked Content 2")
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(2)
                .consumeWith(
                    document(
                        "content-blocks-list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                            parameterWithName("cursor").description("커서 (차단 ID, 선택)").optional(),
                            parameterWithName("limit").description("조회 개수 (기본값: 20, 선택)").optional()
                        ),
                        responseFields(
                            fieldWithPath("content").description("차단한 콘텐츠 목록"),
                            fieldWithPath("content[].blockId").description("차단 ID"),
                            fieldWithPath("content[].contentId").description("차단된 콘텐츠 ID"),
                            fieldWithPath("content[].title").description("콘텐츠 제목"),
                            fieldWithPath("content[].thumbnailUrl").description("썸네일 URL"),
                            fieldWithPath("content[].creatorNickname").description("작성자 닉네임"),
                            fieldWithPath("content[].blockedAt").description("차단 시각"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서").optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("count").description("조회된 항목 수")
                        )
                    )
                )

            verify(exactly = 1) { contentBlockService.getBlockedContents(userId, null, 20) }
        }

        @Test
        @DisplayName("커서와 limit을 사용하여 페이지네이션한다")
        fun getBlockedContents_WithCursorAndLimit_ReturnsPaginatedResults() {
            // Given: 커서와 limit
            val userId = UUID.randomUUID()
            val cursor = "10"
            val limit = 5

            val response = CursorPageResponse(
                content = listOf(
                    BlockedContentItemResponse(
                        blockId = 9L,
                        contentId = UUID.randomUUID().toString(),
                        title = "Blocked Content 9",
                        thumbnailUrl = "https://example.com/thumb9.jpg",
                        creatorNickname = "Creator 9",
                        blockedAt = Instant.now()
                    )
                ),
                nextCursor = "9",
                hasNext = true,
                count = 1
            )

            every { contentBlockService.getBlockedContents(userId, cursor, limit) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri { builder ->
                    builder.path("${ApiPaths.API_V1}/contents/blocks")
                        .queryParam("cursor", cursor)
                        .queryParam("limit", limit)
                        .build()
                }
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content.length()").isEqualTo(1)
                .jsonPath("$.nextCursor").isEqualTo("9")
                .jsonPath("$.hasNext").isEqualTo(true)
                .jsonPath("$.count").isEqualTo(1)

            verify(exactly = 1) { contentBlockService.getBlockedContents(userId, cursor, limit) }
        }

        @Test
        @DisplayName("차단한 콘텐츠가 없으면 빈 목록을 반환한다")
        fun getBlockedContents_WhenNoBlocks_ReturnsEmptyList() {
            // Given: 차단이 없는 상태
            val userId = UUID.randomUUID()

            val response = CursorPageResponse<BlockedContentItemResponse>(
                content = emptyList(),
                nextCursor = null,
                hasNext = false,
                count = 0
            )

            every { contentBlockService.getBlockedContents(userId, null, 20) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .get()
                .uri("${ApiPaths.API_V1}/contents/blocks")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(0)

            verify(exactly = 1) { contentBlockService.getBlockedContents(userId, null, 20) }
        }
    }
}
