package me.onetwo.upvy.domain.interaction.service

import me.onetwo.upvy.domain.interaction.dto.CommentLikeCountResponse
import me.onetwo.upvy.domain.interaction.dto.CommentLikeResponse
import me.onetwo.upvy.domain.interaction.dto.CommentLikeStatusResponse
import me.onetwo.upvy.domain.interaction.repository.CommentLikeRepository
import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 좋아요 서비스 구현체
 *
 * 댓글 좋아요 비즈니스 로직을 처리합니다.
 *
 * ### 처리 흐름
 * 1. 댓글 좋아요 상태 변경 (user_comment_likes 테이블)
 * 2. 댓글 좋아요 수 카운트 (Repository에서 집계)
 *
 * @property commentLikeRepository 댓글 좋아요 레포지토리
 */
@Service
@Transactional(readOnly = true)
class CommentLikeServiceImpl(
    private val commentLikeRepository: CommentLikeRepository
) : CommentLikeService {

    /**
     * 댓글 좋아요
     *
     * ### 처리 흐름
     * 1. user_comment_likes 테이블에 레코드 생성 시도
     * 2. 중복 시 DataAccessException을 처리하여 idempotent 보장
     * 3. 댓글 좋아요 수 조회 및 응답 반환
     *
     * ### 비즈니스 규칙
     * - UNIQUE 제약 조건을 활용하여 중복 방지
     * - 이미 좋아요가 있으면 예외 처리로 안전하게 idempotent 보장
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 응답
     */
    @Transactional
    override fun likeComment(userId: UUID, commentId: UUID): Mono<CommentLikeResponse> {
        logger.debug("Liking comment: userId={}, commentId={}", userId, commentId)

        return commentLikeRepository.save(userId, commentId)
            .then(getLikeResponse(commentId, true))
            .onErrorResume(DataAccessException::class.java) {
                logger.debug("Comment already liked: userId={}, commentId={}", userId, commentId)
                getLikeResponse(commentId, true)
            }
            .doOnSuccess { logger.debug("Comment liked successfully: userId={}, commentId={}", userId, commentId) }
            .doOnError { error ->
                logger.error("Failed to like comment: userId={}, commentId={}", userId, commentId, error)
            }
    }

    /**
     * 댓글 좋아요 취소
     *
     * ### 처리 흐름
     * 1. user_comment_likes 테이블에서 레코드 삭제 (Soft Delete)
     * 2. 댓글 좋아요 수 조회 및 응답 반환
     *
     * ### 비즈니스 규칙
     * - delete는 idempotent하므로 좋아요가 없어도 안전하게 처리됨
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 응답
     */
    @Transactional
    override fun unlikeComment(userId: UUID, commentId: UUID): Mono<CommentLikeResponse> {
        logger.debug("Unliking comment: userId={}, commentId={}", userId, commentId)

        return commentLikeRepository.delete(userId, commentId)
            .then(getLikeResponse(commentId, false))
            .doOnSuccess { logger.debug("Comment unliked successfully: userId={}, commentId={}", userId, commentId) }
            .doOnError { error ->
                logger.error("Failed to unlike comment: userId={}, commentId={}", userId, commentId, error)
            }
    }

    /**
     * 댓글 좋아요 수 조회
     *
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 수 응답
     */
    @Transactional(readOnly = true)
    override fun getLikeCount(commentId: UUID): Mono<CommentLikeCountResponse> {
        logger.debug("Getting like count: commentId={}", commentId)

        return commentLikeRepository.countByCommentId(commentId)
            .map { likeCount ->
                CommentLikeCountResponse(
                    commentId = commentId.toString(),
                    likeCount = likeCount
                )
            }
            .doOnSuccess { response ->
                logger.debug("Like count retrieved: commentId={}, count={}", commentId, response.likeCount)
            }
    }

    /**
     * 댓글 좋아요 상태 조회
     *
     * 특정 댓글에 대한 사용자의 좋아요 상태를 확인합니다.
     *
     * ### 처리 흐름
     * 1. CommentLikeRepository.exists()로 좋아요 여부 확인
     * 2. CommentLikeStatusResponse 반환
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 상태 응답
     */
    @Transactional(readOnly = true)
    override fun getLikeStatus(userId: UUID, commentId: UUID): Mono<CommentLikeStatusResponse> {
        logger.debug("Getting like status: userId={}, commentId={}", userId, commentId)

        return commentLikeRepository.exists(userId, commentId)
            .map { isLiked ->
                CommentLikeStatusResponse(
                    commentId = commentId.toString(),
                    isLiked = isLiked
                )
            }
            .doOnSuccess { response ->
                logger.debug("Like status retrieved: userId={}, commentId={}, isLiked={}", userId, commentId, response.isLiked)
            }
    }

    /**
     * 댓글 좋아요 응답 생성
     *
     * @param commentId 댓글 ID
     * @param isLiked 좋아요 여부
     * @return 댓글 좋아요 응답
     */
    private fun getLikeResponse(commentId: UUID, isLiked: Boolean): Mono<CommentLikeResponse> {
        return commentLikeRepository.countByCommentId(commentId)
            .map { likeCount ->
                CommentLikeResponse(
                    commentId = commentId.toString(),
                    likeCount = likeCount,
                    isLiked = isLiked
                )
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommentLikeServiceImpl::class.java)
    }
}
