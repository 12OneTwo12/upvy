package me.onetwo.growsnap.domain.content.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("콘텐츠 Controller 통합 테스트")
class ContentControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Nested
    @DisplayName("POST /api/v1/contents/upload-url - Presigned URL 생성")
    inner class GenerateUploadUrl {

        @Test
        @DisplayName("유효한 요청으로 Presigned URL 생성 시, 200과 URL 정보를 반환한다")
        fun generateUploadUrl_WithValidRequest_Returns200AndUrlInfo() {
            // Given: 사용자 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val request = ContentUploadUrlRequest(
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_CONTENTS}/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isNotEmpty
                .jsonPath("$.uploadUrl").isNotEmpty
                .jsonPath("$.expiresIn").isNumber
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId} - 콘텐츠 조회")
    inner class GetContent {

        @Test
        @DisplayName("존재하는 콘텐츠 조회 시, 200과 콘텐츠 정보를 반환한다")
        fun getContent_WhenContentExists_Returns200AndContentInfo() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(content.id!!.toString())
                .jsonPath("$.creatorId").isEqualTo(user.id!!.toString())
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 조회 시, 404를 반환한다")
        fun getContent_WhenContentNotExists_Returns404() {
            // Given: 존재하지 않는 contentId
            val nonExistentId = UUID.randomUUID()

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", nonExistentId.toString())
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId} - 콘텐츠 삭제")
    inner class DeleteContent {

        @Test
        @DisplayName("본인이 작성한 콘텐츠 삭제 시, 204를 반환한다")
        fun deleteContent_WhenOwner_Returns204() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isNoContent

            // Then: 소프트 삭제 확인
            val deletedContent = contentRepository.findById(content.id!!).block()
            assertThat(deletedContent?.deletedAt).isNotNull
        }
    }
}
