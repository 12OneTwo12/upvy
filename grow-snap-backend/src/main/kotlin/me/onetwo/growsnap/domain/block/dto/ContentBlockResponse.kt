package me.onetwo.growsnap.domain.block.dto

import me.onetwo.growsnap.domain.block.model.ContentBlock
import java.time.Instant

/**
 * 콘텐츠 차단 응답 DTO
 *
 * 콘텐츠 차단 완료 후 반환되는 응답 데이터입니다.
 *
 * @property id 차단 ID
 * @property userId 차단한 사용자 ID
 * @property contentId 차단된 콘텐츠 ID
 * @property createdAt 차단 시각
 */
data class ContentBlockResponse(
    val id: Long,
    val userId: String,
    val contentId: String,
    val createdAt: Instant
) {
    companion object {
        /**
         * ContentBlock 모델을 ContentBlockResponse DTO로 변환합니다.
         *
         * @param contentBlock 콘텐츠 차단 모델
         * @return 콘텐츠 차단 응답 DTO
         * @throws IllegalArgumentException id 또는 createdAt이 null인 경우
         */
        fun from(contentBlock: ContentBlock): ContentBlockResponse {
            return ContentBlockResponse(
                id = requireNotNull(contentBlock.id) { "차단 ID는 null일 수 없습니다" },
                userId = contentBlock.userId.toString(),
                contentId = contentBlock.contentId.toString(),
                createdAt = requireNotNull(contentBlock.createdAt) { "차단 생성 시각은 null일 수 없습니다" }
            )
        }
    }
}
