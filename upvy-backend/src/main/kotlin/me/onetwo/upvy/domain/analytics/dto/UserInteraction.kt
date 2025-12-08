package me.onetwo.upvy.domain.analytics.dto

import java.util.UUID

/**
 * 사용자 인터랙션 DTO
 *
 * 사용자의 콘텐츠 인터랙션 정보를 표현하기 위한 DTO입니다.
 * Repository 레이어에서 Pair<UUID, InteractionType> 대신 명확한 필드명으로 가독성을 향상시킵니다.
 *
 * @property contentId 콘텐츠 ID
 * @property interactionType 인터랙션 타입
 */
data class UserInteraction(
    val contentId: UUID,
    val interactionType: InteractionType
)
