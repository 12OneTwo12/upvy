package me.onetwo.upvy.domain.content.dto

import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentMetadata

/**
 * 콘텐츠와 메타데이터를 함께 담는 DTO
 *
 * Repository 레이어에서 JOIN 쿼리 결과를 명확하게 표현하기 위한 DTO입니다.
 * Pair<Content, ContentMetadata> 대신 명확한 필드명으로 가독성을 향상시킵니다.
 *
 * @property content 콘텐츠 엔티티
 * @property metadata 콘텐츠 메타데이터 엔티티
 */
data class ContentWithMetadata(
    val content: Content,
    val metadata: ContentMetadata
)
