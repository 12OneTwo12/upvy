package me.onetwo.growsnap.domain.content.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 엔티티
 *
 * 플랫폼의 핵심 콘텐츠(비디오/사진)를 나타냅니다.
 * PHOTO 타입의 경우 여러 사진은 ContentPhoto 테이블에서 별도 관리합니다.
 *
 * @property id 콘텐츠 고유 식별자
 * @property creatorId 콘텐츠 제작자 ID
 * @property contentType 콘텐츠 타입 (VIDEO, PHOTO)
 * @property url 콘텐츠 파일 URL (S3) - VIDEO: 비디오 URL, PHOTO: 대표 사진 URL
 * @property thumbnailUrl 썸네일 이미지 URL
 * @property duration 비디오 길이 (초 단위, 사진인 경우 null)
 * @property width 콘텐츠 가로 크기 (픽셀)
 * @property height 콘텐츠 세로 크기 (픽셀)
 * @property status 콘텐츠 상태
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class Content(
    val id: UUID? = null,
    val creatorId: UUID,
    val contentType: ContentType,
    val url: String,
    val thumbnailUrl: String,
    val duration: Int? = null,
    val width: Int,
    val height: Int,
    val status: ContentStatus = ContentStatus.PENDING,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: UUID? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: UUID? = null,
    val deletedAt: LocalDateTime? = null
)
