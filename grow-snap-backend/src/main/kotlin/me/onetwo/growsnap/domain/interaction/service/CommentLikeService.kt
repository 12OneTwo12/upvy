package me.onetwo.growsnap.domain.interaction.service

import me.onetwo.growsnap.domain.interaction.dto.CommentLikeCountResponse
import me.onetwo.growsnap.domain.interaction.dto.CommentLikeResponse
import me.onetwo.growsnap.domain.interaction.dto.CommentLikeStatusResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 댓글 좋아요 서비스 인터페이스
 *
 * 댓글 좋아요 기능을 제공합니다.
 */
interface CommentLikeService {

    /**
     * 댓글 좋아요
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 응답
     */
    fun likeComment(userId: UUID, commentId: UUID): Mono<CommentLikeResponse>

    /**
     * 댓글 좋아요 취소
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 응답
     */
    fun unlikeComment(userId: UUID, commentId: UUID): Mono<CommentLikeResponse>

    /**
     * 댓글 좋아요 수 조회
     *
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 수 응답
     */
    fun getLikeCount(commentId: UUID): Mono<CommentLikeCountResponse>

    /**
     * 댓글 좋아요 상태 조회
     *
     * 특정 댓글에 대한 사용자의 좋아요 상태를 확인합니다.
     *
     * @param userId 사용자 ID
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 상태 응답
     */
    fun getLikeStatus(userId: UUID, commentId: UUID): Mono<CommentLikeStatusResponse>
}
