package me.onetwo.growsnap.domain.analytics.repository

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.dto.UserInteraction
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자별 콘텐츠 인터랙션 레포지토리
 *
 * 협업 필터링을 위한 사용자-콘텐츠 간 인터랙션(좋아요, 저장, 공유)을 관리합니다.
 *
 * ### 주요 기능
 * - 사용자의 인터랙션 기록 저장
 * - 사용자별 인터랙션 히스토리 조회
 * - 콘텐츠별 인터랙션 사용자 조회
 */
interface UserContentInteractionRepository {

    /**
     * 사용자의 콘텐츠 인터랙션 저장
     *
     * 이미 존재하는 인터랙션인 경우 무시됩니다 (중복 방지).
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param interactionType 인터랙션 타입 (LIKE, SAVE, SHARE)
     * @return 저장 완료 신호
     */
    fun save(userId: UUID, contentId: UUID, interactionType: InteractionType): Mono<Void>

    /**
     * 사용자가 인터랙션한 콘텐츠 ID 목록 조회
     *
     * 특정 인터랙션 타입에 대해 사용자가 인터랙션한 모든 콘텐츠를 조회합니다.
     *
     * @param userId 사용자 ID
     * @param interactionType 인터랙션 타입 (null이면 모든 타입)
     * @param limit 조회할 최대 개수
     * @return 콘텐츠 ID 목록
     */
    fun findContentIdsByUser(
        userId: UUID,
        interactionType: InteractionType?,
        limit: Int
    ): Flux<UUID>

    /**
     * 콘텐츠에 인터랙션한 사용자 ID 목록 조회
     *
     * 특정 콘텐츠에 대해 인터랙션한 모든 사용자를 조회합니다.
     * 유사 사용자 찾기에 사용됩니다.
     *
     * @param contentId 콘텐츠 ID
     * @param interactionType 인터랙션 타입 (null이면 모든 타입)
     * @param limit 조회할 최대 개수
     * @return 사용자 ID 목록
     */
    fun findUsersByContent(
        contentId: UUID,
        interactionType: InteractionType?,
        limit: Int
    ): Flux<UUID>

    /**
     * 사용자의 모든 인터랙션 조회 (협업 필터링용)
     *
     * 사용자-콘텐츠-인터랙션 타입의 조합을 모두 조회하여
     * 선호도 점수 계산에 사용합니다.
     *
     * @param userId 사용자 ID
     * @param limit 조회할 최대 개수
     * @return 사용자 인터랙션 목록
     */
    fun findAllInteractionsByUser(
        userId: UUID,
        limit: Int
    ): Flux<UserInteraction>
}
