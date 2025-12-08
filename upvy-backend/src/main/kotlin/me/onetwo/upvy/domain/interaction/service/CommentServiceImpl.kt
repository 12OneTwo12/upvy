package me.onetwo.upvy.domain.interaction.service

import me.onetwo.upvy.domain.analytics.dto.InteractionType
import me.onetwo.upvy.domain.analytics.event.UserInteractionEvent
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.analytics.service.ContentInteractionService
import me.onetwo.upvy.domain.interaction.dto.CommentListResponse
import me.onetwo.upvy.domain.interaction.dto.CommentRequest
import me.onetwo.upvy.domain.interaction.dto.CommentResponse
import me.onetwo.upvy.domain.interaction.exception.CommentException
import me.onetwo.upvy.domain.interaction.model.Comment
import me.onetwo.upvy.domain.interaction.repository.CommentLikeRepository
import me.onetwo.upvy.domain.interaction.repository.CommentRepository
import me.onetwo.upvy.domain.user.dto.UserInfo
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 서비스 구현체
 *
 * ## 처리 흐름
 * 1. comments 저장 (트랜잭션)
 * 2. content_interactions.comment_count 증가 (메인 체인, 즉시 반영)
 * 3. UserInteractionEvent 발행 (협업 필터링용, 비동기)
 * 4. 응답 반환
 *
 * @property commentRepository 댓글 레포지토리
 * @property commentLikeRepository 댓글 좋아요 레포지토리
 * @property contentInteractionService 콘텐츠 인터랙션 서비스
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property userProfileRepository 사용자 프로필 레포지토리
 * @property eventPublisher Reactive 이벤트 발행자
 */
@Service
@Transactional(readOnly = true)
class CommentServiceImpl(
    private val commentRepository: CommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val contentInteractionService: ContentInteractionService,
    private val userProfileRepository: UserProfileRepository,
    private val eventPublisher: ReactiveEventPublisher
) : CommentService {

    /**
     * 댓글 작성
     *
     * @param userId 작성자 ID
     * @param contentId 콘텐츠 ID
     * @param request 댓글 작성 요청
     * @return 작성된 댓글
     */
    @Transactional
    override fun createComment(userId: UUID, contentId: UUID, request: CommentRequest): Mono<CommentResponse> {
        logger.debug("Creating comment: userId={}, contentId={}", userId, contentId)

        val parentCommentId = request.parentCommentId?.let { UUID.fromString(it) }
        val validateParent: Mono<Void> = if (parentCommentId != null) {
            commentRepository.findById(parentCommentId)
                .switchIfEmpty(Mono.error(CommentException.ParentCommentNotFoundException(parentCommentId)))
                .then()
        } else {
            Mono.empty()
        }

        return validateParent
            .then(
                commentRepository.save(
                    Comment(
                        contentId = contentId,
                        userId = userId,
                        parentCommentId = parentCommentId,
                        content = request.content,
                    )
                ).switchIfEmpty(Mono.error(IllegalStateException("Failed to create comment")))
            )
            .flatMap { savedComment ->
                // 카운트 증가를 메인 체인에 포함 ← 즉시 반영
                logger.debug("Incrementing comment count for contentId={}", contentId)
                contentInteractionService.incrementCommentCount(contentId)
                    .thenReturn(savedComment)
            }
            .doOnSuccess { savedComment ->
                logger.debug("Publishing UserInteractionEvent: userId={}, contentId={}", userId, contentId)
                // 협업 필터링만 이벤트로 처리 (실패해도 OK)
                eventPublisher.publish(
                    UserInteractionEvent(
                        userId = userId,
                        contentId = contentId,
                        interactionType = InteractionType.COMMENT
                    )
                )
            }
            .flatMap { savedComment: Comment ->
                userProfileRepository.findUserInfosByUserIds(setOf(userId))
                    .map { userInfoMap ->
                        val userInfo = userInfoMap[userId] ?: UserInfo("Unknown", null)
                        CommentResponse(
                            id = savedComment.id!!.toString(),
                            contentId = savedComment.contentId.toString(),
                            userId = savedComment.userId.toString(),
                            userNickname = userInfo.nickname,
                            userProfileImageUrl = userInfo.profileImageUrl,
                            content = savedComment.content,
                            parentCommentId = savedComment.parentCommentId?.toString(),
                            createdAt = savedComment.createdAt.toString()
                        )
                    }
            }
            .doOnSuccess { response -> logger.debug("Comment created successfully: commentId={}", response.id) }
            .doOnError { error ->
                logger.error("Failed to create comment: userId={}, contentId={}", userId, contentId, error)
            }
            .onErrorMap { error ->
                if (error is CommentException) error
                else CommentException.CommentCreationException(error.message)
            }
    }

    /**
     * 콘텐츠의 최상위 댓글 목록 조회 (Cursor 기반 페이징)
     *
     * @param userId 현재 사용자 ID (좋아요 여부 확인용, null이면 isLiked = false)
     * @param contentId 콘텐츠 ID
     * @param cursor 커서 (댓글 ID, null이면 처음부터)
     * @param limit 조회 개수
     * @return 댓글 목록 응답 (페이징 정보 포함, likeCount와 isLiked 포함)
     */
    @Transactional(readOnly = true)
    override fun getComments(userId: UUID?, contentId: UUID, cursor: String?, limit: Int): Mono<CommentListResponse> {
        logger.debug("Getting comments with pagination: userId={}, contentId={}, cursor={}, limit={}", userId, contentId, cursor, limit)

        val cursorUUID = cursor?.let { UUID.fromString(it) }

        return commentRepository.findTopLevelCommentsByContentId(contentId, cursorUUID, limit + 1)
            .collectList()
            .flatMap { comments ->
                val actualComments = if (comments.size > limit) comments.take(limit) else comments
                val hasNext = comments.size > limit
                val nextCursor = if (hasNext) actualComments.lastOrNull()?.id?.toString() else null

                val userIds = actualComments.map { it.userId }.toSet()
                val userInfoMapMono = if (userIds.isNotEmpty()) {
                    userProfileRepository.findUserInfosByUserIds(userIds)
                } else {
                    Mono.just(emptyMap())
                }

                // N+1 쿼리 문제 해결: 답글 수를 일괄 조회
                val commentIds = actualComments.mapNotNull { it.id }
                val replyCountMapMono = if (commentIds.isNotEmpty()) {
                    commentRepository.countRepliesByParentCommentIds(commentIds)
                } else {
                    Mono.just(emptyMap())
                }

                // N+1 API 호출 문제 해결: 좋아요 수와 좋아요 여부를 일괄 조회
                val likeCountMapMono = if (commentIds.isNotEmpty()) {
                    commentLikeRepository.countByCommentIds(commentIds)
                } else {
                    Mono.just(emptyMap())
                }

                val likedCommentIdsMono = if (userId != null && commentIds.isNotEmpty()) {
                    commentLikeRepository.findLikedCommentIds(userId, commentIds)
                } else {
                    Mono.just(emptySet())
                }

                Mono.zip(userInfoMapMono, replyCountMapMono, likeCountMapMono, likedCommentIdsMono)
                    .map { tuple ->
                        val userInfoMap = tuple.t1
                        val replyCountMap = tuple.t2
                        val likeCountMap = tuple.t3
                        val likedCommentIds = tuple.t4

                        val responseList = actualComments.map { comment ->
                            val userInfo = userInfoMap[comment.userId] ?: UserInfo("Unknown", null)
                            val replyCount = replyCountMap[comment.id] ?: 0
                            val likeCount = likeCountMap[comment.id] ?: 0
                            val isLiked = comment.id in likedCommentIds

                            mapToCommentResponse(comment, userInfo, replyCount, likeCount, isLiked)
                        }

                        CommentListResponse(
                            comments = responseList,
                            hasNext = hasNext,
                            nextCursor = nextCursor
                        )
                    }
            }
    }

    /**
     * 대댓글 목록 조회 (Cursor 기반 페이징)
     *
     * @param parentCommentId 부모 댓글 ID
     * @param cursor 커서 (댓글 ID, null이면 처음부터)
     * @param limit 조회 개수
     * @return 대댓글 목록 응답 (페이징 정보 포함)
     */
    @Transactional(readOnly = true)
    override fun getReplies(userId: UUID?, parentCommentId: UUID, cursor: String?, limit: Int): Mono<CommentListResponse> {
        logger.debug("Getting replies with pagination: userId={}, parentCommentId={}, cursor={}, limit={}", userId, parentCommentId, cursor, limit)

        val cursorUUID = cursor?.let { UUID.fromString(it) }

        return commentRepository.findRepliesByParentCommentId(parentCommentId, cursorUUID, limit + 1)
            .collectList()
            .flatMap { replies ->
                val actualReplies = if (replies.size > limit) replies.take(limit) else replies
                val hasNext = replies.size > limit
                val nextCursor = if (hasNext) actualReplies.lastOrNull()?.id?.toString() else null

                val userIds = actualReplies.map { it.userId }.toSet()
                val userInfoMapMono = if (userIds.isNotEmpty()) {
                    userProfileRepository.findUserInfosByUserIds(userIds)
                } else {
                    Mono.just(emptyMap())
                }

                // N+1 API 호출 문제 해결: 좋아요 수와 좋아요 여부를 일괄 조회
                val replyIds = actualReplies.mapNotNull { it.id }
                val likeCountMapMono = if (replyIds.isNotEmpty()) {
                    commentLikeRepository.countByCommentIds(replyIds)
                } else {
                    Mono.just(emptyMap())
                }

                val likedReplyIdsMono = if (userId != null && replyIds.isNotEmpty()) {
                    commentLikeRepository.findLikedCommentIds(userId, replyIds)
                } else {
                    Mono.just(emptySet())
                }

                Mono.zip(userInfoMapMono, likeCountMapMono, likedReplyIdsMono)
                    .map { tuple ->
                        val userInfoMap = tuple.t1
                        val likeCountMap = tuple.t2
                        val likedReplyIds = tuple.t3

                        val responseList = actualReplies.map { reply ->
                            val userInfo = userInfoMap[reply.userId] ?: UserInfo("Unknown", null)
                            val likeCount = likeCountMap[reply.id] ?: 0
                            val isLiked = reply.id in likedReplyIds
                            mapToCommentResponse(reply, userInfo, 0, likeCount, isLiked)
                        }

                        CommentListResponse(
                            comments = responseList,
                            hasNext = hasNext,
                            nextCursor = nextCursor
                        )
                    }
            }
    }

    /**
     * 댓글 삭제
     *
     * @param userId 요청한 사용자 ID
     * @param commentId 댓글 ID
     * @return Void
     */
    @Transactional
    override fun deleteComment(userId: UUID, commentId: UUID): Mono<Void> {
        logger.debug("Deleting comment: userId={}, commentId={}", userId, commentId)

        return commentRepository.findById(commentId)
            .switchIfEmpty(Mono.error(CommentException.CommentNotFoundException(commentId)))
            .flatMap { comment ->
                if (comment.userId != userId) {
                    return@flatMap Mono.error<Void>(CommentException.CommentAccessDeniedException(commentId))
                }

                commentRepository.delete(commentId, userId)
                    .doOnSuccess { logger.debug("Decrementing comment count for contentId={}", comment.contentId) }
                    .then(contentInteractionService.decrementCommentCount(comment.contentId))
            }
            .doOnSuccess { logger.debug("Comment deleted successfully: commentId={}", commentId) }
            .doOnError { error ->
                logger.error("Failed to delete comment: userId={}, commentId={}", userId, commentId, error)
            }
            .onErrorMap { error ->
                if (error is CommentException) error
                else CommentException.CommentDeletionException(error.message)
            }
    }

    /**
     * Comment 엔티티를 CommentResponse DTO로 변환
     *
     * @param comment 댓글 엔티티
     * @param userInfo 사용자 정보
     * @param replyCount 대댓글 개수
     * @return CommentResponse DTO
     */
    private fun mapToCommentResponse(
        comment: Comment,
        userInfo: UserInfo,
        replyCount: Int = 0,
        likeCount: Int = 0,
        isLiked: Boolean = false
    ): CommentResponse {
        return CommentResponse(
            id = comment.id!!.toString(),
            contentId = comment.contentId.toString(),
            userId = comment.userId.toString(),
            userNickname = userInfo.nickname,
            userProfileImageUrl = userInfo.profileImageUrl,
            content = comment.content,
            parentCommentId = comment.parentCommentId?.toString(),
            createdAt = comment.createdAt.toString(),
            replyCount = replyCount,
            likeCount = likeCount,
            isLiked = isLiked
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommentServiceImpl::class.java)
    }
}
