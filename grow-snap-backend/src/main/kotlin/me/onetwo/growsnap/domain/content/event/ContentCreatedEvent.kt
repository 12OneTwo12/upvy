package me.onetwo.growsnap.domain.content.event

import java.util.UUID

/**
 * 콘텐츠 생성 이벤트
 *
 * 새로운 콘텐츠가 생성되었을 때 발행되는 이벤트입니다.
 * ContentInteraction 초기화 등 비동기 처리가 필요한 작업에 사용됩니다.
 *
 * @property contentId 생성된 콘텐츠 ID
 * @property creatorId 콘텐츠 생성자 ID
 */
data class ContentCreatedEvent(
    val contentId: UUID,
    val creatorId: UUID
)
