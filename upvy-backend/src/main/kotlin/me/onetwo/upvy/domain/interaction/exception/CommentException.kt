package me.onetwo.upvy.domain.interaction.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus
import java.util.UUID

/**
 * 댓글 관련 예외
 *
 * BusinessException을 상속받아 GlobalExceptionHandler에서 통합 처리됩니다.
 */
sealed class CommentException(
    errorCode: String,
    httpStatus: HttpStatus,
    message: String
) : BusinessException(errorCode, httpStatus, message) {

    /**
     * 댓글을 찾을 수 없는 경우
     */
    class CommentNotFoundException(commentId: UUID) : CommentException(
        errorCode = "COMMENT_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "Comment not found: $commentId"
    )

    /**
     * 댓글 작성 실패
     */
    class CommentCreationException(reason: String?) : CommentException(
        errorCode = "COMMENT_CREATION_FAILED",
        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
        message = "Comment creation failed: ${reason ?: "unknown"}"
    )

    /**
     * 댓글 삭제 실패
     */
    class CommentDeletionException(reason: String?) : CommentException(
        errorCode = "COMMENT_DELETION_FAILED",
        httpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
        message = "Comment deletion failed: ${reason ?: "unknown"}"
    )

    /**
     * 부모 댓글을 찾을 수 없는 경우
     */
    class ParentCommentNotFoundException(parentCommentId: UUID) : CommentException(
        errorCode = "PARENT_COMMENT_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "Parent comment not found: $parentCommentId"
    )

    /**
     * 권한 없음 (다른 사용자의 댓글 삭제 시도)
     */
    class CommentAccessDeniedException(commentId: UUID) : CommentException(
        errorCode = "COMMENT_ACCESS_DENIED",
        httpStatus = HttpStatus.FORBIDDEN,
        message = "Access denied to comment: $commentId"
    )
}
