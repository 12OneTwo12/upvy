package me.onetwo.growsnap.domain.report.service

import me.onetwo.growsnap.domain.report.dto.ReportRequest
import me.onetwo.growsnap.domain.report.dto.ReportResponse
import me.onetwo.growsnap.domain.report.model.TargetType
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 신고 서비스 인터페이스
 *
 * 신고 관련 비즈니스 로직을 처리합니다.
 */
interface ReportService {

    /**
     * 대상 신고
     *
     * 사용자가 대상(콘텐츠, 댓글, 사용자)을 신고합니다.
     * 중복 신고는 허용되지 않습니다.
     *
     * @param reporterId 신고한 사용자 ID
     * @param targetType 신고 대상 타입 (CONTENT, COMMENT, USER)
     * @param targetId 신고 대상 ID
     * @param request 신고 요청 데이터
     * @return 신고 응답
     */
    fun report(
        reporterId: UUID,
        targetType: TargetType,
        targetId: UUID,
        request: ReportRequest
    ): Mono<ReportResponse>
}
