package me.onetwo.upvy.crawler.batch.step.analyze

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import me.onetwo.upvy.crawler.client.LlmClient
import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.ContentLanguage
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.TranscriptSegment
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

            // 5. 출처 표기 추가 (CC 라이선스 원본 정보) - 콘텐츠 언어에 맞게
            val sourceAttribution = buildSourceAttribution(job, contentLanguage)
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
     * CC 라이선스 원본 YouTube 영상의 정보를 콘텐츠 언어에 맞게 포함합니다.
     */
    private fun buildSourceAttribution(job: AiContentJob, language: ContentLanguage): String? {
        if (job.youtubeVideoId.isBlank()) {
            return null
        }

        val videoUrl = "https://www.youtube.com/watch?v=${job.youtubeVideoId}"
        val titleInfo = job.youtubeTitle?.let { "\"$it\"" } ?: ""

        // 언어별 출처 문자열
        val (sourceLabel, ccDescription, originalTitleLabel, originalLinkLabel, channelLabel) = when (language) {
            ContentLanguage.KO -> SourceLabels(
                sourceLabel = "📌 출처",
                ccDescription = "이 콘텐츠는 Creative Commons 라이선스로 공개된 YouTube 영상을 기반으로 AI에 의해 제작되었습니다.",
                originalTitleLabel = "원본 제목",
                originalLinkLabel = "원본 링크",
                channelLabel = "채널"
            )
            ContentLanguage.EN -> SourceLabels(
                sourceLabel = "📌 Source",
                ccDescription = "This content was created by AI based on a YouTube video published under a Creative Commons license.",
                originalTitleLabel = "Original Title",
                originalLinkLabel = "Original Link",
                channelLabel = "Channel"
            )
            ContentLanguage.JA -> SourceLabels(
                sourceLabel = "📌 出典",
                ccDescription = "このコンテンツはCreative Commonsライセンスで公開されたYouTube動画を基にAIによって制作されました。",
                originalTitleLabel = "元のタイトル",
                originalLinkLabel = "元のリンク",
                channelLabel = "チャンネル"
            )
        }

        // 채널명이 있으면 채널명 사용, 없으면 채널 ID 사용
        val channelName = job.youtubeChannelTitle ?: job.youtubeChannelId
        val channelInfo = channelName?.let { "$channelLabel: $it" } ?: ""

        return buildString {
            append("---\n")
            append("$sourceLabel: $ccDescription\n")
            if (titleInfo.isNotBlank()) {
                append("$originalTitleLabel: $titleInfo\n")
            }
            append("$originalLinkLabel: $videoUrl\n")
            if (channelInfo.isNotBlank()) {
                append(channelInfo)
            }
        }.trim()
    }

    /**
     * 출처 표기용 라벨 데이터 클래스
     */
    private data class SourceLabels(
        val sourceLabel: String,
        val ccDescription: String,
        val originalTitleLabel: String,
        val originalLinkLabel: String,
        val channelLabel: String
    )
}
