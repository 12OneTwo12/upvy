package me.onetwo.growsnap.domain.interaction.controller

import jakarta.validation.Valid
import me.onetwo.growsnap.domain.interaction.dto.CommentRequest
import me.onetwo.growsnap.domain.interaction.dto.CommentResponse
import me.onetwo.growsnap.domain.interaction.service.CommentService
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class CommentController(
    private val commentService: CommentService
) {

    @PostMapping("/videos/{videoId}/comments")
    fun createComment(
        principal: Mono<Principal>,
        @PathVariable videoId: String,
        @Valid @RequestBody request: CommentRequest
    ): Mono<ResponseEntity<CommentResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                val contentId = UUID.fromString(videoId)
                commentService.createComment(userId, contentId, request)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    @GetMapping("/videos/{videoId}/comments")
    fun getComments(@PathVariable videoId: String): Flux<CommentResponse> {
        val contentId = UUID.fromString(videoId)

        return commentService.getComments(contentId)
    }

    @DeleteMapping("/comments/{commentId}")
    fun deleteComment(
        principal: Mono<Principal>,
        @PathVariable commentId: String
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                val id = UUID.fromString(commentId)
                commentService.deleteComment(userId, id)
            }
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
    }
}
