package me.onetwo.upvy.domain.report.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import me.onetwo.upvy.domain.report.model.ReportType

/**
 * 신고 요청 DTO
 *
 * 사용자가 대상(콘텐츠, 댓글, 사용자)을 신고할 때 전송하는 요청 데이터입니다.
 *
 * @property reportType 신고 사유 타입 (필수)
 * @property description 신고 상세 설명 (선택 사항, 최대 500자)
 */
data class ReportRequest(
    @field:NotNull(message = "신고 타입은 필수입니다")
    val reportType: ReportType?,

    @field:Size(max = 500, message = "신고 상세 설명은 최대 500자까지 입력 가능합니다")
    val description: String? = null
)
