package me.onetwo.upvy.domain.interaction.exception

/**
 * 소셜 인터랙션 관련 예외
 *
 * 좋아요, 댓글, 저장, 공유, 신고 등 소셜 인터랙션 도메인의 예외를 정의합니다.
 */
sealed class InteractionException(message: String) : RuntimeException(message) {

    /**
     * 콘텐츠를 찾을 수 없음
     *
     * @property contentId 찾을 수 없는 콘텐츠 ID
     */
    class ContentNotFoundException(val contentId: String) :
        InteractionException("Content not found: $contentId")

    /**
     * 댓글을 찾을 수 없음
     *
     * @property commentId 찾을 수 없는 댓글 ID
     */
    class CommentNotFoundException(val commentId: String) :
        InteractionException("Comment not found: $commentId")

    /**
     * 댓글 깊이 제한 초과
     *
     * 최대 depth 2까지만 허용됩니다 (댓글 → 답글 → X)
     *
     * @property parentCommentId 부모 댓글 ID
     */
    class CommentDepthExceededException(val parentCommentId: String) :
        InteractionException("Comment depth exceeded. Cannot reply to comment: $parentCommentId")

    /**
     * 좋아요 상태 변경 실패
     *
     * @property userId 사용자 ID
     * @property contentId 콘텐츠 ID
     * @property reason 실패 이유
     */
    class LikeOperationException(val userId: String, val contentId: String, val reason: String) :
        InteractionException("Like operation failed for user $userId on content $contentId: $reason")

    /**
     * 저장 상태 변경 실패
     *
     * @property userId 사용자 ID
     * @property contentId 콘텐츠 ID
     * @property reason 실패 이유
     */
    class SaveOperationException(val userId: String, val contentId: String, val reason: String) :
        InteractionException("Save operation failed for user $userId on content $contentId: $reason")

    /**
     * 권한 없음
     *
     * @property userId 사용자 ID
     * @property targetType 대상 타입
     * @property targetId 대상 ID
     */
    class UnauthorizedException(val userId: String, val targetType: String, val targetId: String) :
        InteractionException("User $userId is not authorized to access $targetType: $targetId")

    /**
     * 신고 생성 실패
     *
     * @property reason 실패 이유
     */
    class ReportCreationException(val reason: String) :
        InteractionException("Report creation failed: $reason")
}
