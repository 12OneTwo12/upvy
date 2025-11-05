package me.onetwo.growsnap.domain.search.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import me.onetwo.growsnap.domain.search.model.SearchSortType
import java.time.LocalDate

/**
 * 콘텐츠 검색 요청 DTO
 *
 * 인스타그램 스타일의 콘텐츠 검색 요청 파라미터입니다.
 *
 * ## 검색 대상
 * - 제목 (title)
 * - 설명 (description)
 * - 태그 (tags)
 * - 크리에이터 이름 (creator_name)
 *
 * ## 필터링
 * - 카테고리, 난이도, 길이, 날짜, 언어
 *
 * ## 정렬
 * - RELEVANCE: BM25 검색 스코어 (기본값)
 * - RECENT: 최신순
 * - POPULAR: 인기순
 *
 * ## Validation
 * - q: 필수, 최소 2글자 이상
 * - limit: 최소 1, 최대 100 (기본값 20)
 * - minDuration, maxDuration: 최소 0
 *
 * @property q 검색 키워드 (필수)
 * @property category 카테고리 필터 (선택)
 * @property difficulty 난이도 필터 (선택)
 * @property minDuration 최소 길이 (초, 선택)
 * @property maxDuration 최대 길이 (초, 선택)
 * @property startDate 시작 날짜 (선택)
 * @property endDate 종료 날짜 (선택)
 * @property language 언어 코드 (선택, 예: "ko", "en")
 * @property sortBy 정렬 기준 (기본값: RELEVANCE)
 * @property cursor 페이지네이션 커서 (선택)
 * @property limit 페이지 크기 (기본값: 20, 최대: 100)
 */
data class ContentSearchRequest(
    @field:NotBlank(message = "검색 키워드는 필수입니다")
    @field:Size(min = 2, message = "검색 키워드는 최소 2글자 이상이어야 합니다")
    val q: String,

    val category: Category? = null,

    val difficulty: DifficultyLevel? = null,

    @field:Min(value = 0, message = "최소 길이는 0 이상이어야 합니다")
    val minDuration: Int? = null,

    @field:Min(value = 0, message = "최대 길이는 0 이상이어야 합니다")
    val maxDuration: Int? = null,

    val startDate: LocalDate? = null,

    val endDate: LocalDate? = null,

    @field:Size(min = 2, max = 2, message = "언어 코드는 2글자여야 합니다")
    val language: String? = null,

    val sortBy: SearchSortType = SearchSortType.RELEVANCE,

    val cursor: String? = null,

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    val limit: Int = 20
)
