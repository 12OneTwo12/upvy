package me.onetwo.upvy.infrastructure.common.dto

/**
 * 커서 기반 페이지네이션 응답 DTO
 *
 * 커서 기반 페이지네이션 결과를 담는 공통 응답 DTO입니다.
 *
 * @param T 페이지 항목 타입
 * @property content 현재 페이지의 데이터 목록
 * @property nextCursor 다음 페이지를 요청할 때 사용할 커서 (null이면 마지막 페이지)
 * @property hasNext 다음 페이지 존재 여부
 * @property count 현재 페이지의 항목 수
 */
data class CursorPageResponse<T>(
    val content: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean,
    val count: Int
) {
    companion object {
        /**
         * 빈 페이지 응답 생성
         */
        fun <T> empty(): CursorPageResponse<T> {
            return CursorPageResponse(
                content = emptyList(),
                nextCursor = null,
                hasNext = false,
                count = 0
            )
        }

        /**
         * 페이지 응답 생성
         *
         * @param content 데이터 목록
         * @param limit 요청한 페이지 크기
         * @param getCursor 항목에서 커서 값을 추출하는 함수
         */
        fun <T> of(
            content: List<T>,
            limit: Int,
            getCursor: (T) -> String
        ): CursorPageResponse<T> {
            val hasNext = content.size > limit
            val resultContent = if (hasNext) content.dropLast(1) else content
            val nextCursor = if (hasNext) getCursor(resultContent.last()) else null

            return CursorPageResponse(
                content = resultContent,
                nextCursor = nextCursor,
                hasNext = hasNext,
                count = resultContent.size
            )
        }
    }
}
