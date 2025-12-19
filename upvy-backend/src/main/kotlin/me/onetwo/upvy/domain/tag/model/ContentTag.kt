package me.onetwo.upvy.domain.tag.model

import java.time.Instant
import java.util.UUID

/**
 * 콘텐츠-태그 관계 엔티티
 *
 * Content와 Tag 간의 다대다(M:N) 관계를 나타냅니다.
 * 하나의 콘텐츠는 여러 태그를 가질 수 있고, 하나의 태그는 여러 콘텐츠에 사용될 수 있습니다.
 *
 * @property id 관계 고유 식별자 (Auto Increment)
 * @property contentId 콘텐츠 ID (CHAR(36) UUID)
 * @property tagId 태그 ID (BIGINT)
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class ContentTag(
    val id: Long? = null,
    val contentId: UUID,
    val tagId: Long,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
