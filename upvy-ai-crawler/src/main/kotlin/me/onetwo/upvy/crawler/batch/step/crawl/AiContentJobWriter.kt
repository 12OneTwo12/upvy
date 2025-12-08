package me.onetwo.upvy.crawler.batch.step.crawl

import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.AiContentJobRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

/**
 * AiContentJob 저장 Writer
 *
 * 처리된 비디오 정보를 데이터베이스에 저장합니다.
 */
@Component
class AiContentJobWriter(
    private val aiContentJobRepository: AiContentJobRepository
) : ItemWriter<AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(AiContentJobWriter::class.java)
    }

    override fun write(chunk: Chunk<out AiContentJob>) {
        logger.info("Job 저장 시작: count={}", chunk.size())

        var successCount = 0
        var failCount = 0

        chunk.items.forEach { job ->
            try {
                aiContentJobRepository.save(job)
                successCount++
                logger.debug(
                    "Job 저장 완료: jobId={}, videoId={}, status={}",
                    job.id,
                    job.youtubeVideoId,
                    job.status
                )
            } catch (e: Exception) {
                failCount++
                logger.error(
                    "Job 저장 실패: videoId={}, error={}",
                    job.youtubeVideoId,
                    e.message,
                    e
                )
            }
        }

        logger.info("Job 저장 완료: success={}, fail={}", successCount, failCount)
    }
}
