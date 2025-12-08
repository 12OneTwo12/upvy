package me.onetwo.upvy.domain.interaction.model

import java.time.Instant
import java.util.UUID

/**
 * 댓글 좋아요 엔티티
 *
 * 사용자가 댓글에 좋아요를 누른 상태를 나타냅니다.
 *
 * @property id 좋아요 ID
 * @property userId 사용자 ID
 * @property commentId 댓글 ID
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class CommentLike(
    val id: Long? = null,
    val userId: UUID,
    val commentId: UUID,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
