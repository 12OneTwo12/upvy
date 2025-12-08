package me.onetwo.upvy.crawler.batch.step.review

import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.service.QualityScoreService
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * ReviewStep Processor
 *
 * 품질 점수를 계산하고 승인 대기 또는 거절 상태로 전환합니다.
 */
@Component
class ReviewProcessor(
    private val qualityScoreService: QualityScoreService
) : ItemProcessor<AiContentJob, AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(ReviewProcessor::class.java)
    }

    override fun process(job: AiContentJob): AiContentJob? {
        logger.info("Review 시작: jobId={}, videoId={}", job.id, job.youtubeVideoId)

        try {
            // 1. 품질 점수 계산
            val qualityScore = qualityScoreService.calculateScore(job)
            logger.debug("품질 점수 계산 완료: jobId={}, totalScore={}", job.id, qualityScore.totalScore)

            // 2. 승인 대기열로 보낼지 결정
            val shouldApprove = qualityScoreService.shouldProceedToApproval(qualityScore.totalScore)

            // 3. 검토 우선순위 결정
            val reviewPriority = qualityScoreService.determineReviewPriority(qualityScore.totalScore)

            // 4. 상태 결정
            val newStatus = if (shouldApprove) {
                JobStatus.PENDING_APPROVAL
            } else {
                JobStatus.REJECTED
            }

            logger.info(
                "Review 결과: jobId={}, score={}, status={}, priority={}",
                job.id, qualityScore.totalScore, newStatus, reviewPriority
            )

            // 5. Job 업데이트
            val now = Instant.now()
            return job.copy(
                qualityScore = qualityScore.totalScore,
                status = newStatus,
                updatedAt = now,
                updatedBy = "SYSTEM"
            )

        } catch (e: Exception) {
            logger.error("Review 실패: jobId={}, error={}", job.id, e.message, e)
            return null
        }
    }
}
