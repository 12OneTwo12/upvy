package me.onetwo.upvy.domain.report.repository

import me.onetwo.upvy.domain.report.model.Report
import me.onetwo.upvy.domain.report.model.ReportType
import me.onetwo.upvy.domain.report.model.TargetType
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 신고 레포지토리 인터페이스 (Reactive)
 *
 * 사용자의 신고를 관리합니다.
 * JOOQ를 사용하여 SQL을 생성하고, R2DBC를 통해 실행합니다.
 * 모든 메서드는 Mono를 반환하여 완전한 Non-blocking 처리를 지원합니다.
 */
interface ReportRepository {

    /**
     * 신고 생성
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입
     * @param targetId 신고 대상 ID
     * @param reportType 신고 사유 타입
     * @param description 신고 상세 설명
     * @return 생성된 신고 (Mono)
     */
    fun save(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID,
        reportType: ReportType,
        description: String?
    ): Mono<Report>

    /**
     * 신고 존재 여부 확인
     *
     * 동일한 사용자가 동일한 대상을 이미 신고했는지 확인합니다.
     * 삭제되지 않은 신고만 확인합니다 (deleted_at IS NULL).
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입
     * @param targetId 신고 대상 ID
     * @return 신고 여부 (true: 이미 신고함, false: 신고하지 않음)
     */
    fun exists(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID
    ): Mono<Boolean>

    /**
     * 신고 조회
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입
     * @param targetId 신고 대상 ID
     * @return 신고 (없으면 empty Mono)
     */
    fun findByReporterIdAndTargetIdAndTargetType(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID
    ): Mono<Report>
}
