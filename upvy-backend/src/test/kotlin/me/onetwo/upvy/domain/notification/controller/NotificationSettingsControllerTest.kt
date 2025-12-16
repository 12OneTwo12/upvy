package me.onetwo.upvy.domain.notification.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.notification.dto.UpdateNotificationSettingsRequest
import me.onetwo.upvy.domain.notification.model.NotificationSettings
import me.onetwo.upvy.domain.notification.service.NotificationSettingsService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * NotificationSettingsController 단위 테스트 + Spring REST Docs
 */
@WebFluxTest(controllers = [NotificationSettingsController::class])
@Import(TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("알림 설정 Controller 테스트")
class NotificationSettingsControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var notificationSettingsService: NotificationSettingsService

    private val testUserId = UUID.randomUUID()

    private fun createTestSettings(
        userId: UUID = testUserId,
        allNotificationsEnabled: Boolean = true,
        likeNotificationsEnabled: Boolean = true,
        commentNotificationsEnabled: Boolean = true,
        followNotificationsEnabled: Boolean = true
    ): NotificationSettings {
        return NotificationSettings(
            id = 1L,
            userId = userId,
            allNotificationsEnabled = allNotificationsEnabled,
            likeNotificationsEnabled = likeNotificationsEnabled,
            commentNotificationsEnabled = commentNotificationsEnabled,
            followNotificationsEnabled = followNotificationsEnabled,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    @Test
    @DisplayName("내 알림 설정 조회 성공")
    fun getMySettings_Success() {
        // Given
        val settings = createTestSettings()
        every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(settings)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)
            .consumeWith(
                document(
                    "notification-settings-get",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("allNotificationsEnabled").description("전체 알림 활성화 여부"),
                        fieldWithPath("likeNotificationsEnabled").description("좋아요 알림 활성화 여부"),
                        fieldWithPath("commentNotificationsEnabled").description("댓글 알림 활성화 여부"),
                        fieldWithPath("followNotificationsEnabled").description("팔로우 알림 활성화 여부"),
                        fieldWithPath("updatedAt").description("마지막 수정 시각")
                    )
                )
            )

        verify(exactly = 1) { notificationSettingsService.getSettings(testUserId) }
    }

    @Test
    @DisplayName("내 알림 설정 조회 시 설정이 없으면 기본 설정 생성")
    fun getMySettings_CreatesDefaultIfNotExists() {
        // Given
        val defaultSettings = createTestSettings()
        every { notificationSettingsService.getSettings(testUserId) } returns Mono.just(defaultSettings)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)

        verify(exactly = 1) { notificationSettingsService.getSettings(testUserId) }
    }

    @Test
    @DisplayName("내 알림 설정 전체 수정 성공")
    fun updateMySettings_FullUpdate_Success() {
        // Given
        val updatedSettings = createTestSettings(
            allNotificationsEnabled = false,
            likeNotificationsEnabled = false,
            commentNotificationsEnabled = true,
            followNotificationsEnabled = true
        )
        val request = UpdateNotificationSettingsRequest(
            allNotificationsEnabled = false,
            likeNotificationsEnabled = false,
            commentNotificationsEnabled = true,
            followNotificationsEnabled = true
        )
        every {
            notificationSettingsService.updateSettings(testUserId, request)
        } returns Mono.just(updatedSettings)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(false)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(false)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)
            .consumeWith(
                document(
                    "notification-settings-update",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    requestFields(
                        fieldWithPath("allNotificationsEnabled").description("전체 알림 활성화 여부 (선택)").optional(),
                        fieldWithPath("likeNotificationsEnabled").description("좋아요 알림 활성화 여부 (선택)").optional(),
                        fieldWithPath("commentNotificationsEnabled").description("댓글 알림 활성화 여부 (선택)").optional(),
                        fieldWithPath("followNotificationsEnabled").description("팔로우 알림 활성화 여부 (선택)").optional()
                    ),
                    responseFields(
                        fieldWithPath("allNotificationsEnabled").description("전체 알림 활성화 여부"),
                        fieldWithPath("likeNotificationsEnabled").description("좋아요 알림 활성화 여부"),
                        fieldWithPath("commentNotificationsEnabled").description("댓글 알림 활성화 여부"),
                        fieldWithPath("followNotificationsEnabled").description("팔로우 알림 활성화 여부"),
                        fieldWithPath("updatedAt").description("마지막 수정 시각")
                    )
                )
            )

        verify(exactly = 1) {
            notificationSettingsService.updateSettings(testUserId, request)
        }
    }

    @Test
    @DisplayName("내 알림 설정 부분 수정 성공")
    fun updateMySettings_PartialUpdate_Success() {
        // Given
        val updatedSettings = createTestSettings(
            allNotificationsEnabled = false,
            likeNotificationsEnabled = true,
            commentNotificationsEnabled = true,
            followNotificationsEnabled = true
        )
        val request = UpdateNotificationSettingsRequest(
            allNotificationsEnabled = false
        )
        every {
            notificationSettingsService.updateSettings(testUserId, request)
        } returns Mono.just(updatedSettings)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(false)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(true)
            .consumeWith(
                document(
                    "notification-settings-partial-update",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint())
                )
            )

        verify(exactly = 1) {
            notificationSettingsService.updateSettings(testUserId, request)
        }
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 알림 설정 조회 시 401 반환")
    fun getMySettings_Unauthorized() {
        // When & Then
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 알림 설정 수정 시 401 반환")
    fun updateMySettings_Unauthorized() {
        // Given
        val request = UpdateNotificationSettingsRequest(allNotificationsEnabled = false)

        // When & Then
        webTestClient
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isUnauthorized
    }
}
