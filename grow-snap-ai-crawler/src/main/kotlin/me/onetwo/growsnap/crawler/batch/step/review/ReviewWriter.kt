package me.onetwo.growsnap.crawler.batch.step.review

import me.onetwo.growsnap.crawler.backoffice.service.PendingContentService
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.AiContentJobRepository
import me.onetwo.growsnap.crawler.domain.JobStatus
import me.onetwo.growsnap.crawler.service.QualityScoreService
import org.slf4j.LoggerFactory
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

/**
 * ReviewStep Writer
 *
 * 검토 완료된 Job을 저장하고, 승인 대기열에 추가합니다.
 */
@Component
class ReviewWriter(
    private val aiContentJobRepository: AiContentJobRepository,
    private val pendingContentService: PendingContentService,
    private val qualityScoreService: QualityScoreService
) : ItemWriter<AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(ReviewWriter::class.java)
    }

    override fun write(chunk: Chunk<out AiContentJob>) {
        logger.info("Review 결과 저장 시작: count={}", chunk.size())

        var approvedCount = 0
        var rejectedCount = 0

        chunk.items.forEach { job ->
            try {
                // 1. Job 저장
                val savedJob = aiContentJobRepository.save(job)

                // 2. 승인 대기 상태면 pending_contents에도 INSERT
                when (savedJob.status) {
                    JobStatus.PENDING_APPROVAL -> {
                        approvedCount++
                        val reviewPriority = qualityScoreService.determineReviewPriority(
                            savedJob.qualityScore ?: 0
                        )
                        pendingContentService.createFromJob(savedJob, reviewPriority)
                        logger.info(
                            "승인 대기열에 추가: jobId={}, priority={}",
                            savedJob.id, reviewPriority
                        )
                    }
                    JobStatus.REJECTED -> rejectedCount++
                    else -> {}
                }

                logger.debug(
                    "Job 저장 완료: jobId={}, status={}, qualityScore={}",
                    savedJob.id,
                    savedJob.status,
                    savedJob.qualityScore
                )
            } catch (e: Exception) {
                logger.error("Job 저장 실패: jobId={}", job.id, e)
            }
        }

        logger.info(
            "Review 결과 저장 완료: total={}, pendingApproval={}, rejected={}",
            chunk.size(), approvedCount, rejectedCount
        )
    }
}
