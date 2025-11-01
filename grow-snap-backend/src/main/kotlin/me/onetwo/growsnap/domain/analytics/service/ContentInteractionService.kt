package me.onetwo.growsnap.domain.analytics.service

import java.util.UUID

/**
 * 콘텐츠 인터랙션 서비스 인터페이스
 *
 * 콘텐츠 인터랙션 생성 및 관리를 담당합니다.
 */
interface ContentInteractionService {

    /**
     * 콘텐츠 인터랙션 생성
     *
     * 새로운 콘텐츠가 생성되면 인터랙션 레코드를 초기화합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param creatorId 생성자 ID
     */
    fun createContentInteraction(contentId: UUID, creatorId: UUID)
}
