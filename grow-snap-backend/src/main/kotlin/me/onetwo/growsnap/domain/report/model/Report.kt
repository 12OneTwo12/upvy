package me.onetwo.growsnap.domain.report.model

import java.time.Instant
import java.util.UUID

/**
 * 신고 모델
 *
 * 사용자가 부적절한 대상(콘텐츠, 댓글, 사용자)을 신고한 정보를 담고 있습니다.
 *
 * @property id 신고 ID
 * @property reporterId 신고한 사용자 ID
 * @property targetType 신고 대상 타입 (CONTENT, COMMENT, USER)
 * @property targetId 신고 대상 ID
 * @property reportType 신고 사유 타입
 * @property description 신고 상세 설명 (최대 500자, 선택 사항)
 * @property status 신고 처리 상태
 * @property createdAt 생성 시각
 * @property createdBy 생성한 사용자 ID
 * @property updatedAt 최종 수정 시각
 * @property updatedBy 최종 수정한 사용자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class Report(
    val id: Long? = null,
    val reporterId: UUID,
    val targetType: TargetType,
    val targetId: UUID,
    val reportType: ReportType,
    val description: String? = null,
    val status: ReportStatus = ReportStatus.PENDING,
    val createdAt: Instant? = null,
    val createdBy: String? = null,
    val updatedAt: Instant? = null,
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
