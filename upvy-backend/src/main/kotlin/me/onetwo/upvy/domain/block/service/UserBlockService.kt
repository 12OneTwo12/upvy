package me.onetwo.upvy.domain.block.service

import me.onetwo.upvy.domain.block.dto.BlockedUsersResponse
import me.onetwo.upvy.domain.block.dto.UserBlockResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 차단 서비스 인터페이스
 *
 * 사용자 간 차단 관련 비즈니스 로직을 처리합니다.
 * Reactive Mono/Flux를 반환하여 Non-blocking 처리를 지원합니다.
 */
interface UserBlockService {

    /**
     * 사용자 차단
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 차단 가능 (Controller에서 Principal 체크)
     * - 자기 자신은 차단할 수 없음 (SelfBlockException)
     * - 중복 차단 방지 (DuplicateUserBlockException)
     *
     * ### 예외
     * - SelfBlockException: 자기 자신을 차단하려는 경우
     * - DuplicateUserBlockException: 이미 차단한 사용자인 경우
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     * @return 사용자 차단 응답
     */
    fun blockUser(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<UserBlockResponse>

    /**
     * 사용자 차단 해제
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 차단 해제 가능 (Controller에서 Principal 체크)
     * - 차단하지 않은 사용자는 해제할 수 없음 (UserBlockNotFoundException)
     *
     * ### 예외
     * - UserBlockNotFoundException: 차단하지 않은 사용자인 경우
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     * @return 빈 Mono
     */
    fun unblockUser(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<Void>

    /**
     * 차단한 사용자 목록 조회
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 조회 가능 (Controller에서 Principal 체크)
     * - 커서 기반 페이지네이션 지원 (block_id 기준)
     * - 삭제되지 않은 차단만 조회 (deleted_at IS NULL)
     *
     * @param blockerId 차단한 사용자 ID
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수
     * @return 차단한 사용자 목록
     */
    fun getBlockedUsers(
        blockerId: UUID,
        cursor: String?,
        limit: Int
    ): Mono<BlockedUsersResponse>
}
