package me.onetwo.growsnap.domain.content.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 자막 엔티티
 *
 * 콘텐츠의 다국어 자막 정보를 관리합니다.
 *
 * @property id 자막 고유 식별자
 * @property contentId 콘텐츠 ID
 * @property language 자막 언어 코드 (ISO 639-1, 예: ko, en)
 * @property subtitleUrl 자막 파일 URL (VTT 형식)
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class ContentSubtitle(
    val id: Long? = null,
    val contentId: UUID,
    val language: String,
    val subtitleUrl: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: String? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: String? = null,
    val deletedAt: LocalDateTime? = null
)
