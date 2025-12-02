package me.onetwo.growsnap.domain.block.model

import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠 차단 모델
 *
 * 사용자가 특정 콘텐츠를 차단한 정보를 담고 있습니다.
 * 차단된 콘텐츠는 피드에서 제외됩니다.
 *
 * @property id 차단 ID
 * @property userId 차단한 사용자 ID
 * @property contentId 차단된 콘텐츠 ID
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class ContentBlock(
    val id: Long? = null,
    val userId: UUID,
    val contentId: UUID,
    val createdAt: Instant? = null,
    val createdBy: String? = null,
    val updatedAt: Instant? = null,
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
