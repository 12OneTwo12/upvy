package me.onetwo.growsnap.domain.user.event

import me.onetwo.growsnap.infrastructure.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 사용자 생성 이벤트
 *
 * 신규 사용자 가입 시 발행되며, 비동기로 알림 설정 등 후속 처리가 수행됩니다.
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property userId 생성된 사용자 ID
 */
data class UserCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val userId: UUID
) : DomainEvent
