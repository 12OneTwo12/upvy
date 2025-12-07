package me.onetwo.growsnap.crawler.batch.step.review

import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.AiContentJobRepository
import me.onetwo.growsnap.crawler.domain.JobStatus
import org.slf4j.LoggerFactory
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.item.ItemReader
import org.springframework.stereotype.Component

/**
 * ReviewStep Reader
 *
 * EDITED 상태의 Job을 읽어옵니다.
 */
@Component
@StepScope
class ReviewReader(
    private val aiContentJobRepository: AiContentJobRepository
) : ItemReader<AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(ReviewReader::class.java)
    }

    private var jobs: Iterator<AiContentJob>? = null
    private var initialized = false

    override fun read(): AiContentJob? {
        if (!initialized) {
            initialize()
            initialized = true
        }

        return if (jobs?.hasNext() == true) {
            jobs?.next()
        } else {
            null
        }
    }

    private fun initialize() {
        logger.info("EDITED 상태 Job 조회 시작")

        val editedJobs = aiContentJobRepository.findByStatus(JobStatus.EDITED)
        logger.info("EDITED 상태 Job 수: {}", editedJobs.size)

        jobs = editedJobs.iterator()
    }
}
