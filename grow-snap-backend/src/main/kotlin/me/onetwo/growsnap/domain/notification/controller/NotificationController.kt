package me.onetwo.growsnap.domain.notification.controller

import me.onetwo.growsnap.domain.notification.dto.NotificationListResponse
import me.onetwo.growsnap.domain.notification.dto.NotificationResponse
import me.onetwo.growsnap.domain.notification.dto.UnreadNotificationCountResponse
import me.onetwo.growsnap.domain.notification.service.NotificationService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * 알림 Controller
 *
 * 알림 조회, 읽음 처리, 삭제 API를 제공합니다.
 *
 * @property notificationService 알림 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1_NOTIFICATIONS)
class NotificationController(
    private val notificationService: NotificationService
) {

    /**
     * 알림 목록 조회 (커서 기반 페이징)
     *
     * @param principal 인증된 사용자 Principal
     * @param cursor 마지막으로 조회한 알림 ID (없으면 null)
     * @param limit 조회할 개수 (기본값: 20, 최대: 50)
     * @return 알림 목록
     */
    @GetMapping
    fun getNotifications(
        principal: Mono<Principal>,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<NotificationListResponse>> {
        val safeLimit = limit.coerceIn(1, 50)
        return principal
            .toUserId()
            .flatMap { userId -> notificationService.getNotifications(userId, cursor, safeLimit) }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 읽지 않은 알림 수 조회
     *
     * @param principal 인증된 사용자 Principal
     * @return 읽지 않은 알림 수
     */
    @GetMapping("/unread-count")
    fun getUnreadCount(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<UnreadNotificationCountResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> notificationService.getUnreadCount(userId) }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 개별 알림 읽음 처리
     *
     * @param principal 인증된 사용자 Principal
     * @param notificationId 알림 ID
     * @return 읽음 처리된 알림
     */
    @PatchMapping("/{notificationId}/read")
    fun markAsRead(
        principal: Mono<Principal>,
        @PathVariable notificationId: Long
    ): Mono<ResponseEntity<NotificationResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> notificationService.markAsRead(notificationId, userId) }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 모든 알림 읽음 처리
     *
     * @param principal 인증된 사용자 Principal
     * @return 204 No Content
     */
    @PatchMapping("/read-all")
    fun markAllAsRead(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId -> notificationService.markAllAsRead(userId) }
            .then(Mono.just(ResponseEntity.noContent().build()))
    }

    /**
     * 개별 알림 삭제
     *
     * @param principal 인증된 사용자 Principal
     * @param notificationId 알림 ID
     * @return 204 No Content
     */
    @DeleteMapping("/{notificationId}")
    fun deleteNotification(
        principal: Mono<Principal>,
        @PathVariable notificationId: Long
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId -> notificationService.deleteNotification(notificationId, userId) }
            .then(Mono.just(ResponseEntity.noContent().build()))
    }
}
