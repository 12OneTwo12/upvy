package me.onetwo.growsnap.domain.interaction.controller

import jakarta.validation.Valid
import me.onetwo.growsnap.domain.interaction.dto.CommentListResponse
import me.onetwo.growsnap.domain.interaction.dto.CommentRequest
import me.onetwo.growsnap.domain.interaction.dto.CommentResponse
import me.onetwo.growsnap.domain.interaction.service.CommentService
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping(ApiPaths.API_V1)
class CommentController(
    private val commentService: CommentService
) {

    @PostMapping("/contents/{contentId}/comments")
    fun createComment(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID,
        @Valid @RequestBody request: CommentRequest
    ): Mono<ResponseEntity<CommentResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                commentService.createComment(userId, contentId, request)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    @GetMapping("/contents/{contentId}/comments")
    fun getComments(
        principal: Mono<Principal>?,
        @PathVariable contentId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<CommentListResponse> {
        return if (principal != null) {
            principal.toUserId().flatMap { userId ->
                commentService.getComments(userId, contentId, cursor, limit)
            }
        } else {
            commentService.getComments(null, contentId, cursor, limit)
        }
    }

    @GetMapping("/comments/{commentId}/replies")
    fun getReplies(
        principal: Mono<Principal>?,
        @PathVariable commentId: UUID,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<CommentListResponse> {
        return if (principal != null) {
            principal.toUserId().flatMap { userId ->
                commentService.getReplies(userId, commentId, cursor, limit)
            }
        } else {
            commentService.getReplies(null, commentId, cursor, limit)
        }
    }

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        principal: Mono<Principal>,
        @PathVariable commentId: UUID
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                commentService.deleteComment(userId, commentId)
            }
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
    }
}
