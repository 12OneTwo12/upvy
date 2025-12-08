package me.onetwo.upvy.crawler.backoffice.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import me.onetwo.upvy.crawler.backoffice.domain.Category
import me.onetwo.upvy.crawler.backoffice.domain.PendingContent
import me.onetwo.upvy.crawler.backoffice.domain.PendingContentStatus
import me.onetwo.upvy.crawler.backoffice.repository.PendingContentRepository
import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.Difficulty
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.ReviewPriority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.util.*

@DisplayName("PendingContentService 테스트")
class PendingContentServiceTest {

    private lateinit var pendingContentRepository: PendingContentRepository
    private lateinit var service: PendingContentService

    @BeforeEach
    fun setUp() {
        pendingContentRepository = mockk(relaxed = true)
        service = PendingContentService(pendingContentRepository)
    }

    @Nested
    @DisplayName("createFromJob - Job에서 PendingContent 생성")
    inner class CreateFromJobTest {

        @Test
        @DisplayName("AiContentJob에서 PendingContent를 생성한다")
        fun createFromJob_Success() {
            // Given
            val job = AiContentJob(
                id = 1L,
                youtubeVideoId = "abc123",
                youtubeChannelId = "channel123",
                youtubeTitle = "Original YouTube Title",
                status = JobStatus.PENDING_APPROVAL,
                qualityScore = 85,
                rawVideoS3Key = "raw/video.mp4",
                editedVideoS3Key = "edited/video.mp4",
                thumbnailS3Key = "thumbnails/thumb.jpg",
                generatedTitle = "AI Generated Title",
                generatedDescription = "AI Generated Description",
                generatedTags = "[\"kotlin\", \"android\"]",
                category = "PROGRAMMING",
                difficulty = Difficulty.BEGINNER
            )

            val savedContent = slot<PendingContent>()
            every { pendingContentRepository.save(capture(savedContent)) } answers {
                savedContent.captured.apply {
                    // ID 설정 시뮬레이션
                }
            }

            // When
            val result = service.createFromJob(job, ReviewPriority.HIGH)

            // Then
            verify { pendingContentRepository.save(any()) }
            assertThat(savedContent.captured.aiContentJobId).isEqualTo(1L)
            assertThat(savedContent.captured.title).isEqualTo("AI Generated Title")
            assertThat(savedContent.captured.description).isEqualTo("AI Generated Description")
            assertThat(savedContent.captured.category).isEqualTo(Category.PROGRAMMING)
            assertThat(savedContent.captured.difficulty).isEqualTo(Difficulty.BEGINNER)
            assertThat(savedContent.captured.videoS3Key).isEqualTo("edited/video.mp4")
            assertThat(savedContent.captured.qualityScore).isEqualTo(85)
            assertThat(savedContent.captured.reviewPriority).isEqualTo(ReviewPriority.HIGH)
        }

        @Test
        @DisplayName("generatedTitle이 없으면 youtubeTitle을 사용한다")
        fun createFromJob_FallbackToYoutubeTitle() {
            // Given
            val job = AiContentJob(
                id = 1L,
                youtubeVideoId = "abc123",
                youtubeTitle = "YouTube Title",
                status = JobStatus.PENDING_APPROVAL,
                qualityScore = 75,
                rawVideoS3Key = "raw/video.mp4",
                category = "SCIENCE"
            )

            val savedContent = slot<PendingContent>()
            every { pendingContentRepository.save(capture(savedContent)) } answers { savedContent.captured }

            // When
            service.createFromJob(job, ReviewPriority.NORMAL)

            // Then
            assertThat(savedContent.captured.title).isEqualTo("YouTube Title")
        }
    }

    @Nested
    @DisplayName("getPendingContents - 승인 대기 콘텐츠 조회")
    inner class GetPendingContentsTest {

        @Test
        @DisplayName("승인 대기 콘텐츠 목록을 조회한다")
        fun getPendingContents_Success() {
            // Given
            val pageable = PageRequest.of(0, 20)
            val content = createTestPendingContent(1L)
            val page = PageImpl(listOf(content), pageable, 1)

            every {
                pendingContentRepository.findByStatusOrderByPriority(
                    PendingContentStatus.PENDING_REVIEW,
                    pageable
                )
            } returns page

            // When
            val result = service.getPendingContents(pageable)

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].id).isEqualTo(1L)
        }
    }

    @Nested
    @DisplayName("approve - 콘텐츠 승인")
    inner class ApproveTest {

        @Test
        @DisplayName("콘텐츠를 승인하면 상태가 APPROVED로 변경된다")
        fun approve_Success() {
            // Given
            val content = createTestPendingContent(1L)
            every { pendingContentRepository.findById(1L) } returns Optional.of(content)
            every { pendingContentRepository.save(any()) } answers { firstArg() }

            // When
            val result = service.approve(1L, "admin", "published-uuid-123")

            // Then
            assertThat(result.status).isEqualTo(PendingContentStatus.APPROVED)
            assertThat(result.reviewedBy).isEqualTo("admin")
            assertThat(result.publishedContentId).isEqualTo("published-uuid-123")
            assertThat(result.reviewedAt).isNotNull()
        }
    }

    @Nested
    @DisplayName("reject - 콘텐츠 거절")
    inner class RejectTest {

        @Test
        @DisplayName("콘텐츠를 거절하면 상태가 REJECTED로 변경되고 사유가 저장된다")
        fun reject_Success() {
            // Given
            val content = createTestPendingContent(1L)
            every { pendingContentRepository.findById(1L) } returns Optional.of(content)
            every { pendingContentRepository.save(any()) } answers { firstArg() }

            // When
            val result = service.reject(1L, "admin", "Quality is below standards")

            // Then
            assertThat(result.status).isEqualTo(PendingContentStatus.REJECTED)
            assertThat(result.reviewedBy).isEqualTo("admin")
            assertThat(result.rejectionReason).isEqualTo("Quality is below standards")
            assertThat(result.reviewedAt).isNotNull()
        }
    }

    @Nested
    @DisplayName("updateMetadata - 메타데이터 수정")
    inner class UpdateMetadataTest {

        @Test
        @DisplayName("콘텐츠 메타데이터를 수정한다")
        fun updateMetadata_Success() {
            // Given
            val content = createTestPendingContent(1L)
            every { pendingContentRepository.findById(1L) } returns Optional.of(content)
            every { pendingContentRepository.save(any()) } answers { firstArg() }

            // When
            val result = service.updateMetadata(
                id = 1L,
                title = "Updated Title",
                description = "Updated Description",
                category = Category.SCIENCE,
                difficulty = Difficulty.INTERMEDIATE,
                tags = listOf("science", "education"),
                updatedBy = "admin"
            )

            // Then
            assertThat(result.title).isEqualTo("Updated Title")
            assertThat(result.description).isEqualTo("Updated Description")
            assertThat(result.category).isEqualTo(Category.SCIENCE)
            assertThat(result.difficulty).isEqualTo(Difficulty.INTERMEDIATE)
            assertThat(result.updatedBy).isEqualTo("admin")
        }
    }

    @Nested
    @DisplayName("getDashboardStats - 대시보드 통계")
    inner class GetDashboardStatsTest {

        @Test
        @DisplayName("대시보드 통계를 조회한다")
        fun getDashboardStats_Success() {
            // Given
            every { pendingContentRepository.countCreatedToday(any()) } returns 5
            every {
                pendingContentRepository.countByStatusAndDeletedAtIsNull(PendingContentStatus.PENDING_REVIEW)
            } returns 10
            every {
                pendingContentRepository.countReviewedSince(PendingContentStatus.APPROVED, any())
            } returns 20
            every {
                pendingContentRepository.countReviewedSince(PendingContentStatus.REJECTED, any())
            } returns 3
            every { pendingContentRepository.getAverageQualityScore() } returns 82.5
            every {
                pendingContentRepository.countByReviewPriorityAndStatusAndDeletedAtIsNull(
                    ReviewPriority.HIGH,
                    PendingContentStatus.PENDING_REVIEW
                )
            } returns 2

            // When
            val stats = service.getDashboardStats()

            // Then
            assertThat(stats.todayCreated).isEqualTo(5)
            assertThat(stats.pendingReview).isEqualTo(10)
            assertThat(stats.approvedThisWeek).isEqualTo(20)
            assertThat(stats.rejectedThisWeek).isEqualTo(3)
            assertThat(stats.averageQualityScore).isEqualTo(82.5)
            assertThat(stats.highPriorityCount).isEqualTo(2)
        }
    }

    private fun createTestPendingContent(id: Long): PendingContent {
        return PendingContent(
            id = id,
            aiContentJobId = 100L,
            title = "Test Title",
            description = "Test Description",
            category = Category.PROGRAMMING,
            difficulty = Difficulty.BEGINNER,
            tags = "[\"kotlin\", \"spring\"]",
            videoS3Key = "videos/test.mp4",
            thumbnailS3Key = "thumbnails/test.jpg",
            qualityScore = 85,
            reviewPriority = ReviewPriority.HIGH,
            status = PendingContentStatus.PENDING_REVIEW,
            createdAt = Instant.now()
        )
    }
}
