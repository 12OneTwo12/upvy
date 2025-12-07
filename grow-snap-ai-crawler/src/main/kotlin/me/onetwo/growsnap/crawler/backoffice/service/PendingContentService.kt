package me.onetwo.growsnap.crawler.backoffice.service

import me.onetwo.growsnap.crawler.backoffice.domain.Category
import me.onetwo.growsnap.crawler.backoffice.domain.PendingContent
import me.onetwo.growsnap.crawler.backoffice.domain.PendingContentStatus
import me.onetwo.growsnap.crawler.backoffice.repository.PendingContentRepository
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.Difficulty
import me.onetwo.growsnap.crawler.domain.ReviewPriority
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Service
@Transactional(readOnly = true)
class PendingContentService(
    private val pendingContentRepository: PendingContentRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PendingContentService::class.java)
    }

    /**
     * AI 콘텐츠 Job에서 승인 대기 콘텐츠 생성
     */
    @Transactional
    fun createFromJob(job: AiContentJob, reviewPriority: ReviewPriority): PendingContent {
        logger.info("승인 대기 콘텐츠 생성: jobId={}", job.id)

        val pendingContent = PendingContent(
            aiContentJobId = job.id!!,
            title = job.generatedTitle ?: job.youtubeTitle ?: "제목 없음",
            description = job.generatedDescription,
            category = Category.fromString(job.category),
            difficulty = job.difficulty?.let {
                when (it) {
                    Difficulty.BEGINNER -> Difficulty.BEGINNER
                    Difficulty.INTERMEDIATE -> Difficulty.INTERMEDIATE
                    Difficulty.ADVANCED -> Difficulty.ADVANCED
                }
            },
            tags = job.generatedTags,
            videoS3Key = job.editedVideoS3Key ?: job.rawVideoS3Key ?: "",
            thumbnailS3Key = job.thumbnailS3Key,
            youtubeVideoId = job.youtubeVideoId,
            youtubeTitle = job.youtubeTitle,
            youtubeChannel = job.youtubeChannelId,
            qualityScore = job.qualityScore ?: 0,
            language = job.language ?: "ko",  // 콘텐츠 언어
            reviewPriority = reviewPriority
        )

        return pendingContentRepository.save(pendingContent).also {
            logger.info("승인 대기 콘텐츠 저장 완료: id={}, jobId={}", it.id, job.id)
        }
    }

    /**
     * 승인 대기 콘텐츠 목록 조회
     */
    fun getPendingContents(pageable: Pageable): Page<PendingContent> {
        return pendingContentRepository.findByStatusOrderByPriority(
            PendingContentStatus.PENDING_REVIEW,
            pageable
        )
    }

    /**
     * 상태별 콘텐츠 목록 조회
     */
    fun getContentsByStatus(status: PendingContentStatus, pageable: Pageable): Page<PendingContent> {
        return pendingContentRepository.findByStatusOrderByPriority(status, pageable)
    }

    /**
     * 콘텐츠 상세 조회
     */
    fun getById(id: Long): PendingContent? {
        return pendingContentRepository.findById(id).orElse(null)
    }

    /**
     * 콘텐츠 승인
     */
    @Transactional
    fun approve(id: Long, reviewerUsername: String, publishedContentId: String): PendingContent {
        val content = pendingContentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("콘텐츠를 찾을 수 없습니다: id=$id") }

        content.approve(reviewerUsername, publishedContentId)

        return pendingContentRepository.save(content).also {
            logger.info("콘텐츠 승인 완료: id={}, publishedContentId={}", id, publishedContentId)
        }
    }

    /**
     * 콘텐츠 거절
     */
    @Transactional
    fun reject(id: Long, reviewerUsername: String, reason: String): PendingContent {
        val content = pendingContentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("콘텐츠를 찾을 수 없습니다: id=$id") }

        content.reject(reviewerUsername, reason)

        return pendingContentRepository.save(content).also {
            logger.info("콘텐츠 거절 완료: id={}, reason={}", id, reason)
        }
    }

    /**
     * 메타데이터 수정
     */
    @Transactional
    fun updateMetadata(
        id: Long,
        title: String,
        description: String?,
        category: Category,
        difficulty: Difficulty?,
        tags: List<String>,
        updatedBy: String
    ): PendingContent {
        val content = pendingContentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("콘텐츠를 찾을 수 없습니다: id=$id") }

        content.updateMetadata(title, description, category, difficulty, tags, updatedBy)

        return pendingContentRepository.save(content)
    }

    /**
     * 대시보드 통계
     */
    fun getDashboardStats(): DashboardStats {
        val today = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
        val weekAgo = today.minusSeconds(7 * 24 * 60 * 60)

        return DashboardStats(
            todayCreated = pendingContentRepository.countCreatedToday(today),
            pendingReview = pendingContentRepository.countByStatusAndDeletedAtIsNull(PendingContentStatus.PENDING_REVIEW),
            approvedThisWeek = pendingContentRepository.countReviewedSince(PendingContentStatus.APPROVED, weekAgo),
            rejectedThisWeek = pendingContentRepository.countReviewedSince(PendingContentStatus.REJECTED, weekAgo),
            averageQualityScore = pendingContentRepository.getAverageQualityScore() ?: 0.0,
            highPriorityCount = pendingContentRepository.countByReviewPriorityAndStatusAndDeletedAtIsNull(
                ReviewPriority.HIGH,
                PendingContentStatus.PENDING_REVIEW
            )
        )
    }

    /**
     * 카테고리별 통계
     */
    fun getCategoryStats(): Map<String, Long> {
        return pendingContentRepository.countByCategory()
            .associate { (category, count) -> (category as Category).displayName to (count as Long) }
    }
}

/**
 * 대시보드 통계 데이터
 */
data class DashboardStats(
    val todayCreated: Long,
    val pendingReview: Long,
    val approvedThisWeek: Long,
    val rejectedThisWeek: Long,
    val averageQualityScore: Double,
    val highPriorityCount: Long
)
