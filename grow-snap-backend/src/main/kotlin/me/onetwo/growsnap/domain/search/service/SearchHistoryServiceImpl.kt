package me.onetwo.growsnap.domain.search.service

import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.domain.search.repository.SearchHistoryRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 검색 기록 Service 구현체
 *
 * @property searchHistoryRepository 검색 기록 Repository
 */
@Service
class SearchHistoryServiceImpl(
    private val searchHistoryRepository: SearchHistoryRepository
) : SearchHistoryService {

    /**
     * 검색 기록 저장
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @param searchType 검색 타입
     * @return Mono<Void>
     */
    override fun saveSearchHistory(userId: UUID, keyword: String, searchType: SearchType): Mono<Void> {
        return searchHistoryRepository.save(userId, keyword, searchType)
            .doOnSuccess {
                logger.debug("Search history saved: userId={}, keyword={}, searchType={}", userId, keyword, searchType)
            }
            .then()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SearchHistoryServiceImpl::class.java)
    }
}
