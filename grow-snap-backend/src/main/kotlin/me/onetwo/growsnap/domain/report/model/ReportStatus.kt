package me.onetwo.growsnap.domain.report.model

/**
 * 신고 처리 상태
 *
 * 관리자가 신고를 검토하고 처리한 상태를 나타냅니다.
 */
enum class ReportStatus {
    /**
     * 대기 중
     *
     * 신고가 접수되었으나 아직 검토되지 않은 상태
     */
    PENDING,

    /**
     * 승인됨
     *
     * 신고가 타당하다고 판단되어 조치가 완료된 상태
     */
    APPROVED,

    /**
     * 거부됨
     *
     * 신고가 부적절하거나 타당하지 않다고 판단되어 기각된 상태
     */
    REJECTED
}
