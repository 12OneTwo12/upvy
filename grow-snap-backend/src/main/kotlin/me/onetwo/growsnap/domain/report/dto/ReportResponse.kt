package me.onetwo.growsnap.domain.report.dto

import me.onetwo.growsnap.domain.report.model.Report
import me.onetwo.growsnap.domain.report.model.ReportStatus
import me.onetwo.growsnap.domain.report.model.ReportType
import me.onetwo.growsnap.domain.report.model.TargetType
import java.time.Instant

/**
 * 신고 응답 DTO
 *
 * 신고 접수 완료 후 반환되는 응답 데이터입니다.
 *
 * @property id 신고 ID
 * @property reporterId 신고한 사용자 ID
 * @property targetType 신고 대상 타입
 * @property targetId 신고 대상 ID
 * @property reportType 신고 사유 타입
 * @property description 신고 상세 설명
 * @property status 신고 처리 상태
 * @property createdAt 신고 접수 시각
 */
data class ReportResponse(
    val id: Long,
    val reporterId: String,
    val targetType: TargetType,
    val targetId: String,
    val reportType: ReportType,
    val description: String?,
    val status: ReportStatus,
    val createdAt: Instant
) {
    companion object {
        /**
         * Report 모델을 ReportResponse DTO로 변환합니다.
         *
         * @param report 신고 모델
         * @return 신고 응답 DTO
         */
        fun from(report: Report): ReportResponse {
            return ReportResponse(
                id = report.id ?: throw IllegalStateException("신고 ID가 없습니다"),
                reporterId = report.reporterId.toString(),
                targetType = report.targetType,
                targetId = report.targetId.toString(),
                reportType = report.reportType,
                description = report.description,
                status = report.status,
                createdAt = report.createdAt ?: Instant.now()
            )
        }
    }
}
