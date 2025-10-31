package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.analytics.dto.InteractionEventRequest
import me.onetwo.growsnap.domain.analytics.dto.InteractionType
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.analytics.service.AnalyticsService
import me.onetwo.growsnap.domain.interaction.dto.CommentRequest
import me.onetwo.growsnap.domain.interaction.dto.CommentResponse
import me.onetwo.growsnap.domain.interaction.exception.CommentException
import me.onetwo.growsnap.domain.interaction.model.Comment
import me.onetwo.growsnap.domain.interaction.repository.CommentRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 서비스 구현체
 *
 * 댓글 작성, 조회, 삭제 비즈니스 로직을 처리합니다.
 *
 * ### 처리 흐름
 * 1. 댓글 작성 (comments 테이블)
 * 2. AnalyticsService를 통한 이벤트 발행
 *    - 카운터 증가 (content_interactions.comment_count)
 *    - Spring Event 발행 (UserInteractionEvent)
 *    - user_content_interactions 테이블 저장 (협업 필터링용)
 *
 * @property commentRepository 댓글 레포지토리
 * @property analyticsService Analytics 서비스 (이벤트 발행)
 * @property contentInteractionRepository 콘텐츠 인터랙션 레포지토리
 * @property userProfileRepository 사용자 프로필 레포지토리
 */
@Service
class CommentServiceImpl(
    private val commentRepository: CommentRepository,
    private val analyticsService: AnalyticsService,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val userProfileRepository: UserProfileRepository
) : CommentService {

    /**
     * 댓글 작성
     *
     * ### 처리 흐름
     * 1. 부모 댓글 존재 여부 확인 (대댓글인 경우)
     * 2. comments 테이블에 댓글 저장
     * 3. AnalyticsService.trackInteractionEvent(COMMENT) 호출
     *    - content_interactions의 comment_count 증가
     *    - UserInteractionEvent 발행
     *    - UserInteractionEventListener가 user_content_interactions 저장 (협업 필터링용)
     * 4. 사용자 정보 조회 후 응답 반환
     *
     * @param userId 작성자 ID
     * @param contentId 콘텐츠 ID
     * @param request 댓글 작성 요청
     * @return 작성된 댓글
     */
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
            .flatMap { savedComment: Comment ->
                analyticsService.trackInteractionEvent(
                    userId,
                    InteractionEventRequest(
                        contentId = contentId,
                        interactionType = InteractionType.COMMENT
                    )
                )
                    .then(Mono.just(savedComment))
            }
            .flatMap { savedComment: Comment ->
                Mono.fromCallable {
                    userProfileRepository.findUserInfosByUserIds(setOf(userId))
                }.map { userInfoMap ->
                    val (nickname, profileImageUrl) = userInfoMap[userId] ?: Pair("Unknown", null)
                    CommentResponse(
                        id = savedComment.id!!.toString(),
                        contentId = savedComment.contentId.toString(),
                        userId = savedComment.userId.toString(),
                        userNickname = nickname,
                        userProfileImageUrl = profileImageUrl,
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
     * 콘텐츠의 댓글 목록 조회
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
    override fun getComments(contentId: UUID): Flux<CommentResponse> {
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
                            val userInfo = userInfoMap[parentComment.userId] ?: Pair("Unknown", null)
                            val replies = repliesByParentId[parentComment.id!!]?.map { reply ->
                                val replyUserInfo = userInfoMap[reply.userId] ?: Pair("Unknown", null)
                                mapToCommentResponse(reply, replyUserInfo, emptyList())
                            } ?: emptyList()

                            mapToCommentResponse(parentComment, userInfo, replies)
                        }
                }
            }
            .doOnComplete { logger.debug("Comments retrieved successfully: contentId={}", contentId) }
    }

    /**
     * 댓글 삭제
     *
     * ### 처리 흐름
     * 1. 댓글 존재 여부 및 소유권 확인
     * 2. comments 테이블에서 Soft Delete
     * 3. content_interactions의 comment_count 감소
     *
     * ### 비즈니스 규칙
     * - 자신의 댓글만 삭제 가능 (소유권 검증)
     * - user_content_interactions는 삭제하지 않음 (협업 필터링 데이터 보존)
     *
     * @param userId 요청한 사용자 ID
     * @param commentId 댓글 ID
     * @return Void
     */
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
                .then(contentInteractionRepository.decrementCommentCount(comment.contentId))
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
     * @param userInfo 사용자 정보 (닉네임, 프로필 이미지 URL)
     * @param replies 대댓글 목록
     * @return CommentResponse DTO
     */
    private fun mapToCommentResponse(
        comment: Comment,
        userInfo: Pair<String, String?>,
        replies: List<CommentResponse>
    ): CommentResponse {
        return CommentResponse(
            id = comment.id!!.toString(),
            contentId = comment.contentId.toString(),
            userId = comment.userId.toString(),
            userNickname = userInfo.first,
            userProfileImageUrl = userInfo.second,
            content = comment.content,
            parentCommentId = comment.parentCommentId?.toString(),
            createdAt = comment.createdAt.toString(),
            replies = replies
        )
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CommentServiceImpl::class.java)
    }
}
