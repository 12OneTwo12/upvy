package me.onetwo.upvy.domain.notification.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.notification.dto.NotificationListResponse
import me.onetwo.upvy.domain.notification.dto.NotificationResponse
import me.onetwo.upvy.domain.notification.dto.UnreadNotificationCountResponse
import me.onetwo.upvy.domain.notification.model.NotificationTargetType
import me.onetwo.upvy.domain.notification.model.NotificationType
import me.onetwo.upvy.domain.notification.service.NotificationService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.restdocs.request.RequestDocumentation.queryParameters
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * NotificationController 단위 테스트 + Spring REST Docs
 */
@WebFluxTest(controllers = [NotificationController::class])
@Import(TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("알림 Controller 테스트")
class NotificationControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var notificationService: NotificationService

    private val testUserId = UUID.randomUUID()
    private val testNotificationId = 1L

    private fun createTestNotificationResponse(
        id: Long = testNotificationId,
        type: NotificationType = NotificationType.LIKE,
        title: String = "새로운 좋아요",
        body: String = "testUser님이 게시물을 좋아합니다.",
        isRead: Boolean = false
    ): NotificationResponse {
        return NotificationResponse(
            id = id,
            type = type,
            title = title,
            body = body,
            data = """{"contentId": "123"}""",
            isRead = isRead,
            actorId = UUID.randomUUID().toString(),
            actorNickname = "testUser",
            actorProfileImageUrl = "https://example.com/profile.jpg",
            targetType = NotificationTargetType.CONTENT,
            targetId = UUID.randomUUID().toString(),
            createdAt = Instant.now()
        )
    }

    @Test
    @DisplayName("알림 목록 조회 성공")
    fun getNotifications_Success() {
        // Given
        val notifications = listOf(
            createTestNotificationResponse(1L, NotificationType.LIKE, "새로운 좋아요", "userA님이 게시물을 좋아합니다."),
            createTestNotificationResponse(2L, NotificationType.COMMENT, "새로운 댓글", "userB님이 댓글을 남겼습니다."),
            createTestNotificationResponse(3L, NotificationType.FOLLOW, "새로운 팔로워", "userC님이 팔로우하기 시작했습니다.")
        )
        val response = NotificationListResponse(
            notifications = notifications,
            nextCursor = 3L,
            hasNext = true
        )
        every { notificationService.getNotifications(testUserId, null, 20) } returns Mono.just(response)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.notifications").isArray
            .jsonPath("$.notifications.length()").isEqualTo(3)
            .jsonPath("$.nextCursor").isEqualTo(3)
            .jsonPath("$.hasNext").isEqualTo(true)
            .consumeWith(
                document(
                    "notification-list",
                    preprocessResponse(prettyPrint()),
                    queryParameters(
                        parameterWithName("cursor").description("마지막으로 조회한 알림 ID (커서 페이징)").optional(),
                        parameterWithName("limit").description("조회할 개수 (기본값: 20, 최대: 50)").optional()
                    ),
                    responseFields(
                        fieldWithPath("notifications").description("알림 목록"),
                        fieldWithPath("notifications[].id").description("알림 ID"),
                        fieldWithPath("notifications[].type").description("알림 유형 (LIKE, COMMENT, REPLY, FOLLOW)"),
                        fieldWithPath("notifications[].title").description("알림 제목"),
                        fieldWithPath("notifications[].body").description("알림 본문"),
                        fieldWithPath("notifications[].data").description("추가 데이터 (JSON)"),
                        fieldWithPath("notifications[].isRead").description("읽음 여부"),
                        fieldWithPath("notifications[].actorId").description("알림 발생 주체 ID"),
                        fieldWithPath("notifications[].actorNickname").description("알림 발생 주체 닉네임"),
                        fieldWithPath("notifications[].actorProfileImageUrl").description("알림 발생 주체 프로필 이미지"),
                        fieldWithPath("notifications[].targetType").description("타겟 유형 (CONTENT, COMMENT, USER)"),
                        fieldWithPath("notifications[].targetId").description("타겟 ID"),
                        fieldWithPath("notifications[].createdAt").description("알림 생성 시각"),
                        fieldWithPath("nextCursor").description("다음 페이지 커서 (null이면 마지막 페이지)"),
                        fieldWithPath("hasNext").description("다음 페이지 존재 여부")
                    )
                )
            )

        verify(exactly = 1) { notificationService.getNotifications(testUserId, null, 20) }
    }

    @Test
    @DisplayName("커서 기반 알림 목록 조회 성공")
    fun getNotifications_WithCursor_Success() {
        // Given
        val notifications = listOf(
            createTestNotificationResponse(4L),
            createTestNotificationResponse(5L)
        )
        val response = NotificationListResponse(
            notifications = notifications,
            nextCursor = null,
            hasNext = false
        )
        every { notificationService.getNotifications(testUserId, 3L, 20) } returns Mono.just(response)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri { builder ->
                builder.path("${ApiPaths.API_V1_NOTIFICATIONS}")
                    .queryParam("cursor", 3L)
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.notifications.length()").isEqualTo(2)
            .jsonPath("$.hasNext").isEqualTo(false)
            .jsonPath("$.nextCursor").doesNotExist()

        verify(exactly = 1) { notificationService.getNotifications(testUserId, 3L, 20) }
    }

    @Test
    @DisplayName("읽지 않은 알림 수 조회 성공")
    fun getUnreadCount_Success() {
        // Given
        val response = UnreadNotificationCountResponse(unreadCount = 5)
        every { notificationService.getUnreadCount(testUserId) } returns Mono.just(response)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/unread-count")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.unreadCount").isEqualTo(5)
            .consumeWith(
                document(
                    "notification-unread-count",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("unreadCount").description("읽지 않은 알림 수")
                    )
                )
            )

        verify(exactly = 1) { notificationService.getUnreadCount(testUserId) }
    }

    @Test
    @DisplayName("개별 알림 읽음 처리 성공")
    fun markAsRead_Success() {
        // Given
        val response = createTestNotificationResponse(testNotificationId, isRead = true)
        every { notificationService.markAsRead(testNotificationId, testUserId) } returns Mono.just(response)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/{notificationId}/read", testNotificationId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(testNotificationId.toInt())
            .jsonPath("$.isRead").isEqualTo(true)
            .consumeWith(
                document(
                    "notification-mark-read",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("notificationId").description("알림 ID")
                    )
                )
            )

        verify(exactly = 1) { notificationService.markAsRead(testNotificationId, testUserId) }
    }

    @Test
    @DisplayName("모든 알림 읽음 처리 성공")
    fun markAllAsRead_Success() {
        // Given
        every { notificationService.markAllAsRead(testUserId) } returns Mono.empty()

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .patch()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/read-all")
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "notification-mark-all-read"
                )
            )

        verify(exactly = 1) { notificationService.markAllAsRead(testUserId) }
    }

    @Test
    @DisplayName("개별 알림 삭제 성공")
    fun deleteNotification_Success() {
        // Given
        every { notificationService.deleteNotification(testNotificationId, testUserId) } returns Mono.empty()

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .delete()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/{notificationId}", testNotificationId)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "notification-delete",
                    pathParameters(
                        parameterWithName("notificationId").description("알림 ID")
                    )
                )
            )

        verify(exactly = 1) { notificationService.deleteNotification(testNotificationId, testUserId) }
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 알림 목록 조회 시 401 반환")
    fun getNotifications_Unauthorized() {
        // When & Then
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    @DisplayName("인증되지 않은 사용자의 알림 삭제 시 401 반환")
    fun deleteNotification_Unauthorized() {
        // When & Then
        webTestClient
            .delete()
            .uri("${ApiPaths.API_V1_NOTIFICATIONS}/{notificationId}", testNotificationId)
            .exchange()
            .expectStatus().isUnauthorized
    }
}
