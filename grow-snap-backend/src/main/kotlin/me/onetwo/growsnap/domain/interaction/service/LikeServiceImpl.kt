package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.AnalyticsService
import me.onetwo.growsnap.domain.interaction.dto.LikeCountResponse
import me.onetwo.growsnap.domain.interaction.dto.LikeResponse
import me.onetwo.growsnap.domain.interaction.dto.LikeStatusResponse
import me.onetwo.growsnap.domain.interaction.repository.UserLikeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

/**
 * 좋아요 서비스 구현체
 *
 * 콘텐츠 좋아요 비즈니스 로직을 처리합니다.
 *
 * ### 처리 흐름
 * 1. 좋아요 상태 변경 (user_likes 테이블)
 * 2. AnalyticsService를 통한 이벤트 발행
 *    - 카운터 증가 (content_interactions 테이블)
 *    - Spring Event 발행 (UserInteractionEvent)
 *    - user_content_interactions 테이블 저장 (협업 필터링용)
 *
 * @property userLikeRepository 사용자 좋아요 레포지토리
 * @property analyticsService Analytics 서비스 (이벤트 발행)
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 */
@Service
class LikeServiceImpl(
    private val userLikeRepository: UserLikeRepository,
    private val analyticsService: AnalyticsService,
    private val contentInteractionRepository: ContentInteractionRepository
) : LikeService {

    /**
     * 좋아요
     *
     * ### 처리 흐름
     * 1. user_likes 테이블에 레코드 생성
     * 2. AnalyticsService.trackInteractionEvent(LIKE) 호출
     *    - content_interactions의 like_count 증가
     *    - UserInteractionEvent 발행
     *    - UserInteractionEventListener가 user_content_interactions 저장 (협업 필터링용)
     *
     * ### 비즈니스 규칙
     * - 이미 좋아요가 있으면 중복 생성 안 함 (idempotent)
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답
     */
    override fun likeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
        logger.debug("Liking content: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userLikeRepository.exists(userId, contentId) }
            .flatMap { exists ->
                if (exists) {
                    logger.debug("Content already liked: userId={}, contentId={}", userId, contentId)
                    getLikeResponse(contentId, true)
                } else {
                    Mono.fromCallable { userLikeRepository.save(userId, contentId) }
                        .then(
                            analyticsService.trackInteractionEvent(
                                userId,
                                InteractionEventRequest(
                                    contentId = contentId,
                                    interactionType = InteractionType.LIKE
                                )
                            )
                        )
                        .then(getLikeResponse(contentId, true))
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
     * ### 처리 흐름
     * 1. user_likes 테이블에서 레코드 삭제 (Soft Delete)
     * 2. content_interactions의 like_count 감소
     * 3. user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * ### 비즈니스 규칙
     * - 좋아요가 없으면 아무 작업 안 함 (idempotent)
     * - 협업 필터링: 한 번이라도 좋아요를 누른 기록은 보존
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답
     */
    override fun unlikeContent(userId: UUID, contentId: UUID): Mono<LikeResponse> {
        logger.debug("Unliking content: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userLikeRepository.exists(userId, contentId) }
            .flatMap { exists ->
                if (!exists) {
                    logger.debug("Content not liked: userId={}, contentId={}", userId, contentId)
                    getLikeResponse(contentId, false)
                } else {
                    Mono.fromCallable { userLikeRepository.delete(userId, contentId) }
                        .then(contentInteractionRepository.decrementLikeCount(contentId))
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
     * 특정 콘텐츠에 대한 사용자의 좋아요 상태를 확인합니다.
     *
     * ### 처리 흐름
     * 1. UserLikeRepository.exists()로 좋아요 여부 확인
     * 2. LikeStatusResponse 반환
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 상태 응답
     */
    override fun getLikeStatus(userId: UUID, contentId: UUID): Mono<LikeStatusResponse> {
        logger.debug("Getting like status: userId={}, contentId={}", userId, contentId)

        return Mono.fromCallable { userLikeRepository.exists(userId, contentId) }
            .subscribeOn(Schedulers.boundedElastic())
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
     *
     * @param contentId 콘텐츠 ID
     * @param isLiked 좋아요 여부
     * @return 좋아요 응답
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
