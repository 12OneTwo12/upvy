package me.onetwo.growsnap.crawler.batch.step.review

import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.AiContentJobRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

/**
 * ReviewStep Writer
 *
 * 검토 완료된 Job을 저장합니다.
 */
@Component
class ReviewWriter(
    private val aiContentJobRepository: AiContentJobRepository
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
                aiContentJobRepository.save(job)

                when (job.status) {
                    me.onetwo.growsnap.crawler.domain.JobStatus.PENDING_APPROVAL -> approvedCount++
                    me.onetwo.growsnap.crawler.domain.JobStatus.REJECTED -> rejectedCount++
                    else -> {}
                }

                logger.debug(
                    "Job 저장 완료: jobId={}, status={}, qualityScore={}",
                    job.id,
                    job.status,
                    job.qualityScore
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
