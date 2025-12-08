package me.onetwo.upvy.domain.notification.event

import me.onetwo.upvy.infrastructure.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 좋아요 알림 이벤트
 *
 * 사용자가 콘텐츠에 좋아요를 눌렀을 때 알림 발송을 위한 이벤트입니다.
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property contentOwnerId 콘텐츠 소유자 ID (알림 수신자)
 * @property actorId 좋아요 누른 사용자 ID
 * @property actorNickname 좋아요 누른 사용자 닉네임
 * @property contentId 콘텐츠 ID
 */
data class LikeNotificationEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val contentOwnerId: UUID,
    val actorId: UUID,
    val actorNickname: String,
    val contentId: UUID
) : DomainEvent

/**
 * 댓글 알림 이벤트
 *
 * 사용자가 콘텐츠에 댓글을 남겼을 때 알림 발송을 위한 이벤트입니다.
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property contentOwnerId 콘텐츠 소유자 ID (알림 수신자)
 * @property actorId 댓글 작성자 ID
 * @property actorNickname 댓글 작성자 닉네임
 * @property contentId 콘텐츠 ID
 * @property commentId 댓글 ID
 */
data class CommentNotificationEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val contentOwnerId: UUID,
    val actorId: UUID,
    val actorNickname: String,
    val contentId: UUID,
    val commentId: UUID
) : DomainEvent

/**
 * 답글 알림 이벤트
 *
 * 사용자가 댓글에 답글을 남겼을 때 알림 발송을 위한 이벤트입니다.
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property commentOwnerId 댓글 소유자 ID (알림 수신자)
 * @property actorId 답글 작성자 ID
 * @property actorNickname 답글 작성자 닉네임
 * @property contentId 콘텐츠 ID
 * @property commentId 답글 ID
 */
data class ReplyNotificationEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val commentOwnerId: UUID,
    val actorId: UUID,
    val actorNickname: String,
    val contentId: UUID,
    val commentId: UUID
) : DomainEvent

/**
 * 팔로우 알림 이벤트
 *
 * 사용자가 다른 사용자를 팔로우했을 때 알림 발송을 위한 이벤트입니다.
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property followedUserId 팔로우 받은 사용자 ID (알림 수신자)
 * @property actorId 팔로우 한 사용자 ID
 * @property actorNickname 팔로우 한 사용자 닉네임
 */
data class FollowNotificationEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val followedUserId: UUID,
    val actorId: UUID,
    val actorNickname: String
) : DomainEvent
