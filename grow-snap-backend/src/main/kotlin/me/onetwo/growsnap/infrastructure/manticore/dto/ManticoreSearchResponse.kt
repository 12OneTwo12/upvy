package me.onetwo.growsnap.infrastructure.manticore.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * Manticore Search HTTP API 응답 DTO
 *
 * Manticore Search HTTP API의 /search 엔드포인트 응답 형식입니다.
 *
 * ## API 응답 형식
 * ```json
 * {
 *   "hits": {
 *     "hits": [
 *       {
 *         "_id": "1",
 *         "_score": 1.5,
 *         "_source": {
 *           "content_id": "uuid",
 *           "title": "제목",
 *           "description": "설명"
 *         }
 *       }
 *     ],
 *     "total": 100
 *   }
 * }
 * ```
 *
 * @property hits 검색 결과
 */
data class ManticoreSearchResponse(
    @JsonProperty("hits")
    val hits: Hits
)

/**
 * 검색 결과 컨테이너
 *
 * @property hits 검색 결과 목록
 * @property total 전체 결과 개수
 */
data class Hits(
    @JsonProperty("hits")
    val hits: List<Hit>,

    @JsonProperty("total")
    val total: Int
)

/**
 * 개별 검색 결과
 *
 * @property id 문서 ID
 * @property score 검색 스코어
 * @property source 문서 데이터
 */
data class Hit(
    @JsonProperty("_id")
    val id: String,

    @JsonProperty("_score")
    val score: Double,

    @JsonProperty("_source")
    val source: Map<String, Any>
)

/**
 * 검색 결과 문서 (content_index)
 *
 * Manticore Search의 content_index에서 반환되는 문서 구조입니다.
 *
 * @property contentId 콘텐츠 ID (UUID)
 * @property title 제목
 * @property description 설명
 * @property tags 태그 목록
 * @property creatorName 크리에이터 이름
 * @property category 카테고리
 * @property difficulty 난이도
 * @property language 언어 코드
 * @property duration 비디오 길이 (초)
 * @property createdAt 생성 시각 (Unix timestamp)
 * @property likeCount 좋아요 수
 * @property viewCount 조회수
 * @property commentCount 댓글 수
 * @property popularityScore 인기 스코어
 */
data class ContentDocument(
    @JsonProperty("content_id")
    val contentId: UUID,

    @JsonProperty("title")
    val title: String,

    @JsonProperty("description")
    val description: String?,

    @JsonProperty("tags")
    val tags: String?,

    @JsonProperty("creator_name")
    val creatorName: String,

    @JsonProperty("category")
    val category: String,

    @JsonProperty("difficulty")
    val difficulty: String?,

    @JsonProperty("language")
    val language: String?,

    @JsonProperty("duration")
    val duration: Int?,

    @JsonProperty("created_at")
    val createdAt: Long,

    @JsonProperty("like_count")
    val likeCount: Int,

    @JsonProperty("view_count")
    val viewCount: Int,

    @JsonProperty("comment_count")
    val commentCount: Int,

    @JsonProperty("popularity_score")
    val popularityScore: Double
)
