package me.onetwo.growsnap.domain.interaction.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 사용자 좋아요 엔티티
 *
 * 사용자가 콘텐츠에 좋아요를 누른 상태를 나타냅니다.
 *
 * @property id 좋아요 ID
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class UserLike(
    val id: Long? = null,
    val userId: UUID,
    val contentId: UUID,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: String? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: String? = null,
    val deletedAt: LocalDateTime? = null
)
