package me.onetwo.upvy.domain.block.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.block.dto.BlockedUserItemResponse
import me.onetwo.upvy.domain.block.dto.UserBlockResponse
import me.onetwo.upvy.domain.block.exception.BlockException
import me.onetwo.upvy.domain.block.service.UserBlockService
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
 * 사용자 차단 Controller 테스트
 *
 * UserBlockController의 HTTP 요청 처리를 테스트하고 REST Docs 문서를 생성합니다.
 */
@WebFluxTest(UserBlockController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("사용자 차단 Controller 테스트")
class UserBlockControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var userBlockService: UserBlockService

    @Nested
    @DisplayName("POST /api/v1/users/{userId}/block - 사용자 차단")
    inner class BlockUser {

        @Test
        @DisplayName("유효한 요청으로 사용자를 차단하면 201 Created를 반환한다")
        fun blockUser_WithValidRequest_Returns201Created() {
            // Given: 차단할 사용자 정보
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()
            val response = UserBlockResponse(
                id = 1L,
                blockerId = blockerId.toString(),
                blockedId = blockedId.toString(),
                createdAt = Instant.now()
            )

            every { userBlockService.blockUser(blockerId, blockedId) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(blockerId))
                .post()
                .uri("${ApiPaths.API_V1}/users/{userId}/block", blockedId)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.blockerId").isEqualTo(blockerId.toString())
                .jsonPath("$.blockedId").isEqualTo(blockedId.toString())
                .jsonPath("$.createdAt").isNotEmpty
                .consumeWith(
                    document(
                        "user-block-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("userId").description("차단할 사용자 ID")
                        ),
                        responseFields(
                            fieldWithPath("id").description("차단 ID"),
                            fieldWithPath("blockerId").description("차단한 사용자 ID"),
                            fieldWithPath("blockedId").description("차단된 사용자 ID"),
                            fieldWithPath("createdAt").description("차단 시각")
                        )
                    )
                )

            verify(exactly = 1) { userBlockService.blockUser(blockerId, blockedId) }
        }

        @Test
        @DisplayName("자기 자신을 차단하려는 경우 400 Bad Request를 반환한다")
        fun blockUser_WhenBlockingSelf_Returns400BadRequest() {
            // Given: 동일한 사용자 ID
            val userId = UUID.randomUUID()

            every { userBlockService.blockUser(userId, userId) } returns
                Mono.error(BlockException.SelfBlockException(userId.toString()))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/users/{userId}/block", userId)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.code").isEqualTo("SELF_BLOCK_NOT_ALLOWED")
                .consumeWith(
                    document(
                        "user-block-self-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("userId").description("차단할 사용자 ID")
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

            verify(exactly = 1) { userBlockService.blockUser(userId, userId) }
        }

        @Test
        @DisplayName("이미 차단한 사용자인 경우 409 Conflict를 반환한다")
        fun blockUser_WhenAlreadyBlocked_Returns409Conflict() {
            // Given: 이미 차단한 사용자
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()

            every { userBlockService.blockUser(blockerId, blockedId) } returns
                Mono.error(BlockException.DuplicateUserBlockException(blockerId.toString(), blockedId.toString()))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(blockerId))
                .post()
                .uri("${ApiPaths.API_V1}/users/{userId}/block", blockedId)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_USER_BLOCK")
                .consumeWith(
                    document(
                        "user-block-duplicate-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("userId").description("차단할 사용자 ID")
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

            verify(exactly = 1) { userBlockService.blockUser(blockerId, blockedId) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/{userId}/block - 사용자 차단 해제")
    inner class UnblockUser {

        @Test
        @DisplayName("유효한 요청으로 사용자 차단을 해제하면 204 No Content를 반환한다")
        fun unblockUser_WithValidRequest_Returns204NoContent() {
            // Given: 차단 해제할 사용자 정보
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()

            every { userBlockService.unblockUser(blockerId, blockedId) } returns Mono.empty()

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(blockerId))
                .delete()
                .uri("${ApiPaths.API_V1}/users/{userId}/block", blockedId)
                .exchange()
                .expectStatus().isNoContent
                .expectBody()
                .consumeWith(
                    document(
                        "user-block-delete",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("userId").description("차단 해제할 사용자 ID")
                        )
                    )
                )

            verify(exactly = 1) { userBlockService.unblockUser(blockerId, blockedId) }
        }

        @Test
        @DisplayName("차단하지 않은 사용자인 경우 404 Not Found를 반환한다")
        fun unblockUser_WhenNotBlocked_Returns404NotFound() {
            // Given: 차단하지 않은 사용자
            val blockerId = UUID.randomUUID()
            val blockedId = UUID.randomUUID()

            every { userBlockService.unblockUser(blockerId, blockedId) } returns
                Mono.error(BlockException.UserBlockNotFoundException(blockerId.toString(), blockedId.toString()))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(blockerId))
                .delete()
                .uri("${ApiPaths.API_V1}/users/{userId}/block", blockedId)
                .exchange()
                .expectStatus().isNotFound
                .expectBody()
                .jsonPath("$.code").isEqualTo("USER_BLOCK_NOT_FOUND")
                .consumeWith(
                    document(
                        "user-block-not-found-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("userId").description("차단 해제할 사용자 ID")
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

            verify(exactly = 1) { userBlockService.unblockUser(blockerId, blockedId) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/blocks - 차단한 사용자 목록 조회")
    inner class GetBlockedUsers {

        @Test
        @DisplayName("차단한 사용자 목록을 조회하면 200 OK를 반환한다")
        fun getBlockedUsers_ReturnsBlockedUsersList() {
            // Given: 차단한 사용자 목록
            val blockerId = UUID.randomUUID()
            val blockedUser1 = BlockedUserItemResponse(
                blockId = 1L,
                userId = UUID.randomUUID().toString(),
                nickname = "Blocked User 1",
                profileImageUrl = "https://example.com/profile1.jpg",
                blockedAt = Instant.now()
            )
            val blockedUser2 = BlockedUserItemResponse(
                blockId = 2L,
                userId = UUID.randomUUID().toString(),
                nickname = "Blocked User 2",
                profileImageUrl = null,
                blockedAt = Instant.now()
            )

            val response = CursorPageResponse(
                content = listOf(blockedUser1, blockedUser2),
                nextCursor = null,
                hasNext = false,
                count = 2
            )

            every { userBlockService.getBlockedUsers(blockerId, null, 20) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(blockerId))
                .get()
                .uri("${ApiPaths.API_V1}/users/blocks")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content.length()").isEqualTo(2)
                .jsonPath("$.content[0].blockId").isEqualTo(1)
                .jsonPath("$.content[0].userId").isNotEmpty
                .jsonPath("$.content[0].nickname").isEqualTo("Blocked User 1")
                .jsonPath("$.content[0].profileImageUrl").isEqualTo("https://example.com/profile1.jpg")
                .jsonPath("$.content[0].blockedAt").isNotEmpty
                .jsonPath("$.content[1].blockId").isEqualTo(2)
                .jsonPath("$.content[1].nickname").isEqualTo("Blocked User 2")
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(2)
                .consumeWith(
                    document(
                        "user-blocks-list",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                            parameterWithName("cursor").description("커서 (차단 ID, 선택)").optional(),
                            parameterWithName("limit").description("조회 개수 (기본값: 20, 선택)").optional()
                        ),
                        responseFields(
                            fieldWithPath("content").description("차단한 사용자 목록"),
                            fieldWithPath("content[].blockId").description("차단 ID"),
                            fieldWithPath("content[].userId").description("차단된 사용자 ID"),
                            fieldWithPath("content[].nickname").description("닉네임"),
                            fieldWithPath("content[].profileImageUrl").description("프로필 이미지 URL").optional(),
                            fieldWithPath("content[].blockedAt").description("차단 시각"),
                            fieldWithPath("nextCursor").description("다음 페이지 커서").optional(),
                            fieldWithPath("hasNext").description("다음 페이지 존재 여부"),
                            fieldWithPath("count").description("조회된 항목 수")
                        )
                    )
                )

            verify(exactly = 1) { userBlockService.getBlockedUsers(blockerId, null, 20) }
        }

        @Test
        @DisplayName("커서와 limit을 사용하여 페이지네이션한다")
        fun getBlockedUsers_WithCursorAndLimit_ReturnsPaginatedResults() {
            // Given: 커서와 limit
            val blockerId = UUID.randomUUID()
            val cursor = "10"
            val limit = 5

            val response = CursorPageResponse(
                content = listOf(
                    BlockedUserItemResponse(
                        blockId = 9L,
                        userId = UUID.randomUUID().toString(),
                        nickname = "Blocked User 9",
                        profileImageUrl = null,
                        blockedAt = Instant.now()
                    )
                ),
                nextCursor = "9",
                hasNext = true,
                count = 1
            )

            every { userBlockService.getBlockedUsers(blockerId, cursor, limit) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(blockerId))
                .get()
                .uri { builder ->
                    builder.path("${ApiPaths.API_V1}/users/blocks")
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

            verify(exactly = 1) { userBlockService.getBlockedUsers(blockerId, cursor, limit) }
        }

        @Test
        @DisplayName("차단한 사용자가 없으면 빈 목록을 반환한다")
        fun getBlockedUsers_WhenNoBlocks_ReturnsEmptyList() {
            // Given: 차단이 없는 상태
            val blockerId = UUID.randomUUID()

            val response = CursorPageResponse<BlockedUserItemResponse>(
                content = emptyList(),
                nextCursor = null,
                hasNext = false,
                count = 0
            )

            every { userBlockService.getBlockedUsers(blockerId, null, 20) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(blockerId))
                .get()
                .uri("${ApiPaths.API_V1}/users/blocks")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.content").isArray
                .jsonPath("$.content.length()").isEqualTo(0)
                .jsonPath("$.hasNext").isEqualTo(false)
                .jsonPath("$.count").isEqualTo(0)

            verify(exactly = 1) { userBlockService.getBlockedUsers(blockerId, null, 20) }
        }
    }
}
