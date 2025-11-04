package me.onetwo.growsnap.domain.user.event

import me.onetwo.growsnap.infrastructure.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 팔로우 이벤트 - 알림 생성용
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property followerId 팔로우한 사용자 ID
 * @property followingId 팔로우된 사용자 ID
 */
data class FollowEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val followerId: UUID,
    val followingId: UUID
) : DomainEvent
