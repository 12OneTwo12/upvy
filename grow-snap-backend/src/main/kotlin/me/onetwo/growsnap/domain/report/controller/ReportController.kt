package me.onetwo.growsnap.domain.report.controller

import jakarta.validation.Valid
import me.onetwo.growsnap.domain.report.dto.ReportRequest
import me.onetwo.growsnap.domain.report.dto.ReportResponse
import me.onetwo.growsnap.domain.report.model.TargetType
import me.onetwo.growsnap.domain.report.service.ReportService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 신고 컨트롤러
 *
 * 대상(콘텐츠, 댓글, 사용자) 신고 관련 HTTP 요청을 처리합니다.
 *
 * @property reportService 신고 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
class ReportController(
    private val reportService: ReportService
) {

    /**
     * 대상 신고
     *
     * POST /api/v1/reports/{targetType}/{targetId}
     *
     * 사용자가 부적절한 대상(콘텐츠, 댓글, 사용자)을 신고합니다.
     * 로그인한 사용자만 신고할 수 있으며, 중복 신고는 허용되지 않습니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param targetType 신고 대상 타입 (CONTENT, COMMENT, USER)
     * @param targetId 신고 대상 ID
     * @param request 신고 요청 데이터 (신고 타입, 상세 설명)
     * @return 201 Created와 신고 응답
     */
    @PostMapping("/reports/{targetType}/{targetId}")
    fun report(
        principal: Mono<Principal>,
        @PathVariable targetType: TargetType,
        @PathVariable targetId: UUID,
        @Valid @RequestBody request: ReportRequest
    ): Mono<ResponseEntity<ReportResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                reportService.report(userId, targetType, targetId, request)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }
}
