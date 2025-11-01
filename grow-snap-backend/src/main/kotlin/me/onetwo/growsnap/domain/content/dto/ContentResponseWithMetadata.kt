package me.onetwo.growsnap.domain.content.dto

import java.util.UUID

/**
 * 콘텐츠 응답과 메타데이터를 함께 담는 내부 DTO
 *
 * 이벤트 발행을 위해 응답 데이터와 함께 contentId를 전달합니다.
 *
 * @property response 클라이언트로 반환할 콘텐츠 응답
 * @property contentId 이벤트 발행에 사용할 콘텐츠 ID
 */
internal data class ContentResponseWithMetadata(
    val response: ContentResponse,
    val contentId: UUID
)
