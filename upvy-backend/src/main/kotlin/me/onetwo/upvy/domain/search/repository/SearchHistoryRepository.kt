package me.onetwo.upvy.domain.search.repository

import me.onetwo.upvy.domain.search.model.SearchHistory
import me.onetwo.upvy.domain.search.model.SearchType
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 검색 기록 레포지토리 인터페이스 (Reactive)
 *
 * 사용자의 검색 기록을 관리합니다.
 * JOOQ를 사용하여 SQL을 생성하고, R2DBC를 통해 실행합니다.
 * 모든 메서드는 Mono를 반환하여 완전한 Non-blocking 처리를 지원합니다.
 */
interface SearchHistoryRepository {

    /**
     * 검색 기록 저장
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @param searchType 검색 타입
     * @return 저장된 검색 기록 (Mono)
     */
    fun save(userId: UUID, keyword: String, searchType: SearchType): Mono<SearchHistory>

    /**
     * 사용자의 최근 검색어 조회 (중복 제거)
     *
     * 동일한 키워드는 가장 최근 검색만 표시됩니다.
     *
     * @param userId 사용자 ID
     * @param limit 최대 개수
     * @return 최근 검색어 목록 (최신순)
     */
    fun findRecentByUserId(userId: UUID, limit: Int): Mono<List<SearchHistory>>

    /**
     * 특정 검색어 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun deleteByUserIdAndKeyword(userId: UUID, keyword: String): Mono<Void>

    /**
     * 전체 검색 기록 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    fun deleteAllByUserId(userId: UUID): Mono<Void>
}
