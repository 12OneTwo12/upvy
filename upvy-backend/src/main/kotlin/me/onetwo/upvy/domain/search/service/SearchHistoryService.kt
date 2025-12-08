package me.onetwo.upvy.domain.search.service

import me.onetwo.upvy.domain.search.model.SearchType
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 검색 기록 Service 인터페이스
 */
interface SearchHistoryService {

    /**
     * 검색 기록 저장
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @param searchType 검색 타입
     * @return Mono<Void>
     */
    fun saveSearchHistory(userId: UUID, keyword: String, searchType: SearchType): Mono<Void>
}
