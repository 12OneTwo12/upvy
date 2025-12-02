package me.onetwo.growsnap.domain.analytics.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest
import me.onetwo.growsnap.domain.analytics.dto.ViewEventRequest
import me.onetwo.growsnap.domain.analytics.repository.UserViewHistoryRepository
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("Analytics Controller 통합 테스트")
class AnalyticsControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var userViewHistoryRepository: UserViewHistoryRepository

    @Nested
    @DisplayName("POST /api/v1/analytics/views - 시청 이벤트 기록")
    inner class TrackViewEvent {

        @Test
        @DisplayName("유효한 요청으로 시청 이벤트 기록 시, 204 No Content를 반환한다")
        fun trackViewEvent_WithValidRequest_ReturnsNoContent() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository = contentRepository,
                creatorId = user.id!!
            )

            val request = ViewEventRequest(
                contentId = content.id!!,
                watchedDuration = 120,
                completionRate = 85,
                skipped = false
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent

            // Then: 이벤트가 처리되어 DB에 기록되었는지 확인
            val contentId = content.id!!
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val viewedContentIds = userViewHistoryRepository.findRecentViewedContentIds(
                    user.id!!,
                    java.time.Instant.now().minus(1, ChronoUnit.HOURS),
                    100
                ).collectList().block()!!
                assertThat(viewedContentIds).contains(contentId)
            }
        }

        @Test
        @DisplayName("contentId가 null인 경우, 400 Bad Request를 반환한다")
        fun trackViewEvent_WithNullContentId_ReturnsBadRequest() {
            // Given: 사용자 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val request = mapOf(
                "contentId" to null,
                "watchedDuration" to 120,
                "completionRate" to 85,
                "skipped" to false
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("completionRate가 100을 초과하는 경우, 400 Bad Request를 반환한다")
        fun trackViewEvent_WithInvalidCompletionRate_ReturnsBadRequest() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository = contentRepository,
                creatorId = user.id!!
            )

            val request = mapOf(
                "contentId" to content.id!!.toString(),
                "watchedDuration" to 120,
                "completionRate" to 150,
                "skipped" to false
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest
        }

        @Test
        @DisplayName("스킵 이벤트(3초 이내) 기록 시, 204 No Content를 반환한다")
        fun trackViewEvent_WithSkipEvent_ReturnsNoContent() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository = contentRepository,
                creatorId = user.id!!
            )

            val request = ViewEventRequest(
                contentId = content.id!!,
                watchedDuration = 2,
                completionRate = 5,
                skipped = true
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent
        }

        @Test
        @DisplayName("watchedDuration이 포함된 시청 이벤트 기록 시, 정상 처리된다")
        fun trackViewEvent_WithWatchedDuration_ReturnsNoContent() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository = contentRepository,
                creatorId = user.id!!
            )

            val request = ViewEventRequest(
                contentId = content.id!!,
                watchedDuration = 300,
                completionRate = 60,
                skipped = false
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_ANALYTICS}/views")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent
        }
    }
}
