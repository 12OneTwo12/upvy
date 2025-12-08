package me.onetwo.upvy.domain.block.service

import me.onetwo.upvy.domain.block.dto.BlockedContentsResponse
import me.onetwo.upvy.domain.block.dto.ContentBlockResponse
import me.onetwo.upvy.domain.block.exception.BlockException
import me.onetwo.upvy.domain.block.repository.ContentBlockRepository
import me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 차단 서비스 구현체
 *
 * ## 처리 흐름
 * 1. 차단 저장 시도 (DB UPSERT로 중복 방지)
 * 2. 응답 반환
 *
 * ## 동시성 처리
 * - Repository의 UPSERT 패턴이 자동으로 중복 처리
 * - DB 제약 조건으로 안전성 보장
 *
 * @property contentBlockRepository 콘텐츠 차단 레포지토리
 */
@Service
class ContentBlockServiceImpl(
    private val contentBlockRepository: ContentBlockRepository
) : ContentBlockService {

    /**
     * 콘텐츠 차단
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 차단 가능 (Controller에서 Principal 체크)
     * - 자신의 콘텐츠도 차단 가능 (관심없음 기능)
     * - 중복 차단 자동 처리 (DB UPSERT 패턴)
     *
     * ### 동시성 안정성
     * - Repository UPSERT 패턴이 중복을 자동으로 처리
     * - 차단 해제 후 재차단 시 기존 레코드 재사용
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 콘텐츠 차단 응답
     */
    @Transactional
    override fun blockContent(
        userId: UUID,
        contentId: UUID
    ): Mono<ContentBlockResponse> {
        logger.debug("Blocking content: userId={}, contentId={}", userId, contentId)

        return contentBlockRepository.save(userId, contentId)
            .map { contentBlock ->
                logger.info("Content blocked successfully: blockId={}, userId={}, contentId={}", contentBlock.id, userId, contentId)
                ContentBlockResponse.from(contentBlock)
            }
            .doOnError { error ->
                logger.error("Failed to block content: userId={}, contentId={}", userId, contentId, error)
            }
    }

    /**
     * 콘텐츠 차단 해제
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 차단 해제 가능 (Controller에서 Principal 체크)
     * - 차단하지 않은 콘텐츠는 해제할 수 없음 (ContentBlockNotFoundException)
     *
     * ### 예외
     * - ContentBlockNotFoundException: 차단하지 않은 콘텐츠인 경우
     *
     * @param userId 차단한 사용자 ID
     * @param contentId 차단된 콘텐츠 ID
     * @return 빈 Mono
     */
    @Transactional
    override fun unblockContent(
        userId: UUID,
        contentId: UUID
    ): Mono<Void> {
        logger.debug("Unblocking content: userId={}, contentId={}", userId, contentId)

        return contentBlockRepository.exists(userId, contentId)
            .flatMap { exists ->
                if (!exists) {
                    logger.warn("Content block not found: userId={}, contentId={}", userId, contentId)
                    Mono.error(BlockException.ContentBlockNotFoundException(userId.toString(), contentId.toString()))
                } else {
                    contentBlockRepository.delete(userId, contentId)
                }
            }
            .doOnSuccess {
                logger.info("Content unblocked successfully: userId={}, contentId={}", userId, contentId)
            }
            .doOnError { error ->
                logger.error("Failed to unblock content: userId={}, contentId={}", userId, contentId, error)
            }
    }

    /**
     * 차단한 콘텐츠 목록 조회
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 조회 가능 (Controller에서 Principal 체크)
     * - 커서 기반 페이지네이션 지원 (block_id 기준)
     * - 삭제되지 않은 차단만 조회 (deleted_at IS NULL)
     *
     * @param userId 차단한 사용자 ID
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수
     * @return 차단한 콘텐츠 목록
     */
    override fun getBlockedContents(
        userId: UUID,
        cursor: String?,
        limit: Int
    ): Mono<BlockedContentsResponse> {
        logger.debug("Getting blocked contents: userId={}, cursor={}, limit={}", userId, cursor, limit)

        val cursorLong = cursor?.toLongOrNull()
        val actualLimit = limit + 1

        return contentBlockRepository.findBlockedContentsByUserId(userId, cursorLong, actualLimit)
            .collectList()
            .map { items ->
                val hasNext = items.size > limit
                val content = if (hasNext) items.dropLast(1) else items
                val nextCursor = if (hasNext) items[limit - 1].blockId.toString() else null

                logger.info("Blocked contents retrieved: userId={}, count={}, hasNext={}", userId, content.size, hasNext)

                CursorPageResponse(
                    content = content,
                    nextCursor = nextCursor,
                    hasNext = hasNext,
                    count = content.size
                )
            }
            .doOnError { error ->
                logger.error("Failed to get blocked contents: userId={}", userId, error)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ContentBlockServiceImpl::class.java)
    }
}
