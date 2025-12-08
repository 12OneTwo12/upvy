package me.onetwo.upvy.domain.content.dto

import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentMetadata
import java.util.UUID

/**
 * 콘텐츠 생성 결과를 담는 내부 DTO
 *
 * 콘텐츠 생성 후 이벤트 발행을 위해 필요한 데이터를 전달합니다.
 *
 * @property content 생성된 콘텐츠 엔티티
 * @property metadata 생성된 콘텐츠 메타데이터
 * @property contentId 콘텐츠 ID (이벤트 발행용)
 */
internal data class ContentCreationResult(
    val content: Content,
    val metadata: ContentMetadata,
    val contentId: UUID
)
