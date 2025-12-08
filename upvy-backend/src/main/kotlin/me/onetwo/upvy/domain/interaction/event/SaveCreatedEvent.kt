package me.onetwo.upvy.domain.interaction.event

import java.util.UUID

/**
 * 저장 생성 이벤트
 *
 * 사용자가 콘텐츠를 저장했을 때 발행되는 이벤트입니다.
 *
 * ### 이벤트 처리 흐름
 * 1. SaveService가 user_saves 테이블에 저장 성공
 * 2. SaveCreatedEvent 발행
 * 3. InteractionEventListener가 비동기로 처리:
 *    - content_interactions의 save_count 증가
 *    - user_content_interactions 테이블에 저장 (협업 필터링용)
 *
 * @property userId 사용자 ID
 * @property contentId 콘텐츠 ID
 */
data class SaveCreatedEvent(
    val userId: UUID,
    val contentId: UUID
)
