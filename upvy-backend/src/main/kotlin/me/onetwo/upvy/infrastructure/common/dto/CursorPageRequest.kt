package me.onetwo.upvy.infrastructure.common.dto

/**
 * 커서 기반 페이지네이션 요청 DTO
 *
 * 무한 스크롤을 지원하는 커서 기반 페이지네이션 요청 정보를 담습니다.
 * 오프셋 기반 페이지네이션보다 성능이 우수하며, 실시간 데이터 추가에 안전합니다.
 *
 * @property cursor 이전 페이지의 마지막 항목 ID (null이면 첫 페이지)
 * @property limit 페이지당 항목 수 (기본값: 20, 최대: 100)
 */
data class CursorPageRequest(
    val cursor: String? = null,
    val limit: Int = DEFAULT_LIMIT
) {
    init {
        require(limit in MIN_LIMIT..MAX_LIMIT) {
            "Limit must be between $MIN_LIMIT and $MAX_LIMIT"
        }
    }

    companion object {
        /** 페이지당 최소 항목 수 */
        const val MIN_LIMIT = 1

        /** 페이지당 기본 항목 수 */
        const val DEFAULT_LIMIT = 20

        /** 페이지당 최대 항목 수 */
        const val MAX_LIMIT = 100
    }
}
