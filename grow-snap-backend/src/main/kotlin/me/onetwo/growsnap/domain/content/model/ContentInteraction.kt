package me.onetwo.growsnap.domain.content.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 인터랙션 엔티티
 *
 * 콘텐츠에 대한 사용자 인터랙션 통계를 관리합니다.
 * (좋아요, 댓글, 저장, 공유, 조회수)
 *
 * @property id 인터랙션 고유 식별자
 * @property contentId 콘텐츠 ID
 * @property likeCount 좋아요 수
 * @property commentCount 댓글 수
 * @property saveCount 저장 수
 * @property shareCount 공유 수
 * @property viewCount 조회수
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class ContentInteraction(
    val id: Long? = null,
    val contentId: UUID,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val saveCount: Int = 0,
    val shareCount: Int = 0,
    val viewCount: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: String? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: String? = null,
    val deletedAt: LocalDateTime? = null
)
