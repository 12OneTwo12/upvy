package me.onetwo.growsnap.crawler.batch.step.analyze

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import me.onetwo.growsnap.crawler.client.LlmClient
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.JobStatus
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * AnalyzeStep Processor
 *
 * LLM을 사용하여 자막을 분석하고 세그먼트를 추출합니다.
 */
@Component
class AnalyzeProcessor(
    private val llmClient: LlmClient,
    @Value("\${ai.llm.provider}") private val llmProvider: String,
    @Value("\${ai.llm.model}") private val llmModel: String
) : ItemProcessor<AiContentJob, AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(AnalyzeProcessor::class.java)
        private val objectMapper = jacksonObjectMapper()
    }

    override fun process(job: AiContentJob): AiContentJob? {
        logger.info("Analyze 시작: jobId={}, videoId={}", job.id, job.youtubeVideoId)

        if (job.transcript.isNullOrBlank()) {
            logger.warn("transcript가 없음: jobId={}", job.id)
            return null
        }

        try {
            // 1. 핵심 세그먼트 추출
            val segments = runBlocking {
                llmClient.extractKeySegments(job.transcript!!)
            }
            logger.debug("세그먼트 추출 완료: jobId={}, segmentCount={}", job.id, segments.size)

            // 2. 메타데이터 생성
            val metadata = runBlocking {
                llmClient.generateMetadata(job.transcript!!)
            }
            logger.debug(
                "메타데이터 생성 완료: jobId={}, title={}, category={}",
                job.id, metadata.title, metadata.category
            )

            // 3. 태그를 JSON 문자열로 변환
            val tagsJson = objectMapper.writeValueAsString(metadata.tags)

            // 4. Job 업데이트
            val now = Instant.now()
            return job.copy(
                generatedTitle = metadata.title,
                generatedDescription = metadata.description,
                generatedTags = tagsJson,
                category = metadata.category,
                difficulty = metadata.difficulty,
                llmProvider = llmProvider,
                llmModel = llmModel,
                status = JobStatus.ANALYZED,
                updatedAt = now,
                updatedBy = "SYSTEM"
            )

        } catch (e: Exception) {
            logger.error("Analyze 실패: jobId={}, error={}", job.id, e.message, e)
            return null
        }
    }
}
