package me.onetwo.upvy.domain.interaction.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 저장 엔티티
 *
 * 사용자가 콘텐츠를 저장한 상태를 나타냅니다.
 *
 * @property id 저장 ID
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class UserSave(
    val id: Long? = null,
    val userId: UUID,
    val contentId: UUID,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
