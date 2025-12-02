package me.onetwo.growsnap.domain.report.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.report.dto.ReportRequest
import me.onetwo.growsnap.domain.report.dto.ReportResponse
import me.onetwo.growsnap.domain.report.exception.ReportException
import me.onetwo.growsnap.domain.report.model.ReportStatus
import me.onetwo.growsnap.domain.report.model.ReportType
import me.onetwo.growsnap.domain.report.model.TargetType
import me.onetwo.growsnap.domain.report.service.ReportService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.config.RestDocsConfiguration
import me.onetwo.growsnap.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 신고 Controller 테스트
 *
 * ReportController의 HTTP 요청 처리를 테스트하고 REST Docs 문서를 생성합니다.
 */
@WebFluxTest(ReportController::class)
@Import(RestDocsConfiguration::class, TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("신고 Controller 테스트")
class ReportControllerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var reportService: ReportService

    @Nested
    @DisplayName("POST /api/v1/reports/{targetType}/{targetId} - 대상 신고")
    inner class ReportTarget {

        @Test
        @DisplayName("유효한 콘텐츠 신고 요청 시, 201 Created와 신고 응답을 반환한다")
        fun report_WithValidContentRequest_ReturnsCreated() {
            // Given: 콘텐츠 신고 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.SPAM,
                description = "스팸 콘텐츠입니다"
            )
            val response = ReportResponse(
                id = 1L,
                reporterId = userId.toString(),
                targetType = TargetType.CONTENT,
                targetId = contentId.toString(),
                reportType = ReportType.SPAM,
                description = "스팸 콘텐츠입니다",
                status = ReportStatus.PENDING,
                createdAt = Instant.now()
            )

            every { reportService.report(userId, TargetType.CONTENT, contentId, request) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/reports/{targetType}/{targetId}", TargetType.CONTENT.name.lowercase(), contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(1)
                .jsonPath("$.reporterId").isEqualTo(userId.toString())
                .jsonPath("$.targetType").isEqualTo("CONTENT")
                .jsonPath("$.targetId").isEqualTo(contentId.toString())
                .jsonPath("$.reportType").isEqualTo("SPAM")
                .jsonPath("$.description").isEqualTo("스팸 콘텐츠입니다")
                .jsonPath("$.status").isEqualTo("PENDING")
                .consumeWith(
                    document(
                        "report-content-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("targetType").description("신고 대상 타입 (CONTENT, COMMENT, USER)"),
                            parameterWithName("targetId").description("신고 대상 ID")
                        ),
                        requestFields(
                            fieldWithPath("reportType").description("신고 타입 (OFF_TOPIC, SPAM, INAPPROPRIATE_CONTENT, COPYRIGHT, HARASSMENT, HATE_SPEECH, MISINFORMATION, OTHER)"),
                            fieldWithPath("description").description("신고 상세 설명 (최대 500자, 선택)").optional()
                        ),
                        responseFields(
                            fieldWithPath("id").description("신고 ID"),
                            fieldWithPath("reporterId").description("신고한 사용자 ID"),
                            fieldWithPath("targetType").description("신고 대상 타입"),
                            fieldWithPath("targetId").description("신고 대상 ID"),
                            fieldWithPath("reportType").description("신고 타입"),
                            fieldWithPath("description").description("신고 상세 설명"),
                            fieldWithPath("status").description("신고 처리 상태 (PENDING, APPROVED, REJECTED)"),
                            fieldWithPath("createdAt").description("신고 생성 시각")
                        )
                    )
                )

            verify(exactly = 1) { reportService.report(userId, TargetType.CONTENT, contentId, request) }
        }

        @Test
        @DisplayName("유효한 댓글 신고 요청 시, 201 Created와 신고 응답을 반환한다")
        fun report_WithValidCommentRequest_ReturnsCreated() {
            // Given: 댓글 신고 요청
            val userId = UUID.randomUUID()
            val commentId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.HARASSMENT,
                description = "괴롭힘 댓글입니다"
            )
            val response = ReportResponse(
                id = 2L,
                reporterId = userId.toString(),
                targetType = TargetType.COMMENT,
                targetId = commentId.toString(),
                reportType = ReportType.HARASSMENT,
                description = "괴롭힘 댓글입니다",
                status = ReportStatus.PENDING,
                createdAt = Instant.now()
            )

            every { reportService.report(userId, TargetType.COMMENT, commentId, request) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/reports/{targetType}/{targetId}", TargetType.COMMENT.name.lowercase(), commentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(2)
                .jsonPath("$.reporterId").isEqualTo(userId.toString())
                .jsonPath("$.targetType").isEqualTo("COMMENT")
                .jsonPath("$.targetId").isEqualTo(commentId.toString())
                .jsonPath("$.reportType").isEqualTo("HARASSMENT")
                .jsonPath("$.description").isEqualTo("괴롭힘 댓글입니다")
                .jsonPath("$.status").isEqualTo("PENDING")
                .consumeWith(
                    document(
                        "report-comment-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("targetType").description("신고 대상 타입 (CONTENT, COMMENT, USER)"),
                            parameterWithName("targetId").description("신고 대상 ID")
                        ),
                        requestFields(
                            fieldWithPath("reportType").description("신고 타입 (OFF_TOPIC, SPAM, INAPPROPRIATE_CONTENT, COPYRIGHT, HARASSMENT, HATE_SPEECH, MISINFORMATION, OTHER)"),
                            fieldWithPath("description").description("신고 상세 설명 (최대 500자, 선택)").optional()
                        ),
                        responseFields(
                            fieldWithPath("id").description("신고 ID"),
                            fieldWithPath("reporterId").description("신고한 사용자 ID"),
                            fieldWithPath("targetType").description("신고 대상 타입"),
                            fieldWithPath("targetId").description("신고 대상 ID"),
                            fieldWithPath("reportType").description("신고 타입"),
                            fieldWithPath("description").description("신고 상세 설명"),
                            fieldWithPath("status").description("신고 처리 상태 (PENDING, APPROVED, REJECTED)"),
                            fieldWithPath("createdAt").description("신고 생성 시각")
                        )
                    )
                )

            verify(exactly = 1) { reportService.report(userId, TargetType.COMMENT, commentId, request) }
        }

        @Test
        @DisplayName("유효한 사용자 신고 요청 시, 201 Created와 신고 응답을 반환한다")
        fun report_WithValidUserRequest_ReturnsCreated() {
            // Given: 사용자 신고 요청
            val userId = UUID.randomUUID()
            val targetUserId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.HATE_SPEECH,
                description = "혐오 발언 사용자입니다"
            )
            val response = ReportResponse(
                id = 3L,
                reporterId = userId.toString(),
                targetType = TargetType.USER,
                targetId = targetUserId.toString(),
                reportType = ReportType.HATE_SPEECH,
                description = "혐오 발언 사용자입니다",
                status = ReportStatus.PENDING,
                createdAt = Instant.now()
            )

            every { reportService.report(userId, TargetType.USER, targetUserId, request) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/reports/{targetType}/{targetId}", TargetType.USER.name.lowercase(), targetUserId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(3)
                .jsonPath("$.reporterId").isEqualTo(userId.toString())
                .jsonPath("$.targetType").isEqualTo("USER")
                .jsonPath("$.targetId").isEqualTo(targetUserId.toString())
                .jsonPath("$.reportType").isEqualTo("HATE_SPEECH")
                .jsonPath("$.description").isEqualTo("혐오 발언 사용자입니다")
                .jsonPath("$.status").isEqualTo("PENDING")
                .consumeWith(
                    document(
                        "report-user-create",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("targetType").description("신고 대상 타입 (CONTENT, COMMENT, USER)"),
                            parameterWithName("targetId").description("신고 대상 ID")
                        ),
                        requestFields(
                            fieldWithPath("reportType").description("신고 타입 (OFF_TOPIC, SPAM, INAPPROPRIATE_CONTENT, COPYRIGHT, HARASSMENT, HATE_SPEECH, MISINFORMATION, OTHER)"),
                            fieldWithPath("description").description("신고 상세 설명 (최대 500자, 선택)").optional()
                        ),
                        responseFields(
                            fieldWithPath("id").description("신고 ID"),
                            fieldWithPath("reporterId").description("신고한 사용자 ID"),
                            fieldWithPath("targetType").description("신고 대상 타입"),
                            fieldWithPath("targetId").description("신고 대상 ID"),
                            fieldWithPath("reportType").description("신고 타입"),
                            fieldWithPath("description").description("신고 상세 설명"),
                            fieldWithPath("status").description("신고 처리 상태 (PENDING, APPROVED, REJECTED)"),
                            fieldWithPath("createdAt").description("신고 생성 시각")
                        )
                    )
                )

            verify(exactly = 1) { reportService.report(userId, TargetType.USER, targetUserId, request) }
        }

        @Test
        @DisplayName("중복 신고 시, 409 Conflict를 반환한다")
        fun report_WhenDuplicate_ReturnsConflict() {
            // Given: 이미 신고한 콘텐츠
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.SPAM,
                description = null
            )

            every { reportService.report(userId, TargetType.CONTENT, contentId, request) } returns
                Mono.error(ReportException.DuplicateReportException(userId.toString(), contentId.toString(), "CONTENT"))

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/reports/{targetType}/{targetId}", TargetType.CONTENT.name.lowercase(), contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("DUPLICATE_REPORT")
                .consumeWith(
                    document(
                        "report-duplicate-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("targetType").description("신고 대상 타입 (CONTENT, COMMENT, USER)"),
                            parameterWithName("targetId").description("신고 대상 ID")
                        ),
                        requestFields(
                            fieldWithPath("reportType").description("신고 타입"),
                            fieldWithPath("description").description("신고 상세 설명").optional()
                        ),
                        responseFields(
                            fieldWithPath("timestamp").description("오류 발생 시각"),
                            fieldWithPath("status").description("HTTP 상태 코드"),
                            fieldWithPath("error").description("HTTP 상태 메시지"),
                            fieldWithPath("message").description("에러 메시지"),
                            fieldWithPath("path").description("요청 경로"),
                            fieldWithPath("code").description("에러 코드")
                        )
                    )
                )

            verify(exactly = 1) { reportService.report(userId, TargetType.CONTENT, contentId, request) }
        }

        @Test
        @DisplayName("신고 타입 누락 시, 400 Bad Request를 반환한다")
        fun report_WithoutReportType_ReturnsBadRequest() {
            // Given: 신고 타입이 없는 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val invalidRequest = mapOf(
                "reportType" to null,
                "description" to "설명만 있는 요청"
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/reports/{targetType}/{targetId}", TargetType.CONTENT.name.lowercase(), contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .consumeWith(
                    document(
                        "report-validation-error",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        pathParameters(
                            parameterWithName("targetType").description("신고 대상 타입"),
                            parameterWithName("targetId").description("신고 대상 ID")
                        ),
                        responseFields(
                            fieldWithPath("timestamp").description("오류 발생 시각"),
                            fieldWithPath("status").description("HTTP 상태 코드"),
                            fieldWithPath("error").description("HTTP 상태 메시지"),
                            fieldWithPath("message").description("검증 오류 메시지"),
                            fieldWithPath("path").description("요청 경로"),
                            fieldWithPath("code").description("에러 코드")
                        )
                    )
                )

            verify(exactly = 0) { reportService.report(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("설명 없이 신고 시, 201 Created를 반환한다")
        fun report_WithoutDescription_ReturnsCreated() {
            // Given: 설명 없는 신고 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.SPAM,
                description = null
            )
            val response = ReportResponse(
                id = 4L,
                reporterId = userId.toString(),
                targetType = TargetType.CONTENT,
                targetId = contentId.toString(),
                reportType = ReportType.SPAM,
                description = null,
                status = ReportStatus.PENDING,
                createdAt = Instant.now()
            )

            every { reportService.report(userId, TargetType.CONTENT, contentId, request) } returns Mono.just(response)

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(userId))
                .post()
                .uri("${ApiPaths.API_V1}/reports/{targetType}/{targetId}", TargetType.CONTENT.name.lowercase(), contentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(4)
                .jsonPath("$.reporterId").isEqualTo(userId.toString())
                .jsonPath("$.targetType").isEqualTo("CONTENT")
                .jsonPath("$.targetId").isEqualTo(contentId.toString())
                .jsonPath("$.reportType").isEqualTo("SPAM")
                .jsonPath("$.status").isEqualTo("PENDING")

            verify(exactly = 1) { reportService.report(userId, TargetType.CONTENT, contentId, request) }
        }
    }
}
