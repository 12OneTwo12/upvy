package me.onetwo.growsnap.domain.interaction.dto

/**
 * 댓글 목록 응답 (Cursor 기반 페이징)
 *
 * @property comments 댓글 목록
 * @property hasNext 다음 페이지 존재 여부
 * @property nextCursor 다음 페이지 커서
 */
data class CommentListResponse(
    val comments: List<CommentResponse>,
    val hasNext: Boolean,
    val nextCursor: String?
)
