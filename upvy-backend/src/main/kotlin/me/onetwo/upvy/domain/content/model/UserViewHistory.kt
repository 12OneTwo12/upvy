package me.onetwo.upvy.domain.content.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 시청 기록 엔티티
 *
 * 사용자의 콘텐츠 시청 이력을 관리합니다.
 * 추천 알고리즘 및 중복 콘텐츠 방지에 사용됩니다.
 *
 * @property id 시청 기록 고유 식별자
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 * @property watchedAt 시청 시각 (UTC Instant)
 * @property completionRate 시청 완료율 (0-100)
 * @property createdAt 생성 시각 (UTC Instant)
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각 (UTC Instant)
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (UTC Instant, Soft Delete)
 */
data class UserViewHistory(
    val id: Long? = null,
    val userId: UUID,
    val contentId: UUID,
    val watchedAt: Instant = Instant.now(),
    val completionRate: Int = 0,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
