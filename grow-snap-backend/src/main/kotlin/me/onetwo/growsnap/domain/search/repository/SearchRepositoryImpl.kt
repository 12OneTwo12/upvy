package me.onetwo.growsnap.domain.search.repository

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.domain.search.dto.AutocompleteSuggestion
import me.onetwo.growsnap.domain.search.dto.SuggestionType
import me.onetwo.growsnap.domain.search.model.SearchSortType
import me.onetwo.growsnap.infrastructure.manticore.ManticoreSearchClient
import me.onetwo.growsnap.infrastructure.manticore.ManticoreSearchProperties
import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchRequest
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.UUID

/**
 * SearchRepository 구현체
 *
 * Manticore Search를 사용한 검색 기능을 제공합니다.
 *
 * ## 검색 전략
 * - **콘텐츠 검색**: Manticore Search content_index 사용
 * - **사용자 검색**: Manticore Search user_index 사용
 * - **자동완성**: Manticore Search autocomplete_index 사용
 *
 * @property manticoreSearchClient Manticore Search 클라이언트
 * @property dslContext JOOQ DSL Context
 * @property properties Manticore Search 설정
 */
@Repository
class SearchRepositoryImpl(
    private val manticoreSearchClient: ManticoreSearchClient,
    private val dslContext: DSLContext,
    private val properties: ManticoreSearchProperties
) : SearchRepository {

    /**
     * 콘텐츠 검색
     *
     * @param query 검색 키워드
     * @param category 카테고리 필터
     * @param difficulty 난이도 필터
     * @param minDuration 최소 길이
     * @param maxDuration 최대 길이
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @param language 언어 코드
     * @param sortBy 정렬 기준
     * @param cursor 페이지네이션 커서
     * @param limit 페이지 크기
     * @return 콘텐츠 ID 목록
     */
    override fun searchContents(
        query: String,
        category: Category?,
        difficulty: DifficultyLevel?,
        minDuration: Int?,
        maxDuration: Int?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        language: String?,
        sortBy: SearchSortType,
        cursor: String?,
        limit: Int
    ): Mono<List<UUID>> {
        logger.debug("Searching contents: query={}, category={}, sortBy={}", query, category, sortBy)

        // Manticore Search 쿼리 구성
        val matchQuery = mapOf(
            "match" to mapOf("*" to query)
        )

        // 필터 구성
        val filters = mutableMapOf<String, Any>()
        category?.let {
            filters["equals"] = mapOf("category" to it.name)
        }
        difficulty?.let {
            filters["equals"] = mapOf("difficulty" to it.name)
        }

        if (minDuration != null || maxDuration != null) {
            val rangeFilter = mutableMapOf<String, Int>()
            minDuration?.let { rangeFilter["gte"] = it }
            maxDuration?.let { rangeFilter["lte"] = it }
            filters["range"] = mapOf("duration" to rangeFilter)
        }

        language?.let {
            filters["equals"] = mapOf("language" to it)
        }

        // 소프트 삭제 필터
        filters["equals"] = mapOf("is_deleted" to false)

        // 정렬 구성
        val sort = when (sortBy) {
            SearchSortType.RELEVANCE -> listOf(
                mapOf("_score" to mapOf("order" to "desc")),
                mapOf("created_at" to mapOf("order" to "desc"))
            )
            SearchSortType.RECENT -> listOf(
                mapOf("created_at" to mapOf("order" to "desc"))
            )
            SearchSortType.POPULAR -> listOf(
                mapOf("popularity_score" to mapOf("order" to "desc")),
                mapOf("created_at" to mapOf("order" to "desc"))
            )
        }

        val request = ManticoreSearchRequest(
            index = properties.index.content,
            query = matchQuery,
            filter = filters.takeIf { it.isNotEmpty() },
            sort = sort,
            limit = limit + 1,  // hasNext 확인을 위해 +1
            offset = 0
        )

        return manticoreSearchClient.search(request)
            .map { response ->
                response.hits.hits.mapNotNull { hit ->
                    val contentIdStr = hit.source["content_id"] as? String
                    contentIdStr?.let { UUID.fromString(it) }
                }
            }
            .doOnSuccess { contentIds ->
                logger.debug("Content search completed: count={}", contentIds.size)
            }
            .doOnError { error ->
                logger.error("Content search failed: query={}", query, error)
            }
    }

    /**
     * 사용자 검색
     *
     * Manticore Search user_index에서 닉네임으로 검색합니다.
     *
     * @param query 검색 키워드
     * @param cursor 페이지네이션 커서
     * @param limit 페이지 크기
     * @return 사용자 ID 목록
     */
    override fun searchUsers(
        query: String,
        cursor: String?,
        limit: Int
    ): Mono<List<UUID>> {
        logger.debug("Searching users: query={}", query)

        val matchQuery = mapOf(
            "match" to mapOf("nickname" to query)
        )

        val request = ManticoreSearchRequest(
            index = properties.index.user,
            query = matchQuery,
            sort = listOf(
                mapOf("created_at" to mapOf("order" to "desc"))
            ),
            limit = limit + 1,  // hasNext 확인을 위해 +1
            offset = 0
        )

        return manticoreSearchClient.search(request)
            .map { response ->
                response.hits.hits.mapNotNull { hit ->
                    val userIdStr = hit.source["user_id"] as? String
                    userIdStr?.let { UUID.fromString(it) }
                }
            }
            .doOnSuccess { userIds ->
                logger.debug("User search completed: count={}", userIds.size)
            }
            .doOnError { error ->
                logger.error("User search failed: query={}", query, error)
            }
    }

    /**
     * 자동완성
     *
     * Manticore Search autocomplete_index에서 자동완성 제안을 조회합니다.
     *
     * @param query 입력 중인 키워드
     * @param limit 제안 개수
     * @return 자동완성 제안 목록
     */
    override fun autocomplete(
        query: String,
        limit: Int
    ): Mono<List<AutocompleteSuggestion>> {
        logger.debug("Autocomplete: query={}", query)

        val matchQuery = mapOf(
            "match" to mapOf("text" to query)
        )

        val request = ManticoreSearchRequest(
            index = properties.index.autocomplete,
            query = matchQuery,
            limit = limit
        )

        return manticoreSearchClient.search(request)
            .map { response ->
                response.hits.hits.mapNotNull { hit ->
                    val text = hit.source["text"] as? String
                    val type = hit.source["type"] as? String

                    if (text != null && type != null) {
                        AutocompleteSuggestion(
                            text = text,
                            type = SuggestionType.valueOf(type),
                            highlightedText = highlightText(text, query)
                        )
                    } else {
                        null
                    }
                }
            }
            .doOnSuccess { suggestions ->
                logger.debug("Autocomplete completed: count={}", suggestions.size)
            }
            .doOnError { error ->
                logger.error("Autocomplete failed: query={}", query, error)
            }
    }

    /**
     * 검색어 하이라이팅
     *
     * 검색어와 매칭된 부분을 <em> 태그로 감싸서 강조합니다.
     *
     * @param text 원본 텍스트
     * @param query 검색 키워드
     * @return 하이라이팅된 텍스트
     */
    private fun highlightText(text: String, query: String): String {
        val regex = Regex(query, RegexOption.IGNORE_CASE)
        return text.replace(regex) { matchResult ->
            "<em>${matchResult.value}</em>"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SearchRepositoryImpl::class.java)
    }
}
