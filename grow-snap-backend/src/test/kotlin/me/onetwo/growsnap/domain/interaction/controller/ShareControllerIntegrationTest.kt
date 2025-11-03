package me.onetwo.growsnap.domain.interaction.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("공유 Controller 통합 테스트")
class ShareControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @Nested
    @DisplayName("POST /api/v1/contents/{contentId}/share - 콘텐츠 공유")
    inner class ShareContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 공유 시, 200 OK와 공유 응답을 반환한다")
        fun shareContent_WithValidRequest_ReturnsShareResponse() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/share", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.shareCount").isNumber
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}/share-link - 공유 링크 조회")
    inner class GetShareLink {

        @Test
        @DisplayName("공유 링크 조회 시, 200 OK와 공유 링크를 반환한다")
        fun getShareLink_WithValidRequest_ReturnsShareLink() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/share-link", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.shareUrl").isNotEmpty
        }
    }
}
