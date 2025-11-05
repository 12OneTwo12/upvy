package me.onetwo.growsnap.domain.search.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 사용자 검색 요청 DTO
 *
 * 인스타그램 스타일의 사용자 검색 요청 파라미터입니다.
 * 닉네임, 이름, 프로필 설명에서 검색합니다.
 *
 * ## 검색 대상
 * - 닉네임 (nickname)
 * - 프로필 설명 (bio)
 *
 * ## 정렬 기준
 * - 팔로워 수 (follower_count) 내림차순
 * - 관련도 (relevance) 내림차순
 *
 * ## Validation
 * - q: 필수, 최소 2글자 이상
 * - limit: 최소 1, 최대 100 (기본값 20)
 *
 * @property q 검색 키워드 (필수)
 * @property cursor 페이지네이션 커서 (선택)
 * @property limit 페이지 크기 (기본값: 20, 최대: 100)
 */
data class UserSearchRequest(
    @field:NotBlank(message = "검색 키워드는 필수입니다")
    @field:Size(min = 2, message = "검색 키워드는 최소 2글자 이상이어야 합니다")
    val q: String,

    val cursor: String? = null,

    @field:Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
    val limit: Int = 20
)
