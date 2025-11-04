package me.onetwo.growsnap.domain.analytics.event

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.infrastructure.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 사용자 인터랙션 이벤트
 *
 * user_content_interactions 저장용 (협업 필터링 데이터)
 * - 비동기 처리로 메인 로직과 격리
 * - 저장 실패해도 메인 요청에 영향 없음
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 * @property interactionType 인터랙션 타입 (LIKE, SAVE, SHARE)
 */
data class UserInteractionEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val userId: UUID,
    val contentId: UUID,
    val interactionType: InteractionType
) : DomainEvent
