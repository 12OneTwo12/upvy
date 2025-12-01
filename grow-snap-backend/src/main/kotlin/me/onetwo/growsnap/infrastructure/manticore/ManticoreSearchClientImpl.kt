package me.onetwo.growsnap.infrastructure.manticore

import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchRequest
import me.onetwo.growsnap.infrastructure.manticore.dto.ManticoreSearchResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Manticore Search 클라이언트 구현체
 *
 * WebClient를 사용하여 Manticore Search HTTP API와 통신합니다.
 *
 * ## HTTP API 엔드포인트
 * - POST /search: Full-text search
 * - POST /insert: Document insert
 * - POST /update: Document update
 * - POST /delete: Document delete
 * - POST /replace: Document replace (upsert)
 *
 * ## 에러 처리
 * - **search()**: 에러를 상위 레이어로 전파 (SearchRepositoryImpl에서 failover 처리)
 * - **insert/update/delete/replace**: 에러 발생 시 로그 출력 후 false 반환
 *
 * @property webClient Manticore Search WebClient
 */
@Component
class ManticoreSearchClientImpl(
    private val webClient: WebClient
) : ManticoreSearchClient {

    /**
     * 전체 검색
     *
     * ## 에러 처리
     * - 에러 발생 시 로그를 남기고 에러를 전파합니다.
     * - 상위 레이어(SearchRepositoryImpl)에서 failover 처리를 담당합니다.
     *
     * @param request 검색 요청
     * @return 검색 결과
     */
    override fun search(request: ManticoreSearchRequest): Mono<ManticoreSearchResponse> {
        logger.debug(
            "Searching Manticore: index={}, query={}, limit={}",
            request.index,
            request.query,
            request.limit
        )

        return webClient
            .post()
            .uri("/search")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ManticoreSearchResponse::class.java)
            .doOnSuccess { response ->
                logger.debug(
                    "Manticore search completed: total={}, hits={}",
                    response.hits.total,
                    response.hits.hits.size
                )
            }
            .doOnError { error ->
                logger.error("Manticore search failed: index={}", request.index, error)
            }
            // 에러를 상위 레이어로 전파하여 failover 처리가 작동하도록 함
    }

    /**
     * 문서 삽입
     *
     * @param index 인덱스 이름
     * @param document 문서 데이터
     * @return 삽입 성공 여부
     */
    override fun insert(index: String, document: Map<String, Any>): Mono<Boolean> {
        logger.debug("Inserting document to Manticore: index={}", index)

        val request = mapOf(
            "index" to index,
            "doc" to document
        )

        return webClient
            .post()
            .uri("/insert")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                val created = response["created"] as? Int ?: 0
                created > 0
            }
            .doOnSuccess { success ->
                logger.debug("Manticore insert completed: success={}", success)
            }
            .doOnError { error ->
                logger.error("Manticore insert failed: index={}", index, error)
            }
            .onErrorReturn(false)
    }

    /**
     * 문서 대체 (Upsert)
     *
     * @param index 인덱스 이름
     * @param document 문서 데이터
     * @return 대체 성공 여부
     */
    override fun replace(index: String, document: Map<String, Any>): Mono<Boolean> {
        logger.debug("Replacing document in Manticore: index={}", index)

        val request = mapOf(
            "index" to index,
            "doc" to document
        )

        return webClient
            .post()
            .uri("/replace")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                val created = (response["created"] as? Int ?: 0) > 0
                val updated = (response["updated"] as? Int ?: 0) > 0
                created || updated
            }
            .doOnSuccess { success ->
                logger.debug("Manticore replace completed: success={}", success)
            }
            .doOnError { error ->
                logger.error("Manticore replace failed: index={}", index, error)
            }
            .onErrorReturn(false)
    }

    /**
     * 문서 삭제
     *
     * @param index 인덱스 이름
     * @param id 문서 ID
     * @return 삭제 성공 여부
     */
    override fun delete(index: String, id: String): Mono<Boolean> {
        logger.debug("Deleting document from Manticore: index={}, id={}", index, id)

        val request = mapOf(
            "index" to index,
            "id" to id
        )

        return webClient
            .post()
            .uri("/delete")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map::class.java)
            .map { response ->
                val deleted = response["deleted"] as? Int ?: 0
                deleted > 0
            }
            .doOnSuccess { success ->
                logger.debug("Manticore delete completed: success={}", success)
            }
            .doOnError { error ->
                logger.error("Manticore delete failed: index={}, id={}", index, id, error)
            }
            .onErrorReturn(false)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ManticoreSearchClientImpl::class.java)
    }
}
