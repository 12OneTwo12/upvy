package me.onetwo.growsnap.domain.report.repository

import me.onetwo.growsnap.domain.report.model.Report
import me.onetwo.growsnap.domain.report.model.ReportStatus
import me.onetwo.growsnap.domain.report.model.ReportType
import me.onetwo.growsnap.domain.report.model.TargetType
import me.onetwo.growsnap.jooq.generated.tables.Reports.Companion.REPORTS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 신고 레포지토리 구현체 (Reactive with JOOQ R2DBC)
 *
 * JOOQ 3.17+의 R2DBC 지원을 사용합니다.
 * JOOQ의 type-safe API로 SQL을 생성하고 R2DBC로 실행합니다.
 * 완전한 Non-blocking 처리를 지원합니다.
 *
 * @property dslContext JOOQ DSLContext (R2DBC 기반)
 */
@Repository
class ReportRepositoryImpl(
    private val dslContext: DSLContext
) : ReportRepository {

    /**
     * 신고 생성
     *
     * ### 처리 흐름
     * 1. reports 테이블에 INSERT
     * 2. created_at, created_by, updated_at, updated_by 자동 설정
     * 3. status는 기본값 PENDING으로 설정
     *
     * ### 비즈니스 규칙
     * - 중복 신고 방지는 Service 계층에서 처리 (exists 체크)
     * - Soft Delete 지원 (deleted_at)
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입
     * @param targetId 신고 대상 ID
     * @param reportType 신고 사유 타입
     * @param description 신고 상세 설명
     * @return 생성된 신고 (Mono)
     */
    override fun save(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID,
        reportType: ReportType,
        description: String?
    ): Mono<Report> {
        val now = Instant.now()
        val reporterIdStr = reporterId.toString()
        val targetIdStr = targetId.toString()

        return Mono.from(
            dslContext
                .insertInto(REPORTS)
                .set(REPORTS.REPORTER_ID, reporterIdStr)
                .set(REPORTS.TARGET_TYPE, targetType.name)
                .set(REPORTS.TARGET_ID, targetIdStr)
                .set(REPORTS.REASON, reportType.name)
                .set(REPORTS.DESCRIPTION, description)
                .set(REPORTS.STATUS, ReportStatus.PENDING.name)
                .set(REPORTS.CREATED_AT, now)
                .set(REPORTS.CREATED_BY, reporterIdStr)
                .set(REPORTS.UPDATED_AT, now)
                .set(REPORTS.UPDATED_BY, reporterIdStr)
                .returningResult(REPORTS.ID)
        ).map { record ->
            Report(
                id = record.getValue(REPORTS.ID),
                reporterId = reporterId,
                targetType = targetType,
                targetId = targetId,
                reportType = reportType,
                description = description,
                status = ReportStatus.PENDING,
                createdAt = now,
                createdBy = reporterIdStr,
                updatedAt = now,
                updatedBy = reporterIdStr,
                deletedAt = null
            )
        }
    }

    /**
     * 신고 존재 여부 확인
     *
     * ### 처리 흐름
     * 1. reports 테이블에서 reporter_id, target_type, target_id로 검색
     * 2. deleted_at IS NULL 조건으로 삭제되지 않은 신고만 확인
     * 3. COUNT > 0이면 true, 아니면 false 반환
     *
     * ### 비즈니스 규칙
     * - 동일한 사용자가 동일한 대상을 중복 신고할 수 없음
     * - 삭제된 신고는 제외 (deleted_at IS NULL)
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입
     * @param targetId 신고 대상 ID
     * @return 신고 여부 (Mono<Boolean>)
     */
    override fun exists(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID
    ): Mono<Boolean> {
        return Mono.from(
            dslContext
                .selectCount()
                .from(REPORTS)
                .where(REPORTS.REPORTER_ID.eq(reporterId.toString()))
                .and(REPORTS.TARGET_TYPE.eq(targetType.name))
                .and(REPORTS.TARGET_ID.eq(targetId.toString()))
                .and(REPORTS.DELETED_AT.isNull)
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 신고 조회
     *
     * ### 처리 흐름
     * 1. reports 테이블에서 reporter_id, target_type, target_id로 검색
     * 2. deleted_at IS NULL 조건으로 삭제되지 않은 신고만 조회
     * 3. 결과를 Report 모델로 매핑
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입
     * @param targetId 신고 대상 ID
     * @return 신고 (없으면 empty Mono)
     */
    override fun findByReporterIdAndTargetIdAndTargetType(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID
    ): Mono<Report> {
        return Mono.from(
            dslContext
                .select(
                    REPORTS.ID,
                    REPORTS.REPORTER_ID,
                    REPORTS.TARGET_TYPE,
                    REPORTS.TARGET_ID,
                    REPORTS.REASON,
                    REPORTS.DESCRIPTION,
                    REPORTS.STATUS,
                    REPORTS.CREATED_AT,
                    REPORTS.CREATED_BY,
                    REPORTS.UPDATED_AT,
                    REPORTS.UPDATED_BY,
                    REPORTS.DELETED_AT
                )
                .from(REPORTS)
                .where(REPORTS.REPORTER_ID.eq(reporterId.toString()))
                .and(REPORTS.TARGET_TYPE.eq(targetType.name))
                .and(REPORTS.TARGET_ID.eq(targetId.toString()))
                .and(REPORTS.DELETED_AT.isNull)
        ).map { record ->
            Report(
                id = record.getValue(REPORTS.ID),
                reporterId = UUID.fromString(record.getValue(REPORTS.REPORTER_ID)),
                targetType = TargetType.valueOf(record.getValue(REPORTS.TARGET_TYPE)!!),
                targetId = UUID.fromString(record.getValue(REPORTS.TARGET_ID)),
                reportType = ReportType.valueOf(record.getValue(REPORTS.REASON)!!),
                description = record.getValue(REPORTS.DESCRIPTION),
                status = ReportStatus.valueOf(record.getValue(REPORTS.STATUS)!!),
                createdAt = record.getValue(REPORTS.CREATED_AT),
                createdBy = record.getValue(REPORTS.CREATED_BY),
                updatedAt = record.getValue(REPORTS.UPDATED_AT),
                updatedBy = record.getValue(REPORTS.UPDATED_BY),
                deletedAt = record.getValue(REPORTS.DELETED_AT)
            )
        }
    }
}
