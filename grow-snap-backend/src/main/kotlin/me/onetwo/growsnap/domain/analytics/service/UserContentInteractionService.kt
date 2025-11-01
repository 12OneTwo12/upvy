package me.onetwo.growsnap.domain.analytics.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import java.util.UUID

/**
 * 사용자별 콘텐츠 인터랙션 서비스 인터페이스
 *
 * 협업 필터링을 위한 사용자별 콘텐츠 인터랙션 데이터를 관리합니다.
 */
interface UserContentInteractionService {

    /**
     * 사용자 인터랙션을 저장합니다.
     *
     * Fire-and-forget 방식으로 비동기 저장합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param interactionType 인터랙션 타입
     */
    fun saveUserInteraction(userId: UUID, contentId: UUID, interactionType: InteractionType)
}
