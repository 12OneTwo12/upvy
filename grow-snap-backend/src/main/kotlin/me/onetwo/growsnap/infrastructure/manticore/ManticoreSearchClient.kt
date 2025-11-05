package me.onetwo.growsnap.infrastructure.manticore

import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchRequest
import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchResponse
import reactor.core.publisher.Mono

/**
 * Manticore Search 클라이언트 인터페이스
 *
 * Manticore Search HTTP API와 통신하는 클라이언트 인터페이스입니다.
 *
 * ## Manticore Search HTTP API
 * - POST /search: Full-text search
 * - POST /insert: Document insert
 * - POST /update: Document update
 * - POST /delete: Document delete
 * - POST /replace: Document replace (upsert)
 *
 * ## 구현체
 * - ManticoreSearchClientImpl: WebClient 기반 구현체
 *
 * ## 사용 예시
 * ```kotlin
 * val request = ManticoreSearchRequest(
 *     index = "content_index",
 *     query = mapOf("match" to mapOf("*" to "검색어")),
 *     limit = 20
 * )
 * manticoreSearchClient.search(request)
 *     .subscribe { response ->
 *         println("Total: ${response.hits.total}")
 *     }
 * ```
 */
interface ManticoreSearchClient {

    /**
     * 전체 검색
     *
     * Manticore Search에서 콘텐츠를 검색합니다.
     *
     * @param request 검색 요청
     * @return 검색 결과
     */
    fun search(request: ManticoreSearchRequest): Mono<ManticoreSearchResponse>

    /**
     * 문서 삽입
     *
     * Manticore Search 인덱스에 문서를 삽입합니다.
     *
     * @param index 인덱스 이름
     * @param document 문서 데이터
     * @return 삽입 성공 여부
     */
    fun insert(index: String, document: Map<String, Any>): Mono<Boolean>

    /**
     * 문서 대체 (Upsert)
     *
     * Manticore Search 인덱스에 문서를 대체합니다.
     * 문서가 존재하면 업데이트, 없으면 삽입합니다.
     *
     * @param index 인덱스 이름
     * @param document 문서 데이터
     * @return 대체 성공 여부
     */
    fun replace(index: String, document: Map<String, Any>): Mono<Boolean>

    /**
     * 문서 삭제
     *
     * Manticore Search 인덱스에서 문서를 삭제합니다.
     *
     * @param index 인덱스 이름
     * @param id 문서 ID
     * @return 삭제 성공 여부
     */
    fun delete(index: String, id: String): Mono<Boolean>
}
