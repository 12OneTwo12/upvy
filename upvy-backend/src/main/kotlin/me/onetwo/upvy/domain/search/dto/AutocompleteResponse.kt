package me.onetwo.upvy.domain.search.dto

/**
 * 검색 자동완성 응답 DTO
 *
 * 자동완성 제안 목록을 포함합니다.
 *
 * @property suggestions 자동완성 제안 목록
 */
data class AutocompleteResponse(
    val suggestions: List<AutocompleteSuggestion>
) {
    companion object {
        /**
         * 빈 응답 생성
         */
        fun empty(): AutocompleteResponse {
            return AutocompleteResponse(emptyList())
        }
    }
}

/**
 * 자동완성 제안 항목
 *
 * ## 제안 타입
 * - CONTENT: 콘텐츠 제목
 * - TAG: 태그
 * - USER: 사용자 닉네임
 *
 * @property text 제안 텍스트
 * @property type 제안 타입 (CONTENT, TAG, USER)
 * @property highlightedText 매칭된 부분 강조 텍스트 (HTML 태그 포함)
 * @property userId 사용자 ID (USER 타입일 때만 사용, Manticore에서 반환)
 * @property profileImageUrl 프로필 이미지 URL (USER 타입일 때만 사용, Service에서 조회하여 추가)
 */
data class AutocompleteSuggestion(
    val text: String,
    val type: SuggestionType,
    val highlightedText: String,
    val userId: String? = null,
    val profileImageUrl: String? = null
)

/**
 * 자동완성 제안 타입
 *
 * ## 타입 설명
 * - CONTENT: 콘텐츠 제목에서 매칭
 * - TAG: 태그에서 매칭
 * - USER: 사용자 닉네임에서 매칭
 */
enum class SuggestionType {
    /**
     * 콘텐츠 제목
     */
    CONTENT,

    /**
     * 태그
     */
    TAG,

    /**
     * 사용자
     */
    USER
}
