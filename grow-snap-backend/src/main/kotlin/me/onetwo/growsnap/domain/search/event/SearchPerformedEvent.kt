package me.onetwo.growsnap.domain.search.event

import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.infrastructure.event.DomainEvent
import java.time.Instant
import java.util.UUID

/**
 * 검색 수행 이벤트
 *
 * 사용자가 검색을 수행했을 때 발행되는 이벤트입니다.
 *
 * ## Reactor Sinks API 패턴
 *
 * ### 이벤트 처리 흐름
 * 1. SearchService가 검색 수행
 * 2. ReactiveEventPublisher로 SearchPerformedEvent 발행
 * 3. SearchEventSubscriber가 비동기로 처리:
 *    - SearchHistoryService를 통해 검색 기록 저장 (Non-Critical Path)
 *    - Redis trending keywords 카운트 증가 (향후 구현 예정)
 *
 * ### Non-Critical Path
 * - 검색 기록 저장 실패해도 메인 검색 API는 성공
 * - onErrorResume으로 에러 격리
 *
 * @property eventId 이벤트 고유 ID
 * @property occurredAt 이벤트 발생 시각
 * @property userId 검색한 사용자 ID (인증되지 않은 경우 null)
 * @property keyword 검색 키워드
 * @property searchType 검색 타입 (CONTENT, USER)
 */
data class SearchPerformedEvent(
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: Instant = Instant.now(),
    val userId: UUID?,
    val keyword: String,
    val searchType: SearchType
) : DomainEvent
