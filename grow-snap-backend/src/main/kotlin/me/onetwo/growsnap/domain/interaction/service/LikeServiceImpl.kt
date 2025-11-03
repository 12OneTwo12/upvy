package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.interaction.dto.LikeCountResponse
import me.onetwo.growsnap.domain.interaction.dto.LikeResponse
import me.onetwo.growsnap.domain.interaction.dto.LikeStatusResponse
import me.onetwo.growsnap.domain.interaction.event.LikeCreatedEvent
import me.onetwo.growsnap.domain.interaction.event.LikeDeletedEvent
import me.onetwo.growsnap.domain.interaction.repository.UserLikeRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 좋아요 서비스 구현체 (Reactive)
 *
 * 이벤트 기반 처리:
 * 1. 좋아요 상태 변경 → 2. Event 발행 → 3. 응답 반환
 * 4. [비동기] like_count 증가/감소, 협업 필터링 데이터 저장
 *
 * R2DBC 트랜잭션: @Transactional + ReactiveTransactionManager
 * - 트랜잭션 커밋 후 @TransactionalEventListener(AFTER_COMMIT) 발화 보장
 * - 완전한 Non-blocking 처리
 *
 * @property userLikeRepository 사용자 좋아요 레포지토리
 * @property applicationEventPublisher Spring 이벤트 발행자
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 */
@Service
class LikeServiceImpl(
    private val userLikeRepository: UserLikeRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val contentInteractionRepository: ContentInteractionRepository
) : LikeService {

    /**
     * 좋아요
     *
     * ### 처리 흐름 (이벤트 기반)
     * 1. user_likes 테이블에 레코드 생성
     * 2. LikeCreatedEvent 발행 (트랜잭션 내)
     * 3. 트랜잭션 커밋
     * 4. [비동기] InteractionEventListener가 AFTER_COMMIT에 처리:
     *    - content_interactions의 like_count 증가
     *    - user_content_interactions 저장 (협업 필터링용)
     * 5. 응답 반환
     *
     * ### 비즈니스 규칙
     * - 이미 좋아요가 있으면 중복 생성 안 함 (idempotent)
     *
     * ### 트랜잭션 관리 (R2DBC)
     * - @Transactional이 reactive chain을 구독할 때 트랜잭션 시작
     * - 모든 reactive 연산이 완료되면 트랜잭션 커밋
     * - 트랜잭션 커밋 후 @TransactionalEventListener(AFTER_COMMIT) 실행 보장
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답
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
                        .doOnSuccess {
                            logger.debug("Publishing LikeCreatedEvent: userId={}, contentId={}", userId, contentId)
                            applicationEventPublisher.publishEvent(LikeCreatedEvent(userId, contentId))
                        }
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
     * ### 처리 흐름 (이벤트 기반)
     * 1. user_likes 테이블에서 레코드 삭제 (Soft Delete)
     * 2. LikeDeletedEvent 발행 (트랜잭션 내)
     * 3. 트랜잭션 커밋
     * 4. [비동기] InteractionEventListener가 AFTER_COMMIT에 처리:
     *    - content_interactions의 like_count 감소
     *    - user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     * 5. 응답 반환
     *
     * ### 비즈니스 규칙
     * - 좋아요가 없으면 아무 작업 안 함 (idempotent)
     * - 협업 필터링: 한 번이라도 좋아요를 누른 기록은 보존
     *
     * ### 트랜잭션 관리 (R2DBC)
     * - @Transactional이 reactive chain을 구독할 때 트랜잭션 시작
     * - 모든 reactive 연산이 완료되면 트랜잭션 커밋
     * - 트랜잭션 커밋 후 @TransactionalEventListener(AFTER_COMMIT) 실행 보장
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
                        .doOnSuccess {
                            logger.debug("Publishing LikeDeletedEvent: userId={}, contentId={}", userId, contentId)
                            applicationEventPublisher.publishEvent(LikeDeletedEvent(userId, contentId))
                        }
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
     * 좋아요 응답 생성 (Reactive)
     *
     * @param contentId 콘텐츠 ID
     * @param isLiked 좋아요 여부
     * @return 좋아요 응답 (Mono)
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
