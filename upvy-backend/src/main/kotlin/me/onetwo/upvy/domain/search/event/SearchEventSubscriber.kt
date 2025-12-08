package me.onetwo.upvy.domain.search.event

import jakarta.annotation.PostConstruct
import me.onetwo.upvy.domain.search.service.SearchHistoryService
import me.onetwo.upvy.infrastructure.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * 검색 이벤트 Subscriber
 *
 * Reactor Sinks API를 사용한 완전한 Reactive Event Listener
 *
 * ## Reactor Sinks API 패턴
 * - @PostConstruct에서 Flux 구독 시작
 * - filter()로 SearchPerformedEvent만 처리
 * - flatMap()으로 비동기 처리 (Mono 반환)
 * - subscribeOn(boundedElastic())으로 별도 스레드 실행
 * - onErrorResume()으로 에러 격리 (메인 로직 영향 없음)
 *
 * ## 처리 흐름
 * 1. SearchPerformedEvent 수신
 * 2. userId가 있으면 SearchHistoryService를 통해 검색 기록 저장
 * 3. (향후 구현 예정) Redis trending keywords 카운트 증가
 *
 * ## 장애 격리
 * - search_history 저장 실패해도 검색 API는 성공
 * - onErrorResume으로 예외 흡수
 * - ERROR 로그만 남김
 *
 * @property searchHistoryService 검색 기록 서비스
 * @property domainEventFlux 도메인 이벤트 Flux
 */
@Component
class SearchEventSubscriber(
    private val searchHistoryService: SearchHistoryService,
    private val domainEventFlux: Flux<DomainEvent>
) {

    /**
     * 이벤트 구독 시작
     *
     * 애플리케이션 시작 시 자동으로 Flux 구독
     */
    @PostConstruct
    fun subscribe() {
        domainEventFlux
            .filter { it is SearchPerformedEvent }
            .cast(SearchPerformedEvent::class.java)
            .flatMap { event ->
                handleSearchPerformedEvent(event)
                    .subscribeOn(Schedulers.boundedElastic())  // 별도 스레드에서 실행
                    .onErrorResume { error ->
                        logger.error(
                            "Failed to handle SearchPerformedEvent: " +
                            "userId={}, keyword={}, searchType={}",
                            event.userId,
                            event.keyword,
                            event.searchType,
                            error
                        )
                        Mono.empty()  // 에러 격리: 메인 로직에 영향 없음
                    }
            }
            .subscribe()

        logger.info("SearchEventSubscriber started")
    }

    /**
     * 검색 수행 이벤트 처리
     *
     * 인증된 사용자의 검색 기록만 저장합니다.
     *
     * @param event 검색 수행 이벤트
     * @return Mono<Void>
     */
    private fun handleSearchPerformedEvent(event: SearchPerformedEvent): Mono<Void> {
        logger.debug(
            "Handling SearchPerformedEvent: userId={}, keyword={}, searchType={}",
            event.userId,
            event.keyword,
            event.searchType
        )

        // 인증된 사용자만 검색 기록 저장 (Service를 통해 처리)
        return event.userId?.let { userId ->
            searchHistoryService.saveSearchHistory(userId, event.keyword, event.searchType)
                .doOnSuccess {
                    logger.debug(
                        "Search history saved: userId={}, keyword={}",
                        userId,
                        event.keyword
                    )
                }
        } ?: Mono.empty()  // 비인증 사용자는 스킵

        // Note: Redis trending keywords 카운트 증가 기능은 향후 구현 예정
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SearchEventSubscriber::class.java)
    }
}
