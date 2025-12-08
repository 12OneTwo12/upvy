package me.onetwo.upvy.domain.interaction.dto

import me.onetwo.upvy.domain.content.dto.ContentResponse
import me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse

data class SavedContentResponse(
    val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val savedAt: String
)

/**
 * 저장한 콘텐츠 페이지 응답 (커서 기반 페이지네이션)
 *
 * 사용자가 저장한 콘텐츠 목록을 커서 기반으로 페이징하여 반환합니다.
 * ContentResponse와 동일한 형식으로 반환하여 다른 콘텐츠 조회 API와 일관성을 유지합니다.
 */
typealias SavedContentPageResponse = CursorPageResponse<ContentResponse>
