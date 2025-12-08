package me.onetwo.upvy.domain.content.model

/**
 * 콘텐츠 상태
 *
 * 콘텐츠의 게시 상태를 나타냅니다.
 */
enum class ContentStatus {
    /**
     * 대기 중 - 업로드는 완료되었으나 아직 게시되지 않은 상태
     */
    PENDING,

    /**
     * 게시됨 - 사용자에게 공개된 상태
     */
    PUBLISHED,

    /**
     * 삭제됨 - 삭제 처리된 상태 (Soft Delete)
     */
    DELETED
}
