package me.onetwo.upvy.domain.report.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 신고 관련 예외
 *
 * 신고 처리 중 발생하는 예외를 정의합니다.
 */
sealed class ReportException(
    errorCode: String,
    httpStatus: HttpStatus,
    message: String
) : BusinessException(errorCode, httpStatus, message) {

    /**
     * 중복 신고 예외
     *
     * 동일한 사용자가 동일한 대상을 이미 신고한 경우 발생합니다.
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetId 신고 대상 ID
     * @param targetType 신고 대상 타입 (문자열)
     */
    class DuplicateReportException(
        reporterId: String,
        targetId: String,
        targetType: String
    ) : ReportException(
        errorCode = "DUPLICATE_REPORT",
        httpStatus = HttpStatus.CONFLICT,
        message = "이미 신고한 콘텐츠입니다. reporterId=$reporterId, targetId=$targetId, targetType=$targetType"
    )

    /**
     * 신고를 찾을 수 없는 예외
     *
     * 요청한 신고 ID가 존재하지 않는 경우 발생합니다.
     *
     * @param reportId 신고 ID
     */
    class ReportNotFoundException(
        reportId: Long
    ) : ReportException(
        errorCode = "REPORT_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "신고를 찾을 수 없습니다. reportId=$reportId"
    )

    /**
     * 대상을 찾을 수 없는 예외
     *
     * 신고 대상(콘텐츠, 댓글, 사용자)이 존재하지 않는 경우 발생합니다.
     *
     * @param targetId 대상 ID
     */
    class TargetNotFoundException(
        targetId: String
    ) : ReportException(
        errorCode = "TARGET_NOT_FOUND",
        httpStatus = HttpStatus.NOT_FOUND,
        message = "신고 대상을 찾을 수 없습니다. targetId=$targetId"
    )
}
