package me.onetwo.growsnap.crawler.batch.step.analyze

import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.AiContentJobRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

/**
 * AnalyzeStep Writer
 *
 * 분석 완료된 Job을 저장합니다.
 */
@Component
class AnalyzeWriter(
    private val aiContentJobRepository: AiContentJobRepository
) : ItemWriter<AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(AnalyzeWriter::class.java)
    }

    override fun write(chunk: Chunk<out AiContentJob>) {
        logger.info("Analyze 결과 저장 시작: count={}", chunk.size())

        chunk.items.forEach { job ->
            try {
                aiContentJobRepository.save(job)
                logger.debug(
                    "Job 저장 완료: jobId={}, status={}, title={}",
                    job.id,
                    job.status,
                    job.generatedTitle
                )
            } catch (e: Exception) {
                logger.error("Job 저장 실패: jobId={}", job.id, e)
            }
        }

        logger.info("Analyze 결과 저장 완료: count={}", chunk.size())
    }
}
