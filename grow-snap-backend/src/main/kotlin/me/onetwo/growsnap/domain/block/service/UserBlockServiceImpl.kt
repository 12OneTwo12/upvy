package me.onetwo.growsnap.domain.block.service

import me.onetwo.growsnap.domain.block.dto.BlockedUsersResponse
import me.onetwo.growsnap.domain.block.dto.UserBlockResponse
import me.onetwo.growsnap.domain.block.exception.BlockException
import me.onetwo.growsnap.domain.block.repository.UserBlockRepository
import me.onetwo.growsnap.infrastructure.common.dto.CursorPageResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 차단 서비스 구현체
 *
 * ## 처리 흐름
 * 1. 자기 자신 차단 확인 (SelfBlockException)
 * 2. 중복 차단 확인 (exists 체크)
 * 3. 차단 저장 또는 삭제 (트랜잭션)
 * 4. 응답 반환
 *
 * @property userBlockRepository 사용자 차단 레포지토리
 */
@Service
class UserBlockServiceImpl(
    private val userBlockRepository: UserBlockRepository
) : UserBlockService {

    /**
     * 사용자 차단
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 차단 가능 (Controller에서 Principal 체크)
     * - 자기 자신은 차단할 수 없음 (SelfBlockException)
     * - 중복 차단 방지 (동일 사용자가 동일 사용자를 여러 번 차단 불가)
     *
     * ### 예외
     * - SelfBlockException: 자기 자신을 차단하려는 경우
     * - DuplicateUserBlockException: 이미 차단한 사용자인 경우
     *
     * @param blockerId 차단한 사용자 ID
     * @param blockedId 차단된 사용자 ID
     * @return 사용자 차단 응답
     */
    @Transactional
    override fun blockUser(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<UserBlockResponse> {
        logger.debug("Blocking user: blockerId={}, blockedId={}", blockerId, blockedId)

        if (blockerId == blockedId) {
            logger.warn("Self block attempt detected: userId={}", blockerId)
            return Mono.error(BlockException.SelfBlockException(blockerId.toString()))
        }

        return userBlockRepository.exists(blockerId, blockedId)
            .flatMap { exists ->
                if (exists) {
                    logger.warn("Duplicate user block detected: blockerId={}, blockedId={}", blockerId, blockedId)
                    Mono.error(BlockException.DuplicateUserBlockException(blockerId.toString(), blockedId.toString()))
                } else {
                    userBlockRepository.save(blockerId, blockedId)
                }
            }
            .map { userBlock ->
                logger.info("User blocked successfully: blockId={}, blockerId={}, blockedId={}", userBlock.id, blockerId, blockedId)
                UserBlockResponse.from(userBlock)
            }
            .doOnError { error ->
                logger.error("Failed to block user: blockerId={}, blockedId={}", blockerId, blockedId, error)
            }
    }

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
    @Transactional
    override fun unblockUser(
        blockerId: UUID,
        blockedId: UUID
    ): Mono<Void> {
        logger.debug("Unblocking user: blockerId={}, blockedId={}", blockerId, blockedId)

        return userBlockRepository.exists(blockerId, blockedId)
            .flatMap { exists ->
                if (!exists) {
                    logger.warn("User block not found: blockerId={}, blockedId={}", blockerId, blockedId)
                    Mono.error(BlockException.UserBlockNotFoundException(blockerId.toString(), blockedId.toString()))
                } else {
                    userBlockRepository.delete(blockerId, blockedId)
                }
            }
            .doOnSuccess {
                logger.info("User unblocked successfully: blockerId={}, blockedId={}", blockerId, blockedId)
            }
            .doOnError { error ->
                logger.error("Failed to unblock user: blockerId={}, blockedId={}", blockerId, blockedId, error)
            }
    }

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
    override fun getBlockedUsers(
        blockerId: UUID,
        cursor: String?,
        limit: Int
    ): Mono<BlockedUsersResponse> {
        logger.debug("Getting blocked users: blockerId={}, cursor={}, limit={}", blockerId, cursor, limit)

        val cursorLong = cursor?.toLongOrNull()
        val actualLimit = limit + 1

        return userBlockRepository.findBlockedUsersByBlockerId(blockerId, cursorLong, actualLimit)
            .collectList()
            .map { items ->
                val hasNext = items.size > limit
                val content = if (hasNext) items.dropLast(1) else items
                val nextCursor = if (hasNext) items[limit - 1].blockId.toString() else null

                logger.info("Blocked users retrieved: blockerId={}, count={}, hasNext={}", blockerId, content.size, hasNext)

                CursorPageResponse(
                    content = content,
                    nextCursor = nextCursor,
                    hasNext = hasNext,
                    count = content.size
                )
            }
            .doOnError { error ->
                logger.error("Failed to get blocked users: blockerId={}", blockerId, error)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UserBlockServiceImpl::class.java)
    }
}
