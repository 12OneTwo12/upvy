package me.onetwo.growsnap.domain.content.event

import me.onetwo.growsnap.infrastructure.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 생성 이벤트 - content_interactions 초기화용
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property contentId 생성된 콘텐츠 ID
 * @property creatorId 콘텐츠 생성자 ID
 */
data class ContentCreatedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val contentId: UUID,
    val creatorId: UUID
) : DomainEvent
