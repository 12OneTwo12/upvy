package me.onetwo.upvy.domain.notification.controller

import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.notification.dto.UpdateNotificationSettingsRequest
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.util.createUserWithProfile
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("알림 설정 Controller 통합 테스트")
class NotificationSettingsControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Test
    @DisplayName("알림 설정 조회 시 설정이 없으면 기본 설정 생성")
    fun getMySettings_CreatesDefaultIfNotExists() {
        // Given: 사용자 생성 (알림 설정은 없음)
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 알림 설정 조회
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)
    }

    @Test
    @DisplayName("알림 설정 조회 성공")
    fun getMySettings_Success() {
        // Given: 사용자와 알림 설정 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // 알림 설정 조회 (자동 생성됨)
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk

        // When & Then: 다시 조회
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)
    }

    @Test
    @DisplayName("알림 설정 전체 수정 성공")
    fun updateMySettings_FullUpdate_Success() {
        // Given: 사용자와 알림 설정 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // 알림 설정 조회 (자동 생성)
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk

        val request = UpdateNotificationSettingsRequest(
            allNotificationsEnabled = false,
            likeNotificationsEnabled = false,
            commentNotificationsEnabled = true,
            followNotificationsEnabled = true
        )

        // When & Then: 알림 설정 수정
        webTestClient
            .mutateWith(mockUser(user.id!!))
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
    }

    @Test
    @DisplayName("알림 설정 부분 수정 성공 - 전체 알림만 비활성화")
    fun updateMySettings_PartialUpdate_OnlyAllNotifications() {
        // Given: 사용자와 알림 설정 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // 알림 설정 조회 (자동 생성)
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk

        val request = UpdateNotificationSettingsRequest(
            allNotificationsEnabled = false
        )

        // When & Then: 전체 알림만 비활성화
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(false)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)
    }

    @Test
    @DisplayName("알림 설정 부분 수정 성공 - 좋아요 알림만 비활성화")
    fun updateMySettings_PartialUpdate_OnlyLikeNotifications() {
        // Given: 사용자와 알림 설정 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // 알림 설정 조회 (자동 생성)
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk

        val request = UpdateNotificationSettingsRequest(
            likeNotificationsEnabled = false
        )

        // When & Then: 좋아요 알림만 비활성화
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(false)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)
    }

    @Test
    @DisplayName("알림 설정 수정 후 조회 시 수정된 값 반환")
    fun updateThenGet_ReturnsUpdatedValues() {
        // Given: 사용자와 알림 설정 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        val request = UpdateNotificationSettingsRequest(
            allNotificationsEnabled = false,
            likeNotificationsEnabled = false
        )

        // When: 알림 설정 수정
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk

        // Then: 조회 시 수정된 값 반환
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/settings")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.allNotificationsEnabled").isEqualTo(false)
            .jsonPath("$.likeNotificationsEnabled").isEqualTo(false)
            .jsonPath("$.commentNotificationsEnabled").isEqualTo(true)
            .jsonPath("$.followNotificationsEnabled").isEqualTo(true)
    }
}
