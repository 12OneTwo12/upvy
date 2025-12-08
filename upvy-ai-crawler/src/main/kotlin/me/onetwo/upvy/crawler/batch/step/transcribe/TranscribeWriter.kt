package me.onetwo.upvy.crawler.batch.step.transcribe

import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.AiContentJobRepository
import org.slf4j.LoggerFactory
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component

/**
 * TranscribeStep Writer
 *
 * 변환 완료된 Job을 저장합니다.
 */
@Component
class TranscribeWriter(
    private val aiContentJobRepository: AiContentJobRepository
) : ItemWriter<AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(TranscribeWriter::class.java)
    }

    override fun write(chunk: Chunk<out AiContentJob>) {
        logger.info("Transcribe 결과 저장 시작: count={}", chunk.size())

        chunk.items.forEach { job ->
            try {
                aiContentJobRepository.save(job)
                logger.debug(
                    "Job 저장 완료: jobId={}, status={}, transcriptLength={}",
                    job.id,
                    job.status,
                    job.transcript?.length
                )
            } catch (e: Exception) {
                logger.error("Job 저장 실패: jobId={}", job.id, e)
            }
        }

        logger.info("Transcribe 결과 저장 완료: count={}", chunk.size())
    }
}
