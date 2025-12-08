package me.onetwo.upvy.infrastructure.manticore.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Manticore Search HTTP API 요청 DTO
 *
 * Manticore Search HTTP API의 /search 엔드포인트 요청 형식입니다.
 *
 * ## API 형식
 * ```json
 * {
 *   "index": "content_index",
 *   "query": {
 *     "match": {"*": "검색어"}
 *   },
 *   "filter": {
 *     "range": {"duration": {"gte": 0, "lte": 100}}
 *   },
 *   "sort": [
 *     {"_score": {"order": "desc"}},
 *     {"created_at": {"order": "desc"}}
 *   ],
 *   "limit": 20,
 *   "offset": 0
 * }
 * ```
 *
 * @property index 인덱스 이름
 * @property query 검색 쿼리
 * @property filter 필터 조건
 * @property sort 정렬 조건
 * @property limit 결과 개수 제한
 * @property offset 결과 오프셋
 * @property highlight 하이라이팅 설정
 */
data class ManticoreSearchRequest(
    @JsonProperty("index")
    val index: String,

    @JsonProperty("query")
    val query: Map<String, Any>,

    @JsonProperty("filter")
    val filter: Map<String, Any>? = null,

    @JsonProperty("sort")
    val sort: List<Map<String, Any>>? = null,

    @JsonProperty("limit")
    val limit: Int = 20,

    @JsonProperty("offset")
    val offset: Int = 0,

    @JsonProperty("highlight")
    val highlight: Map<String, Any>? = null
)
