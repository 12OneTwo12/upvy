package me.onetwo.growsnap.domain.content.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 사진 엔티티
 *
 * PHOTO 타입 콘텐츠의 사진 목록을 관리합니다 (인스타그램 스타일 갤러리).
 *
 * @property id 사진 고유 식별자
 * @property contentId 콘텐츠 ID
 * @property photoUrl 사진 URL (S3)
 * @property displayOrder 표시 순서 (0부터 시작)
 * @property width 사진 가로 크기 (픽셀)
 * @property height 사진 세로 크기 (픽셀)
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class ContentPhoto(
    val id: Long? = null,
    val contentId: UUID,
    val photoUrl: String,
    val displayOrder: Int,
    val width: Int,
    val height: Int,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: UUID? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: UUID? = null,
    val deletedAt: LocalDateTime? = null
)
