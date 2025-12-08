package me.onetwo.upvy.infrastructure.event

import java.time.Instant
import java.util.UUID

/**
 * 도메인 이벤트 기본 인터페이스
 *
 * Reactor Sinks API 기반 Reactive Event Bus에서 사용되는 모든 이벤트의 베이스 인터페이스
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 */
interface DomainEvent {
    val eventId: UUID
    val occurredAt: Instant
}
