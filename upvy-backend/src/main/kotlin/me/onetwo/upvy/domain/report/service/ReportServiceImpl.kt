package me.onetwo.upvy.domain.report.service

import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.repository.ContentMetadataRepository
import me.onetwo.upvy.domain.report.dto.ReportRequest
import me.onetwo.upvy.domain.report.dto.ReportResponse
import me.onetwo.upvy.domain.report.exception.ReportException
import me.onetwo.upvy.domain.report.model.TargetType
import me.onetwo.upvy.domain.report.repository.ReportRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 신고 서비스 구현체
 *
 * ## 처리 흐름
 * 1. 중복 신고 확인 (exists 체크)
 * 2. 신고 저장 (트랜잭션)
 * 3. 응답 반환
 *
 * @property reportRepository 신고 레포지토리
 * @property contentMetadataRepository 콘텐츠 메타데이터 레포지토리 (FUN 카테고리 확인용)
 */
@Service
class ReportServiceImpl(
    private val reportRepository: ReportRepository,
    private val contentMetadataRepository: ContentMetadataRepository
) : ReportService {

    /**
     * 대상 신고
     *
     * ### 비즈니스 규칙
     * - 로그인한 사용자만 신고 가능 (Controller에서 Principal 체크)
     * - 중복 신고 방지 (동일 사용자가 동일 대상을 여러 번 신고 불가)
     * - 신고 타입과 상세 설명을 포함하여 저장
     *
     * ### 예외
     * - DuplicateReportException: 이미 신고한 대상인 경우
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입
     * @param targetId 신고 대상 ID
     * @param request 신고 요청 데이터
     * @return 신고 응답
     */
    @Transactional
    override fun report(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID,
        request: ReportRequest
    ): Mono<ReportResponse> {
        logger.debug("Reporting target: reporterId={}, targetType={}, targetId={}, reportType={}", reporterId, targetType, targetId, request.reportType)

        return reportRepository.exists(reporterId, targetType, targetId)
            .flatMap { exists ->
                if (exists) {
                    logger.warn("Duplicate report detected: reporterId={}, targetType={}, targetId={}", reporterId, targetType, targetId)
                    Mono.error(ReportException.DuplicateReportException(reporterId.toString(), targetId.toString(), targetType.name))
                } else {
                    reportRepository.save(
                        reporterId = reporterId,
                        targetType = targetType,
                        targetId = targetId,
                        reportType = request.reportType!!,
                        description = request.description
                    )
                }
            }
            .flatMap { report ->
                logger.info("Target reported successfully: reportId={}, reporterId={}, targetType={}, targetId={}", report.id, reporterId, targetType, targetId)

                // 콘텐츠 신고 시 FUN 카테고리 여부 확인 (프론트엔드에서 OFF_TOPIC + FUN일 때 안내 표시)
                if (targetType == TargetType.CONTENT) {
                    contentMetadataRepository.findCategoryByContentId(targetId)
                        .map { category ->
                            ReportResponse.from(report, isFunCategoryContent = category == Category.FUN)
                        }
                        .defaultIfEmpty(ReportResponse.from(report))
                } else {
                    Mono.just(ReportResponse.from(report))
                }
            }
            .doOnError { error ->
                logger.error("Failed to report target: reporterId={}, targetType={}, targetId={}", reporterId, targetType, targetId, error)
            }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ReportServiceImpl::class.java)
    }
}
