package me.onetwo.upvy.domain.interaction.service

import me.onetwo.upvy.domain.interaction.dto.CommentListResponse
import me.onetwo.upvy.domain.interaction.dto.CommentRequest
import me.onetwo.upvy.domain.interaction.dto.CommentResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 서비스 인터페이스
 */
interface CommentService {

    /**
     * 댓글 작성
     *
     * @param userId 작성자 ID
     * @param contentId 콘텐츠 ID
     * @param request 댓글 작성 요청
     * @return 작성된 댓글
     */
    fun createComment(userId: UUID, contentId: UUID, request: CommentRequest): Mono<CommentResponse>

    /**
     * 콘텐츠의 댓글 목록 조회 (Cursor 기반 페이징)
     *
     * @param userId 현재 사용자 ID (좋아요 여부 확인용, null이면 isLiked = false)
     * @param contentId 콘텐츠 ID
     * @param cursor 커서 (댓글 ID, null이면 처음부터)
     * @param limit 조회 개수 (기본값 20)
     * @return 댓글 목록 응답 (페이징 정보 포함, likeCount와 isLiked 포함)
     */
    fun getComments(userId: UUID?, contentId: UUID, cursor: String?, limit: Int = 20): Mono<CommentListResponse>

    /**
     * 대댓글 목록 조회 (Cursor 기반 페이징)
     *
     * @param userId 현재 사용자 ID (좋아요 여부 확인용, null이면 isLiked = false)
     * @param parentCommentId 부모 댓글 ID
     * @param cursor 커서 (댓글 ID, null이면 처음부터)
     * @param limit 조회 개수 (기본값 20)
     * @return 대댓글 목록 응답 (페이징 정보 포함, likeCount와 isLiked 포함)
     */
    fun getReplies(userId: UUID?, parentCommentId: UUID, cursor: String?, limit: Int = 20): Mono<CommentListResponse>

    /**
     * 댓글 삭제
     *
     * @param userId 요청한 사용자 ID
     * @param commentId 댓글 ID
     * @return Void
     */
    fun deleteComment(userId: UUID, commentId: UUID): Mono<Void>
}
