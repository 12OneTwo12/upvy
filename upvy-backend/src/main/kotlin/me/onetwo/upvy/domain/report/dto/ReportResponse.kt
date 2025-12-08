package me.onetwo.upvy.domain.report.dto

import me.onetwo.upvy.domain.report.model.Report
import me.onetwo.upvy.domain.report.model.ReportStatus
import me.onetwo.upvy.domain.report.model.ReportType
import me.onetwo.upvy.domain.report.model.TargetType
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
 * @property isFunCategoryContent FUN 카테고리 콘텐츠 여부 (OFF_TOPIC 신고 시 프론트엔드에서 안내 표시용)
 */
data class ReportResponse(
    val id: Long,
    val reporterId: String,
    val targetType: TargetType,
    val targetId: String,
    val reportType: ReportType,
    val description: String?,
    val status: ReportStatus,
    val createdAt: Instant,
    val isFunCategoryContent: Boolean = false
) {
    companion object {
        /**
         * Report 모델을 ReportResponse DTO로 변환합니다.
         *
         * @param report 신고 모델
         * @param isFunCategoryContent FUN 카테고리 콘텐츠 여부
         * @return 신고 응답 DTO
         */
        fun from(report: Report, isFunCategoryContent: Boolean = false): ReportResponse {
            return ReportResponse(
                id = report.id ?: error("신고 ID가 없습니다"),
                reporterId = report.reporterId.toString(),
                targetType = report.targetType,
                targetId = report.targetId.toString(),
                reportType = report.reportType,
                description = report.description,
                status = report.status,
                createdAt = report.createdAt ?: error("신고 생성 시각이 없습니다"),
                isFunCategoryContent = isFunCategoryContent
            )
        }
    }
}
