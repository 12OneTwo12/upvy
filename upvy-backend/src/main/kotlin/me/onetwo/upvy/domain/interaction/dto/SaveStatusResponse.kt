package me.onetwo.upvy.domain.interaction.dto

/**
 * 저장 상태 응답 DTO
 *
 * 특정 콘텐츠에 대한 사용자의 저장 상태를 나타냅니다.
 *
 * @property contentId 콘텐츠 ID
 * @property isSaved 저장 여부 (true: 저장, false: 저장 안 함)
 */
data class SaveStatusResponse(
    val contentId: String,
    val isSaved: Boolean
)
