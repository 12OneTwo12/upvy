package me.onetwo.growsnap.crawler.batch.step.analyze

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import me.onetwo.growsnap.crawler.client.LlmClient
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.ContentLanguage
import me.onetwo.growsnap.crawler.domain.JobStatus
import me.onetwo.growsnap.crawler.domain.TranscriptSegment
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
            // 1. 타임스탬프 포함 형식으로 transcript 변환 (LLM이 정확한 시간을 알 수 있도록)
            val formattedTranscript = formatTranscriptWithTimestamps(job)
            logger.debug("타임스탬프 포함 transcript 생성: jobId={}, length={}", job.id, formattedTranscript.length)

            // 2. 핵심 세그먼트 추출 (타임스탬프 정보 포함)
            val segments = runBlocking {
                llmClient.extractKeySegments(formattedTranscript)
            }
            logger.debug("세그먼트 추출 완료: jobId={}, segmentCount={}", job.id, segments.size)

            // 세그먼트 상세 로깅
            segments.forEachIndexed { index, seg ->
                logger.info("세그먼트 #{}: {}ms ~ {}ms, title={}",
                    index + 1, seg.startTimeMs, seg.endTimeMs, seg.title)
            }

            // 2. 메타데이터 생성 (콘텐츠 언어로)
            val contentLanguage = job.language?.let { ContentLanguage.fromCode(it) } ?: ContentLanguage.KO
            val metadata = runBlocking {
                llmClient.generateMetadata(job.transcript!!, contentLanguage)
            }
            logger.debug(
                "메타데이터 생성 완료: jobId={}, title={}, category={}, language={}",
                job.id, metadata.title, metadata.category, contentLanguage.code
            )

            // 3. 태그를 JSON 문자열로 변환
            val tagsJson = objectMapper.writeValueAsString(metadata.tags)

            // 4. 세그먼트를 JSON 문자열로 변환
            val segmentsJson = objectMapper.writeValueAsString(segments)

            // 5. 출처 표기 추가 (CC 라이선스 원본 정보)
            val sourceAttribution = buildSourceAttribution(job)
            val descriptionWithSource = if (sourceAttribution != null) {
                "${metadata.description}\n\n$sourceAttribution"
            } else {
                metadata.description
            }

            // 6. Job 업데이트
            val now = Instant.now()
            return job.copy(
                generatedTitle = metadata.title,
                generatedDescription = descriptionWithSource,
                generatedTags = tagsJson,
                segments = segmentsJson,  // 세그먼트 저장
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

    /**
     * STT 세그먼트의 타임스탬프를 포함한 형식으로 transcript 변환
     * LLM이 정확한 시간 정보를 알 수 있도록 함
     *
     * 형식 예시:
     * [00:00:30 - 00:00:45] 안녕하세요 오늘은...
     * [00:00:45 - 00:01:00] 이 주제에 대해...
     */
    private fun formatTranscriptWithTimestamps(job: AiContentJob): String {
        // transcriptSegments가 없으면 원본 transcript 반환
        if (job.transcriptSegments.isNullOrBlank()) {
            logger.debug("transcriptSegments 없음, 원본 transcript 사용")
            return job.transcript!!
        }

        return try {
            val segments: List<TranscriptSegment> = objectMapper.readValue(job.transcriptSegments!!)

            if (segments.isEmpty()) {
                logger.debug("세그먼트 비어있음, 원본 transcript 사용")
                return job.transcript!!
            }

            val formatted = segments.joinToString("\n") { segment ->
                val startTime = formatTimeMs(segment.startTimeMs)
                val endTime = formatTimeMs(segment.endTimeMs)
                "[$startTime - $endTime] ${segment.text}"
            }

            logger.info("타임스탬프 포함 transcript 생성 완료: {} 세그먼트", segments.size)
            formatted

        } catch (e: Exception) {
            logger.warn("transcriptSegments 파싱 실패, 원본 사용: {}", e.message)
            job.transcript!!
        }
    }

    /**
     * 밀리초를 HH:MM:SS 형식으로 변환
     */
    private fun formatTimeMs(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * 출처 표기 문자열 생성
     *
     * CC 라이선스 원본 YouTube 영상의 정보를 포함합니다.
     */
    private fun buildSourceAttribution(job: AiContentJob): String? {
        if (job.youtubeVideoId.isBlank()) {
            return null
        }

        val videoUrl = "https://www.youtube.com/watch?v=${job.youtubeVideoId}"
        val channelInfo = job.youtubeChannelId?.let { "채널: $it" } ?: ""
        val titleInfo = job.youtubeTitle?.let { "\"$it\"" } ?: ""

        return buildString {
            append("---\n")
            append("📌 출처: 이 콘텐츠는 Creative Commons 라이선스로 공개된 YouTube 영상을 기반으로 제작되었습니다.\n")
            if (titleInfo.isNotBlank()) {
                append("원본 제목: $titleInfo\n")
            }
            append("원본 링크: $videoUrl\n")
            if (channelInfo.isNotBlank()) {
                append(channelInfo)
            }
        }.trim()
    }
}
