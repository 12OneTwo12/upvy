package me.onetwo.upvy.domain.block.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 차단 모델
 *
 * 사용자가 다른 사용자를 차단한 정보를 담고 있습니다.
 * 차단된 사용자의 콘텐츠는 피드, 검색 등 모든 조회에서 제외됩니다.
 *
 * @property id 차단 ID
 * @property blockerId 차단한 사용자 ID
 * @property blockedId 차단된 사용자 ID
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class UserBlock(
    val id: Long? = null,
    val blockerId: UUID,
    val blockedId: UUID,
    val createdAt: Instant? = null,
    val createdBy: String? = null,
    val updatedAt: Instant? = null,
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
