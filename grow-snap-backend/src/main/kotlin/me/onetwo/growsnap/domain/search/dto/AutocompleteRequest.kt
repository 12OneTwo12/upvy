package me.onetwo.growsnap.domain.search.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 검색 자동완성 요청 DTO
 *
 * 사용자가 입력 중인 키워드에 대한 자동완성 제안을 요청합니다.
 *
 * ## 자동완성 기능
 * - 최소 1글자부터 자동완성 제공
 * - 콘텐츠 제목, 태그, 크리에이터 이름에서 검색
 * - 빈도수(frequency) 기준 정렬
 * - 최근 검색어(last_used) 반영
 *
 * ## Validation
 * - q: 필수, 최소 1글자 이상
 * - limit: 최소 1, 최대 20 (기본값 10)
 *
 * @property q 입력 중인 키워드 (필수)
 * @property limit 자동완성 제안 개수 (기본값: 10, 최대: 20)
 */
data class AutocompleteRequest(
    @field:NotBlank(message = "검색 키워드는 필수입니다")
    @field:Size(min = 1, message = "검색 키워드는 최소 1글자 이상이어야 합니다")
    val q: String,

    @field:Min(value = 1, message = "제안 개수는 1 이상이어야 합니다")
    val limit: Int = 10
)
