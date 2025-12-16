package me.onetwo.upvy.domain.report.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.repository.ContentMetadataRepository
import me.onetwo.upvy.domain.report.dto.ReportRequest
import me.onetwo.upvy.domain.report.exception.ReportException
import me.onetwo.upvy.domain.report.model.Report
import me.onetwo.upvy.domain.report.model.ReportStatus
import me.onetwo.upvy.domain.report.model.ReportType
import me.onetwo.upvy.domain.report.model.TargetType
import me.onetwo.upvy.domain.report.repository.ReportRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

@ExtendWith(MockKExtension::class)
@DisplayName("ReportService 단위 테스트")
class ReportServiceTest : BaseReactiveTest {

    private val reportRepository: ReportRepository = mockk()
    private val contentMetadataRepository: ContentMetadataRepository = mockk()
    private val reportService: ReportService = ReportServiceImpl(reportRepository, contentMetadataRepository)

    @Nested
    @DisplayName("report - 대상 신고")
    inner class Report {

        @Test
        @DisplayName("유효한 콘텐츠 신고 요청 시, 신고를 저장하고 응답을 반환한다")
        fun report_WithValidContentRequest_SavesAndReturnsReport() {
            // Given
            val reporterId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.SPAM,
                description = "스팸 콘텐츠입니다"
            )
            val savedReport = me.onetwo.upvy.domain.report.model.Report(
                id = 1L,
                reporterId = reporterId,
                targetType = TargetType.CONTENT,
                targetId = contentId,
                reportType = ReportType.SPAM,
                description = "스팸 콘텐츠입니다",
                status = ReportStatus.PENDING,
                createdAt = Instant.now(),
                createdBy = reporterId.toString(),
                updatedAt = Instant.now(),
                updatedBy = reporterId.toString()
            )

            every { reportRepository.exists(reporterId, TargetType.CONTENT, contentId) } returns Mono.just(false)
            every { reportRepository.save(reporterId, TargetType.CONTENT, contentId, ReportType.SPAM, "스팸 콘텐츠입니다") } returns Mono.just(savedReport)
            every { contentMetadataRepository.findCategoryByContentId(contentId) } returns Mono.just(Category.PROGRAMMING)

            // When
            val result = reportService.report(reporterId, TargetType.CONTENT, contentId, request)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(1L, response.id)
                    assertEquals(reporterId.toString(), response.reporterId)
                    assertEquals(TargetType.CONTENT, response.targetType)
                    assertEquals(contentId.toString(), response.targetId)
                    assertEquals(ReportType.SPAM, response.reportType)
                    assertEquals(false, response.isFunCategoryContent)
                }
                .verifyComplete()

            verify(exactly = 1) { reportRepository.exists(reporterId, TargetType.CONTENT, contentId) }
            verify(exactly = 1) { reportRepository.save(reporterId, TargetType.CONTENT, contentId, ReportType.SPAM, "스팸 콘텐츠입니다") }
            verify(exactly = 1) { contentMetadataRepository.findCategoryByContentId(contentId) }
        }

        @Test
        @DisplayName("유효한 댓글 신고 요청 시, 신고를 저장하고 응답을 반환한다")
        fun report_WithValidCommentRequest_SavesAndReturnsReport() {
            // Given
            val reporterId = UUID.randomUUID()
            val commentId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.HARASSMENT,
                description = "괴롭힘 댓글입니다"
            )
            val savedReport = me.onetwo.upvy.domain.report.model.Report(
                id = 2L,
                reporterId = reporterId,
                targetType = TargetType.COMMENT,
                targetId = commentId,
                reportType = ReportType.HARASSMENT,
                description = "괴롭힘 댓글입니다",
                status = ReportStatus.PENDING,
                createdAt = Instant.now(),
                createdBy = reporterId.toString(),
                updatedAt = Instant.now(),
                updatedBy = reporterId.toString()
            )

            every { reportRepository.exists(reporterId, TargetType.COMMENT, commentId) } returns Mono.just(false)
            every { reportRepository.save(reporterId, TargetType.COMMENT, commentId, ReportType.HARASSMENT, "괴롭힘 댓글입니다") } returns Mono.just(savedReport)

            // When
            val result = reportService.report(reporterId, TargetType.COMMENT, commentId, request)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(2L, response.id)
                    assertEquals(reporterId.toString(), response.reporterId)
                    assertEquals(TargetType.COMMENT, response.targetType)
                    assertEquals(commentId.toString(), response.targetId)
                    assertEquals(ReportType.HARASSMENT, response.reportType)
                }
                .verifyComplete()

            verify(exactly = 1) { reportRepository.exists(reporterId, TargetType.COMMENT, commentId) }
            verify(exactly = 1) { reportRepository.save(reporterId, TargetType.COMMENT, commentId, ReportType.HARASSMENT, "괴롭힘 댓글입니다") }
        }

        @Test
        @DisplayName("유효한 사용자 신고 요청 시, 신고를 저장하고 응답을 반환한다")
        fun report_WithValidUserRequest_SavesAndReturnsReport() {
            // Given
            val reporterId = UUID.randomUUID()
            val targetUserId = UUID.randomUUID()
            val request = ReportRequest(
                reportType = ReportType.HATE_SPEECH,
                description = "혐오 발언 사용자입니다"
            )
            val savedReport = me.onetwo.upvy.domain.report.model.Report(
                id = 3L,
                reporterId = reporterId,
                targetType = TargetType.USER,
                targetId = targetUserId,
                reportType = ReportType.HATE_SPEECH,
                description = "혐오 발언 사용자입니다",
                status = ReportStatus.PENDING,
                createdAt = Instant.now(),
                createdBy = reporterId.toString(),
                updatedAt = Instant.now(),
                updatedBy = reporterId.toString()
            )

            every { reportRepository.exists(reporterId, TargetType.USER, targetUserId) } returns Mono.just(false)
            every { reportRepository.save(reporterId, TargetType.USER, targetUserId, ReportType.HATE_SPEECH, "혐오 발언 사용자입니다") } returns Mono.just(savedReport)

            // When
            val result = reportService.report(reporterId, TargetType.USER, targetUserId, request)

            // Then
            StepVerifier.create(result)
                .assertNext { response ->
                    assertEquals(3L, response.id)
                    assertEquals(reporterId.toString(), response.reporterId)
                    assertEquals(TargetType.USER, response.targetType)
                    assertEquals(targetUserId.toString(), response.targetId)
                    assertEquals(ReportType.HATE_SPEECH, response.reportType)
                }
                .verifyComplete()

            verify(exactly = 1) { reportRepository.exists(reporterId, TargetType.USER, targetUserId) }
            verify(exactly = 1) { reportRepository.save(reporterId, TargetType.USER, targetUserId, ReportType.HATE_SPEECH, "혐오 발언 사용자입니다") }
        }

        @Test
        @DisplayName("중복 신고 시, DuplicateReportException을 발생시킨다")
        fun report_WhenDuplicate_ThrowsException() {
            // Given
            val reporterId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ReportRequest(reportType = ReportType.SPAM, description = null)

            every { reportRepository.exists(reporterId, TargetType.CONTENT, contentId) } returns Mono.just(true)

            // When & Then
            StepVerifier.create(reportService.report(reporterId, TargetType.CONTENT, contentId, request))
                .expectError(ReportException.DuplicateReportException::class.java)
                .verify()

            verify(exactly = 1) { reportRepository.exists(reporterId, TargetType.CONTENT, contentId) }
            verify(exactly = 0) { reportRepository.save(any(), any(), any(), any(), any()) }
        }
    }
}
