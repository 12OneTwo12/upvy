package me.onetwo.upvy.domain.report.model

/**
 * 신고 대상 타입
 *
 * 신고할 수 있는 대상의 종류를 정의합니다.
 */
enum class TargetType {
    /**
     * 콘텐츠 신고
     *
     * 비디오, 사진 등의 콘텐츠를 신고
     */
    CONTENT,

    /**
     * 댓글 신고
     *
     * 사용자가 작성한 댓글을 신고
     */
    COMMENT,

    /**
     * 사용자 신고
     *
     * 사용자 프로필이나 행동을 신고
     */
    USER
}
