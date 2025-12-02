package me.onetwo.growsnap.domain.search.repository

import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.search.dto.AutocompleteSuggestion
import me.onetwo.growsnap.domain.search.dto.SuggestionType
import me.onetwo.growsnap.domain.search.model.SearchSortType
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.manticore.ManticoreSearchClient
import me.onetwo.growsnap.infrastructure.manticore.ManticoreSearchProperties
import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * SearchRepository 구현체
 *
 * Manticore Search를 사용한 검색 기능을 제공하며, 장애 시 DB Fallback을 지원합니다.
 *
 * ## 검색 전략 (Failover 적용)
 * - **1차**: Manticore Search 사용 (content_index, user_index, autocomplete_index)
 * - **2차**: Manticore Search 실패 시 DB LIKE 검색으로 Fallback
 *
 * ## Failover 이점
 * - Manticore Search 미구성 시에도 검색 기능 작동
 * - 운영 환경에서 Manticore Search 장애 시 서비스 지속성 보장
 *
 * @property manticoreSearchClient Manticore Search 클라이언트
 * @property properties Manticore Search 설정
 * @property contentRepository 콘텐츠 Repository (Fallback용)
 * @property userProfileRepository 사용자 프로필 Repository (Fallback용)
 */
@Repository
class SearchRepositoryImpl(
    private val manticoreSearchClient: ManticoreSearchClient,
    private val properties: ManticoreSearchProperties,
    private val contentRepository: ContentRepository,
    private val userProfileRepository: UserProfileRepository
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
        startDate: Instant?,
        endDate: Instant?,
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

        // 필터 구성 (bool query with must clauses)
        val mustClauses = mutableListOf<Map<String, Any>>()

        category?.let {
            mustClauses.add(mapOf("equals" to mapOf("category" to it.name)))
        }
        difficulty?.let {
            mustClauses.add(mapOf("equals" to mapOf("difficulty" to it.name)))
        }

        if (minDuration != null || maxDuration != null) {
            val rangeFilter = mutableMapOf<String, Int>()
            minDuration?.let { rangeFilter["gte"] = it }
            maxDuration?.let { rangeFilter["lte"] = it }
            mustClauses.add(mapOf("range" to mapOf("duration" to rangeFilter)))
        }

        language?.let {
            mustClauses.add(mapOf("equals" to mapOf("language" to it)))
        }

        // 소프트 삭제 필터
        mustClauses.add(mapOf("equals" to mapOf("is_deleted" to false)))

        // Bool query로 모든 필터 조합
        val filters = if (mustClauses.isNotEmpty()) {
            mapOf("bool" to mapOf("must" to mustClauses))
        } else {
            null
        }

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
            filter = filters,
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
                logger.debug("Content search completed via Manticore: count={}", contentIds.size)
            }
            .doOnError { error ->
                logger.warn("Manticore search failed, falling back to DB: query={}", query, error)
            }
            .onErrorResume { error ->
                logger.info("Using DB fallback for content search: query={}", query)
                // Failover: DB LIKE 검색
                contentRepository.searchByTitle(query, limit + 1)
                    .collectList()
                    .doOnSuccess { contentIds ->
                        logger.debug("Content search completed via DB fallback: count={}", contentIds.size)
                    }
            }
    }

    /**
     * 사용자 검색
     *
     * Manticore Search user_index에서 닉네임으로 검색합니다.
     *
     * **Note**: 차단된 사용자 필터링은 Service 계층에서 처리됩니다.
     * Manticore Search 결과는 Repository에서 직접 필터링할 수 없으므로,
     * SearchService에서 결과를 받은 후 차단 목록과 비교하여 필터링합니다.
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
                logger.debug("User search completed via Manticore: count={}", userIds.size)
            }
            .doOnError { error ->
                logger.warn("Manticore search failed, falling back to DB: query={}", query, error)
            }
            .onErrorResume { error ->
                logger.info("Using DB fallback for user search: query={}", query)
                // Failover: DB LIKE 검색
                userProfileRepository.searchByNickname(query, limit + 1)
                    .collectList()
                    .doOnSuccess { userIds ->
                        logger.debug("User search completed via DB fallback: count={}", userIds.size)
                    }
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
                logger.debug("Autocomplete completed via Manticore: count={}", suggestions.size)
            }
            .doOnError { error ->
                logger.warn("Manticore autocomplete failed, returning empty list: query={}", query, error)
            }
            .onErrorResume { error ->
                logger.info("Autocomplete fallback: returning empty list for query={}", query)
                // Failover: 자동완성은 Manticore 없이는 구현 복잡하므로 빈 리스트 반환
                Mono.just(emptyList())
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
