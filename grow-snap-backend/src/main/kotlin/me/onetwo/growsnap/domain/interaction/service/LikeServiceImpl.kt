package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.event.UserInteractionEvent
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.ContentInteractionService
import me.onetwo.growsnap.domain.interaction.dto.LikeCountResponse
import me.onetwo.growsnap.domain.interaction.dto.LikeResponse
import me.onetwo.growsnap.domain.interaction.dto.LikeStatusResponse
import me.onetwo.growsnap.domain.interaction.repository.UserLikeRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 좋아요 서비스 구현체
 *
 * ## 처리 흐름
 * 1. user_likes 저장 (트랜잭션)
 * 2. content_interactions.like_count 증가 (메인 체인, 즉시 반영)
 * 3. UserInteractionEvent 발행 (협업 필터링용, 비동기)
 * 4. 응답 반환
 *
 * @property userLikeRepository 사용자 좋아요 레포지토리
 * @property contentInteractionService 콘텐츠 인터랙션 서비스
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property eventPublisher Reactive 이벤트 발행자
 */
@Service
class LikeServiceImpl(
    private val userLikeRepository: UserLikeRepository,
    private val contentInteractionService: ContentInteractionService,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val eventPublisher: ReactiveEventPublisher
) : LikeService {

    /**
     * 좋아요
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답 (카운트 포함)
     */
    @Transactional
    override fun likeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
        logger.debug("Liking content: userId={}, contentId={}", userId, contentId)

        return userLikeRepository.exists(userId, contentId)
            .flatMap { exists ->
                if (exists) {
                    logger.debug("Content already liked: userId={}, contentId={}", userId, contentId)
                    getLikeResponse(contentId, true)
                } else {
                    userLikeRepository.save(userId, contentId)
                        .flatMap {
                            // 카운트 증가를 메인 체인에 포함 ← 즉시 반영
                            logger.debug("Incrementing like count for contentId={}", contentId)
                            contentInteractionService.incrementLikeCount(contentId)
                        }
                        .doOnSuccess {
                            logger.debug("Publishing UserInteractionEvent: userId={}, contentId={}", userId, contentId)
                            // 협업 필터링만 이벤트로 처리 (실패해도 OK)
                            eventPublisher.publish(
                                UserInteractionEvent(
                                    userId = userId,
                                    contentId = contentId,
                                    interactionType = InteractionType.LIKE
                                )
                            )
                        }
                        .then(getLikeResponse(contentId, true))  // ← 카운트 항상 정확!
                }
            }
            .doOnSuccess { logger.debug("Content liked successfully: userId={}, contentId={}", userId, contentId) }
            .doOnError { error ->
                logger.error("Failed to like content: userId={}, contentId={}", userId, contentId, error)
            }
    }

    /**
     * 좋아요 취소
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답
     */
    @Transactional
    override fun unlikeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
        logger.debug("Unliking content: userId={}, contentId={}", userId, contentId)

        return userLikeRepository.exists(userId, contentId)
            .flatMap { exists ->
                if (!exists) {
                    logger.debug("Content not liked: userId={}, contentId={}", userId, contentId)
                    getLikeResponse(contentId, false)
                } else {
                    userLikeRepository.delete(userId, contentId)
                        .doOnSuccess { logger.debug("Decrementing like count for contentId={}", contentId) }
                        .then(contentInteractionService.decrementLikeCount(contentId))
                        .then(getLikeResponse(contentId, false))
                }
            }
            .doOnSuccess { logger.debug("Content unliked successfully: userId={}, contentId={}", userId, contentId) }
            .doOnError { error ->
                logger.error("Failed to unlike content: userId={}, contentId={}", userId, contentId, error)
            }
    }

    /**
     * 좋아요 수 조회
     *
     * @param contentId 콘텐츠 ID
     * @return 좋아요 수 응답
     */
    override fun getLikeCount(contentId: UUID): Mono<LikeCountResponse> {
        logger.debug("Getting like count: contentId={}", contentId)

        return contentInteractionRepository.getLikeCount(contentId)
            .map { likeCount ->
                LikeCountResponse(
                    contentId = contentId.toString(),
                    likeCount = likeCount
                )
            }
            .doOnSuccess { response ->
                logger.debug("Like count retrieved: contentId={}, count={}", contentId, response.likeCount)
            }
    }

    /**
     * 좋아요 상태 조회
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 상태 응답
     */
    override fun getLikeStatus(userId: UUID, contentId: UUID): Mono<LikeStatusResponse> {
        logger.debug("Getting like status: userId={}, contentId={}", userId, contentId)

        return userLikeRepository.exists(userId, contentId)
            .map { isLiked ->
                LikeStatusResponse(
                    contentId = contentId.toString(),
                    isLiked = isLiked
                )
            }
            .doOnSuccess { response ->
                logger.debug("Like status retrieved: userId={}, contentId={}, isLiked={}", userId, contentId, response.isLiked)
            }
    }

    /**
     * 좋아요 응답 생성
     */
    private fun getLikeResponse(contentId: UUID, isLiked: Boolean): Mono<LikeResponse> {
        return contentInteractionRepository.getLikeCount(contentId)
            .map { likeCount ->
                LikeResponse(
                    contentId = contentId.toString(),
                    likeCount = likeCount,
                    isLiked = isLiked
                )
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LikeServiceImpl::class.java)
    }
}
