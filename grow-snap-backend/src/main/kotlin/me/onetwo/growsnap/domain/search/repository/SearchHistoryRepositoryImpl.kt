package me.onetwo.growsnap.domain.search.repository

import me.onetwo.growsnap.domain.search.model.SearchHistory
import me.onetwo.growsnap.domain.search.model.SearchType
import me.onetwo.growsnap.jooq.generated.tables.SearchHistory.Companion.SEARCH_HISTORY
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

/**
 * 검색 기록 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class SearchHistoryRepositoryImpl(
    private val dslContext: DSLContext
) : SearchHistoryRepository {

    /**
     * 검색 기록 저장
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @param searchType 검색 타입
     * @return 저장된 검색 기록 (Mono)
     */
    override fun save(userId: UUID, keyword: String, searchType: SearchType): Mono<SearchHistory> {
        val now = LocalDateTime.now()
        val userIdStr = userId.toString()

        logger.debug("Saving search history: userId={}, keyword={}, searchType={}", userId, keyword, searchType)

        return Mono.from(
            dslContext
                .insertInto(SEARCH_HISTORY)
                .set(SEARCH_HISTORY.USER_ID, userIdStr)
                .set(SEARCH_HISTORY.KEYWORD, keyword)
                .set(SEARCH_HISTORY.SEARCH_TYPE, searchType.name)
                .set(SEARCH_HISTORY.CREATED_AT, now)
                .set(SEARCH_HISTORY.CREATED_BY, userIdStr)
                .set(SEARCH_HISTORY.UPDATED_AT, now)
                .set(SEARCH_HISTORY.UPDATED_BY, userIdStr)
                .returningResult(SEARCH_HISTORY.ID)
        ).map { record ->
            SearchHistory(
                id = record.getValue(SEARCH_HISTORY.ID),
                userId = userId,
                keyword = keyword,
                searchType = searchType,
                createdAt = now,
                createdBy = userIdStr,
                updatedAt = now,
                updatedBy = userIdStr,
                deletedAt = null
            )
        }.doOnSuccess {
            logger.debug("Search history saved: id={}", it.id)
        }.doOnError { error ->
            logger.error("Failed to save search history: userId={}, keyword={}", userId, keyword, error)
        }
    }

    /**
     * 사용자의 최근 검색어 조회 (중복 제거)
     *
     * 동일한 키워드는 가장 최근 검색만 표시됩니다.
     * DISTINCT ON (keyword)를 사용하여 중복을 제거합니다.
     *
     * @param userId 사용자 ID
     * @param limit 최대 개수
     * @return 최근 검색어 목록 (최신순)
     */
    override fun findRecentByUserId(userId: UUID, limit: Int): Mono<List<SearchHistory>> {
        val userIdStr = userId.toString()

        logger.debug("Finding recent search history: userId={}, limit={}", userId, limit)

        // Subquery: 각 키워드별 최신 검색 기록 ID
        val latestIds = dslContext
            .select(
                SEARCH_HISTORY.ID.max().`as`("latest_id"),
                SEARCH_HISTORY.CREATED_AT.max().`as`("latest_created_at")
            )
            .from(SEARCH_HISTORY)
            .where(SEARCH_HISTORY.USER_ID.eq(userIdStr))
            .and(SEARCH_HISTORY.DELETED_AT.isNull)
            .groupBy(SEARCH_HISTORY.KEYWORD)
            .orderBy(SEARCH_HISTORY.CREATED_AT.max().desc())
            .limit(limit)

        return Flux.from(
            dslContext
                .select(
                    SEARCH_HISTORY.ID,
                    SEARCH_HISTORY.USER_ID,
                    SEARCH_HISTORY.KEYWORD,
                    SEARCH_HISTORY.SEARCH_TYPE,
                    SEARCH_HISTORY.CREATED_AT,
                    SEARCH_HISTORY.CREATED_BY,
                    SEARCH_HISTORY.UPDATED_AT,
                    SEARCH_HISTORY.UPDATED_BY,
                    SEARCH_HISTORY.DELETED_AT
                )
                .from(SEARCH_HISTORY)
                .where(SEARCH_HISTORY.ID.`in`(
                    dslContext.select(latestIds.field("latest_id", Long::class.java))
                        .from(latestIds)
                ))
                .orderBy(SEARCH_HISTORY.CREATED_AT.desc())
        ).map { record ->
            SearchHistory(
                id = record.getValue(SEARCH_HISTORY.ID),
                userId = UUID.fromString(record.getValue(SEARCH_HISTORY.USER_ID)),
                keyword = record.getValue(SEARCH_HISTORY.KEYWORD)!!,
                searchType = SearchType.valueOf(record.getValue(SEARCH_HISTORY.SEARCH_TYPE)!!),
                createdAt = record.getValue(SEARCH_HISTORY.CREATED_AT)!!,
                createdBy = record.getValue(SEARCH_HISTORY.CREATED_BY),
                updatedAt = record.getValue(SEARCH_HISTORY.UPDATED_AT)!!,
                updatedBy = record.getValue(SEARCH_HISTORY.UPDATED_BY),
                deletedAt = record.getValue(SEARCH_HISTORY.DELETED_AT)
            )
        }.collectList()
            .doOnSuccess { list ->
                logger.debug("Found {} recent search histories for userId={}", list.size, userId)
            }
    }

    /**
     * 특정 검색어 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @param keyword 검색 키워드
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    override fun deleteByUserIdAndKeyword(userId: UUID, keyword: String): Mono<Void> {
        val now = LocalDateTime.now()
        val userIdStr = userId.toString()

        logger.debug("Deleting search history: userId={}, keyword={}", userId, keyword)

        return Mono.from(
            dslContext
                .update(SEARCH_HISTORY)
                .set(SEARCH_HISTORY.DELETED_AT, now)
                .set(SEARCH_HISTORY.UPDATED_AT, now)
                .set(SEARCH_HISTORY.UPDATED_BY, userIdStr)
                .where(SEARCH_HISTORY.USER_ID.eq(userIdStr))
                .and(SEARCH_HISTORY.KEYWORD.eq(keyword))
                .and(SEARCH_HISTORY.DELETED_AT.isNull)
        ).then()
            .doOnSuccess {
                logger.debug("Search history deleted: userId={}, keyword={}", userId, keyword)
            }
    }

    /**
     * 전체 검색 기록 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @return 삭제 완료 시그널 (Mono<Void>)
     */
    override fun deleteAllByUserId(userId: UUID): Mono<Void> {
        val now = LocalDateTime.now()
        val userIdStr = userId.toString()

        logger.debug("Deleting all search history: userId={}", userId)

        return Mono.from(
            dslContext
                .update(SEARCH_HISTORY)
                .set(SEARCH_HISTORY.DELETED_AT, now)
                .set(SEARCH_HISTORY.UPDATED_AT, now)
                .set(SEARCH_HISTORY.UPDATED_BY, userIdStr)
                .where(SEARCH_HISTORY.USER_ID.eq(userIdStr))
                .and(SEARCH_HISTORY.DELETED_AT.isNull)
        ).then()
            .doOnSuccess {
                logger.debug("All search history deleted: userId={}", userId)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SearchHistoryRepositoryImpl::class.java)
    }
}
