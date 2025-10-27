package me.onetwo.growsnap.domain.content.model

import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 메타데이터 엔티티
 *
 * 콘텐츠의 제목, 설명, 카테고리 등 메타 정보를 관리합니다.
 *
 * @property id 메타데이터 고유 식별자
 * @property contentId 콘텐츠 ID
 * @property title 콘텐츠 제목
 * @property description 콘텐츠 설명
 * @property category 카테고리
 * @property tags 태그 목록
 * @property language 언어 코드 (ISO 639-1, 예: ko, en)
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class ContentMetadata(
    val id: Long? = null,
    val contentId: UUID,
    val title: String,
    val description: String? = null,
    val category: Category,
    val tags: List<String> = emptyList(),
    val language: String = "ko",
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: UUID? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: UUID? = null,
    val deletedAt: LocalDateTime? = null
)
