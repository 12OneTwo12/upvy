package me.onetwo.growsnap.domain.search.event

import me.onetwo.growsnap.domain.search.repository.SearchHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import reactor.core.scheduler.Schedulers

/**
 * 검색 이벤트 리스너
 *
 * 검색 수행 이벤트를 구독하여 검색 기록을 비동기로 저장합니다.
 *
 * ## 비동기 처리
 * - @Async 어노테이션으로 별도 스레드에서 실행
 * - 검색 API 응답 속도에 영향을 주지 않음
 *
 * ## 처리 흐름
 * 1. SearchPerformedEvent 수신
 * 2. userId가 있으면 search_history 테이블에 저장
 * 3. TODO: Redis trending keywords 카운트 증가
 *
 * @property searchHistoryRepository 검색 기록 레포지토리
 */
@Component
class SearchEventListener(
    private val searchHistoryRepository: SearchHistoryRepository
) {

    /**
     * 검색 수행 이벤트 처리
     *
     * 인증된 사용자의 검색 기록만 저장합니다.
     *
     * @param event 검색 수행 이벤트
     */
    @Async
    @EventListener
    fun handleSearchPerformed(event: SearchPerformedEvent) {
        logger.debug(
            "Search performed event received: userId={}, keyword={}, searchType={}",
            event.userId,
            event.keyword,
            event.searchType
        )

        // 인증된 사용자만 검색 기록 저장
        event.userId?.let { userId ->
            searchHistoryRepository.save(userId, event.keyword, event.searchType)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    { savedHistory ->
                        logger.debug(
                            "Search history saved: id={}, userId={}, keyword={}",
                            savedHistory.id,
                            userId,
                            event.keyword
                        )
                    },
                    { error ->
                        logger.error(
                            "Failed to save search history: userId={}, keyword={}",
                            userId,
                            event.keyword,
                            error
                        )
                    }
                )
        }

        // TODO: Redis trending keywords 카운트 증가
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SearchEventListener::class.java)
    }
}
