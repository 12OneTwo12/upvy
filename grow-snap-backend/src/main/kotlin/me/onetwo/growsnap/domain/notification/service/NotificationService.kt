package me.onetwo.growsnap.domain.notification.service

import me.onetwo.growsnap.domain.notification.dto.NotificationListResponse
import me.onetwo.growsnap.domain.notification.dto.NotificationResponse
import me.onetwo.growsnap.domain.notification.dto.UnreadNotificationCountResponse
import me.onetwo.growsnap.domain.notification.model.Notification
import me.onetwo.growsnap.domain.notification.model.NotificationTargetType
import me.onetwo.growsnap.domain.notification.model.NotificationType
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 알림 서비스 인터페이스
 *
 * 알림 생성, 조회, 읽음 처리, 삭제 관련 비즈니스 로직을 정의합니다.
 */
interface NotificationService {

    /**
     * 알림 생성
     *
     * @param userId 알림 수신자 ID
     * @param type 알림 유형
     * @param title 알림 제목
     * @param body 알림 본문
     * @param actorId 알림 발생 주체 ID
     * @param targetType 타겟 유형
     * @param targetId 타겟 ID
     * @param data 추가 데이터 (JSON 문자열)
     * @return 생성된 알림
     */
    fun createNotification(
        userId: UUID,
        type: NotificationType,
        title: String,
        body: String,
        actorId: UUID? = null,
        targetType: NotificationTargetType? = null,
        targetId: UUID? = null,
        data: String? = null
    ): Mono<Notification>

    /**
     * 알림 목록 조회 (커서 기반 페이징)
     *
     * @param userId 사용자 ID
     * @param cursor 마지막으로 조회한 알림 ID (없으면 null)
     * @param limit 조회할 개수 (기본값: 20)
     * @return 알림 목록 응답
     */
    fun getNotifications(userId: UUID, cursor: Long?, limit: Int = 20): Mono<NotificationListResponse>

    /**
     * 읽지 않은 알림 수 조회
     *
     * @param userId 사용자 ID
     * @return 읽지 않은 알림 수
     */
    fun getUnreadCount(userId: UUID): Mono<UnreadNotificationCountResponse>

    /**
     * 개별 알림 읽음 처리
     *
     * @param notificationId 알림 ID
     * @param userId 사용자 ID
     * @return 읽음 처리된 알림
     */
    fun markAsRead(notificationId: Long, userId: UUID): Mono<NotificationResponse>

    /**
     * 모든 알림 읽음 처리
     *
     * @param userId 사용자 ID
     * @return 완료 신호
     */
    fun markAllAsRead(userId: UUID): Mono<Void>

    /**
     * 개별 알림 삭제
     *
     * @param notificationId 알림 ID
     * @param userId 사용자 ID
     * @return 완료 신호
     */
    fun deleteNotification(notificationId: Long, userId: UUID): Mono<Void>
}
