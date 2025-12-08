package me.onetwo.upvy.domain.notification.service

import me.onetwo.upvy.domain.notification.model.NotificationTargetType
import me.onetwo.upvy.domain.notification.model.NotificationType
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 푸시 알림 서비스 인터페이스
 *
 * 푸시 알림 발송 및 알림 생성 관련 비즈니스 로직을 정의합니다.
 */
interface PushNotificationService {

    /**
     * 사용자에게 푸시 알림 발송
     *
     * 사용자의 알림 설정을 확인하고, 활성화된 경우 푸시 알림을 발송합니다.
     *
     * @param userId 수신자 ID
     * @param type 알림 유형
     * @param title 알림 제목
     * @param body 알림 본문
     * @param actorId 알림 발생 주체 ID (좋아요/팔로우한 사용자)
     * @param targetType 타겟 유형 (콘텐츠, 댓글 등)
     * @param targetId 타겟 ID
     * @param data 추가 데이터 (JSON 문자열)
     * @return 발송 결과
     */
    fun sendNotification(
        userId: UUID,
        type: NotificationType,
        title: String,
        body: String,
        actorId: UUID? = null,
        targetType: NotificationTargetType? = null,
        targetId: UUID? = null,
        data: String? = null
    ): Mono<Unit>

    /**
     * 좋아요 알림 발송
     *
     * @param contentOwnerId 콘텐츠 소유자 ID (알림 수신자)
     * @param actorId 좋아요 누른 사용자 ID
     * @param actorNickname 좋아요 누른 사용자 닉네임
     * @param contentId 콘텐츠 ID
     * @return 발송 결과
     */
    fun sendLikeNotification(
        contentOwnerId: UUID,
        actorId: UUID,
        actorNickname: String,
        contentId: UUID
    ): Mono<Unit>

    /**
     * 댓글 알림 발송
     *
     * @param contentOwnerId 콘텐츠 소유자 ID (알림 수신자)
     * @param actorId 댓글 작성자 ID
     * @param actorNickname 댓글 작성자 닉네임
     * @param contentId 콘텐츠 ID
     * @param commentId 댓글 ID
     * @return 발송 결과
     */
    fun sendCommentNotification(
        contentOwnerId: UUID,
        actorId: UUID,
        actorNickname: String,
        contentId: UUID,
        commentId: UUID
    ): Mono<Unit>

    /**
     * 답글 알림 발송
     *
     * @param commentOwnerId 댓글 소유자 ID (알림 수신자)
     * @param actorId 답글 작성자 ID
     * @param actorNickname 답글 작성자 닉네임
     * @param contentId 콘텐츠 ID
     * @param commentId 답글 ID
     * @return 발송 결과
     */
    fun sendReplyNotification(
        commentOwnerId: UUID,
        actorId: UUID,
        actorNickname: String,
        contentId: UUID,
        commentId: UUID
    ): Mono<Unit>

    /**
     * 팔로우 알림 발송
     *
     * @param followedUserId 팔로우 받은 사용자 ID (알림 수신자)
     * @param actorId 팔로우 한 사용자 ID
     * @param actorNickname 팔로우 한 사용자 닉네임
     * @return 발송 결과
     */
    fun sendFollowNotification(
        followedUserId: UUID,
        actorId: UUID,
        actorNickname: String
    ): Mono<Unit>
}
