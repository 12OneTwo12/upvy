package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.interaction.dto.CommentListResponse
import me.onetwo.growsnap.domain.interaction.dto.CommentRequest
import me.onetwo.growsnap.domain.interaction.dto.CommentResponse
import me.onetwo.growsnap.domain.interaction.event.CommentCreatedEvent
import me.onetwo.growsnap.domain.interaction.event.CommentDeletedEvent
import me.onetwo.growsnap.domain.interaction.exception.CommentException
import me.onetwo.growsnap.domain.interaction.model.Comment
import me.onetwo.growsnap.domain.interaction.repository.CommentLikeRepository
import me.onetwo.growsnap.domain.interaction.repository.CommentRepository
import me.onetwo.growsnap.domain.user.dto.UserInfo
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 서비스 구현체
 *
 * 댓글 작성, 조회, 삭제 비즈니스 로직을 처리합니다.
 *
 * ### 처리 흐름 (이벤트 기반)
 * 1. 댓글 작성 (comments 테이블)
 * 2. Spring Event 발행 (CommentCreatedEvent / CommentDeletedEvent)
 * 3. 응답 반환 (사용자는 즉시 성공 확인)
 * 4. [비동기] InteractionEventListener가 처리:
 *    - content_interactions의 comment_count 증가/감소
 *    - user_content_interactions 저장 (협업 필터링용)
 *
 * ### 장점
 * - 카운트 증가 실패해도 사용자 경험에 영향 없음
 * - 빠른 응답 시간
 * - 높은 가용성
 *
 * @property commentRepository 댓글 레포지토리
 * @property commentLikeRepository 댓글 좋아요 레포지토리
 * @property applicationEventPublisher Spring 이벤트 발행자
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property userProfileRepository 사용자 프로필 레포지토리
 */
@Service
@Transactional(readOnly = true)
class CommentServiceImpl(
    private val commentRepository: CommentRepository,
    private val commentLikeRepository: CommentLikeRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val userProfileRepository: UserProfileRepository
) : CommentService {

    /**
     * 댓글 작성
     *
     * ### 처리 흐름 (이벤트 기반)
     * 1. 부모 댓글 존재 여부 확인 (대댓글인 경우)
     * 2. comments 테이블에 댓글 저장
     * 3. CommentCreatedEvent 발행
     * 4. 응답 반환 (사용자는 즉시 성공 확인)
     * 5. [비동기] InteractionEventListener가 처리:
     *    - content_interactions의 comment_count 증가
     *    - user_content_interactions 저장 (협업 필터링용)
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
            Mono.fromCallable {
                commentRepository.findById(parentCommentId)
                    ?: throw CommentException.ParentCommentNotFoundException(parentCommentId)
            }.then()
        } else {
            Mono.empty()
        }

        return validateParent
            .then(
                Mono.fromCallable {
                    commentRepository.save(
                        Comment(
                            contentId = contentId,
                            userId = userId,
                            parentCommentId = parentCommentId,
                            content = request.content,
                        )
                    ) ?: throw IllegalStateException("Failed to create comment")
                }
            )
            .doOnSuccess { savedComment ->
                logger.debug("Publishing CommentCreatedEvent: userId={}, contentId={}", userId, contentId)
                applicationEventPublisher.publishEvent(
                    CommentCreatedEvent(userId, contentId)
                )
            }
            .flatMap { savedComment: Comment ->
                Mono.fromCallable {
                    userProfileRepository.findUserInfosByUserIds(setOf(userId))
                }.map { userInfoMap ->
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

        return Mono.fromCallable {
            val comments = commentRepository.findTopLevelCommentsByContentId(contentId, cursorUUID, limit)
            val actualComments = if (comments.size > limit) comments.take(limit) else comments
            val hasNext = comments.size > limit
            val nextCursor = if (hasNext) actualComments.lastOrNull()?.id?.toString() else null

            val userIds = actualComments.map { it.userId }.toSet()
            val userInfoMap = if (userIds.isNotEmpty()) {
                userProfileRepository.findUserInfosByUserIds(userIds)
            } else {
                emptyMap()
            }

            // N+1 쿼리 문제 해결: 답글 수를 일괄 조회
            val commentIds = actualComments.mapNotNull { it.id }
            val replyCountMap = if (commentIds.isNotEmpty()) {
                commentRepository.countRepliesByParentCommentIds(commentIds)
            } else {
                emptyMap()
            }

            // N+1 API 호출 문제 해결: 좋아요 수와 좋아요 여부를 일괄 조회
            val likeCountMap = if (commentIds.isNotEmpty()) {
                commentLikeRepository.countByCommentIds(commentIds)
            } else {
                emptyMap()
            }

            val likedCommentIds = if (userId != null && commentIds.isNotEmpty()) {
                commentLikeRepository.findLikedCommentIds(userId, commentIds)
            } else {
                emptySet()
            }

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

        return Mono.fromCallable {
            val replies = commentRepository.findRepliesByParentCommentId(parentCommentId, cursorUUID, limit)
            val actualReplies = if (replies.size > limit) replies.take(limit) else replies
            val hasNext = replies.size > limit
            val nextCursor = if (hasNext) actualReplies.lastOrNull()?.id?.toString() else null

            val userIds = actualReplies.map { it.userId }.toSet()
            val userInfoMap = if (userIds.isNotEmpty()) {
                userProfileRepository.findUserInfosByUserIds(userIds)
            } else {
                emptyMap()
            }

            // N+1 API 호출 문제 해결: 좋아요 수와 좋아요 여부를 일괄 조회
            val replyIds = actualReplies.mapNotNull { it.id }
            val likeCountMap = if (replyIds.isNotEmpty()) {
                commentLikeRepository.countByCommentIds(replyIds)
            } else {
                emptyMap()
            }

            val likedReplyIds = if (userId != null && replyIds.isNotEmpty()) {
                commentLikeRepository.findLikedCommentIds(userId, replyIds)
            } else {
                emptySet()
            }

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

    /**
     * 콘텐츠의 댓글 목록 조회 (기존 방식, 하위 호환성)
     *
     * N+1 쿼리 문제를 방지하기 위해 모든 사용자 정보를 한 번에 조회합니다.
     *
     * ### 처리 흐름
     * 1. comments 테이블에서 해당 콘텐츠의 모든 댓글 조회
     * 2. 모든 작성자 ID를 수집
     * 3. UserProfileRepository로 사용자 정보 일괄 조회 (N+1 문제 해결)
     * 4. 메모리에서 데이터 조합하여 계층 구조 생성 (부모 댓글 - 대댓글)
     *
     * @param contentId 콘텐츠 ID
     * @return 댓글 목록 (계층 구조)
     */
    @Deprecated("Use getComments(contentId, cursor, limit) instead")
    @Transactional(readOnly = true)
    override fun getCommentsLegacy(contentId: UUID): Flux<CommentResponse> {
        logger.debug("Getting comments: contentId={}", contentId)

        return Mono.fromCallable { commentRepository.findByContentId(contentId) }
            .flatMapMany { comments ->
                if (comments.isEmpty()) {
                    return@flatMapMany Flux.empty()
                }

                val userIds = comments.map { it.userId }.toSet()

                Mono.fromCallable {
                    userProfileRepository.findUserInfosByUserIds(userIds)
                }.flatMapMany { userInfoMap ->
                    val repliesByParentId = comments.filter { it.parentCommentId != null }
                        .groupBy { it.parentCommentId!! }

                    val parentComments = comments.filter { it.parentCommentId == null }

                    Flux.fromIterable(parentComments)
                        .map { parentComment ->
                            val userInfo = userInfoMap[parentComment.userId] ?: UserInfo("Unknown", null)
                            val replyCount = repliesByParentId[parentComment.id!!]?.size ?: 0

                            mapToCommentResponse(parentComment, userInfo, replyCount)
                        }
                }
            }
            .doOnComplete { logger.debug("Comments retrieved successfully: contentId={}", contentId) }
    }

    /**
     * 댓글 삭제
     *
     * ### 처리 흐름 (이벤트 기반)
     * 1. 댓글 존재 여부 및 소유권 확인
     * 2. comments 테이블에서 Soft Delete
     * 3. CommentDeletedEvent 발행
     * 4. 응답 반환
     * 5. [비동기] InteractionEventListener가 처리:
     *    - content_interactions의 comment_count 감소
     *
     * ### 비즈니스 규칙
     * - 자신의 댓글만 삭제 가능 (소유권 검증)
     * - user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * @param userId 요청한 사용자 ID
     * @param commentId 댓글 ID
     * @return Void
     */
    @Transactional
    override fun deleteComment(userId: UUID, commentId: UUID): Mono<Void> {
        logger.debug("Deleting comment: userId={}, commentId={}", userId, commentId)

        return Mono.fromCallable {
            commentRepository.findById(commentId)
                ?: throw CommentException.CommentNotFoundException(commentId)
        }.flatMap { comment ->
            if (comment.userId != userId) {
                return@flatMap Mono.error<Void>(CommentException.CommentAccessDeniedException(commentId))
            }

            Mono.fromCallable { commentRepository.delete(commentId, userId) }
                .doOnSuccess {
                    logger.debug("Publishing CommentDeletedEvent: userId={}, contentId={}", userId, comment.contentId)
                    applicationEventPublisher.publishEvent(
                        CommentDeletedEvent(userId, comment.contentId)
                    )
                }
                .then()
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
