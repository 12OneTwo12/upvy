package me.onetwo.upvy.domain.interaction.controller

import me.onetwo.upvy.domain.interaction.dto.CommentLikeCountResponse
import me.onetwo.upvy.domain.interaction.dto.CommentLikeResponse
import me.onetwo.upvy.domain.interaction.dto.CommentLikeStatusResponse
import me.onetwo.upvy.domain.interaction.service.CommentLikeService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 댓글 좋아요 컨트롤러
 *
 * 댓글 좋아요 관련 HTTP 요청을 처리합니다.
 *
 * @property commentLikeService 댓글 좋아요 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
class CommentLikeController(
    private val commentLikeService: CommentLikeService
) {

    /**
     * 댓글 좋아요
     *
     * POST /api/v1/comments/{commentId}/likes
     *
     * @param principal 인증된 사용자 Principal
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 응답
     */
    @PostMapping("/comments/{commentId}/likes")
    fun likeComment(
        principal: Mono<Principal>,
        @PathVariable commentId: UUID
    ): Mono<ResponseEntity<CommentLikeResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                commentLikeService.likeComment(userId, commentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 댓글 좋아요 취소
     *
     * DELETE /api/v1/comments/{commentId}/likes
     *
     * @param principal 인증된 사용자 Principal
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 응답
     */
    @DeleteMapping("/comments/{commentId}/likes")
    fun unlikeComment(
        principal: Mono<Principal>,
        @PathVariable commentId: UUID
    ): Mono<ResponseEntity<CommentLikeResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                commentLikeService.unlikeComment(userId, commentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 댓글 좋아요 수 조회
     *
     * GET /api/v1/comments/{commentId}/likes/count
     *
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 수 응답
     */
    @GetMapping("/comments/{commentId}/likes/count")
    fun getLikeCount(
        @PathVariable commentId: UUID
    ): Mono<ResponseEntity<CommentLikeCountResponse>> {
        return commentLikeService.getLikeCount(commentId)
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 댓글 좋아요 상태 조회
     *
     * 특정 댓글에 대한 사용자의 좋아요 상태를 확인합니다.
     *
     * GET /api/v1/comments/{commentId}/likes/check
     *
     * @param principal 인증된 사용자 Principal
     * @param commentId 댓글 ID
     * @return 댓글 좋아요 상태 응답
     */
    @GetMapping("/comments/{commentId}/likes/check")
    fun getLikeStatus(
        principal: Mono<Principal>,
        @PathVariable commentId: UUID
    ): Mono<ResponseEntity<CommentLikeStatusResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                commentLikeService.getLikeStatus(userId, commentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }
}
