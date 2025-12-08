package me.onetwo.upvy.domain.interaction.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 댓글 작성 요청 DTO
 *
 * @property content 댓글 내용 (최대 1000자)
 * @property parentCommentId 부모 댓글 ID (대댓글인 경우)
 */
data class CommentRequest(
    @field:NotBlank(message = "댓글 내용은 필수입니다")
    @field:Size(max = 1000, message = "댓글은 최대 1000자까지 작성 가능합니다")
    val content: String,

    val parentCommentId: String? = null
)
