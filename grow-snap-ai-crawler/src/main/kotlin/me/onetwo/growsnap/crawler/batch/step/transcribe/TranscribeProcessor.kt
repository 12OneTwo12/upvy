package me.onetwo.growsnap.crawler.batch.step.transcribe

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import me.onetwo.growsnap.crawler.client.SttClient
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.ContentLanguage
import me.onetwo.growsnap.crawler.domain.JobStatus
import me.onetwo.growsnap.crawler.service.AudioExtractService
import me.onetwo.growsnap.crawler.service.S3Service
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant

/**
 * TranscribeStep Processor
 *
 * S3에서 비디오를 가져와 오디오를 추출하고 STT로 변환합니다.
 */
@Component
class TranscribeProcessor(
    private val sttClient: SttClient,
    private val s3Service: S3Service,
    private val audioExtractService: AudioExtractService,
    @Value("\${ai.stt.provider}") private val sttProvider: String
) : ItemProcessor<AiContentJob, AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(TranscribeProcessor::class.java)
        private val objectMapper = jacksonObjectMapper()
    }

    override fun process(job: AiContentJob): AiContentJob? {
        logger.info("Transcribe 시작: jobId={}, videoId={}", job.id, job.youtubeVideoId)

        if (job.rawVideoS3Key == null) {
            logger.warn("rawVideoS3Key가 없음: jobId={}", job.id)
            return null
        }

        try {
            // 1. S3에서 Presigned URL 생성
            val presignedUrl = s3Service.generatePresignedUrl(job.rawVideoS3Key!!)
            logger.debug("Presigned URL 생성 완료: jobId={}", job.id)

            // 2. 오디오 추출
            val audioPath = "${System.getProperty("java.io.tmpdir")}/audio_${job.id}.ogg"
            audioExtractService.extractAudioFromUrl(presignedUrl.toString(), audioPath)
            logger.debug("오디오 추출 완료: jobId={}, audioPath={}", job.id, audioPath)

            // 3. 오디오를 S3에 업로드 (STT가 URL 기반인 경우)
            val audioS3Key = "temp/audio/${job.youtubeVideoId}.ogg"
            s3Service.upload(audioPath, audioS3Key)
            val audioPresignedUrl = s3Service.generatePresignedUrl(audioS3Key)
            logger.debug("오디오 S3 업로드 완료: jobId={}, s3Key={}", job.id, audioS3Key)

            // 4. STT 변환 (콘텐츠 언어를 전달)
            val contentLanguage = job.language?.let { ContentLanguage.fromCode(it) } ?: ContentLanguage.KO
            val transcriptResult = runBlocking {
                sttClient.transcribe(audioPresignedUrl.toString(), contentLanguage)
            }
            logger.info("STT 변환 완료: jobId={}, language={}, textLength={}, segmentsCount={}",
                job.id, contentLanguage.code, transcriptResult.text.length, transcriptResult.segments.size)

            // 5. 세그먼트를 JSON으로 변환 (LLM에게 전달할 타임스탬프 정보)
            val transcriptSegmentsJson = if (transcriptResult.segments.isNotEmpty()) {
                objectMapper.writeValueAsString(transcriptResult.segments)
            } else {
                null
            }

            // 6. 임시 파일 정리
            cleanupTempFiles(audioPath, audioS3Key)

            // 7. Job 업데이트
            val now = Instant.now()
            return job.copy(
                transcript = transcriptResult.text,
                transcriptSegments = transcriptSegmentsJson,  // 타임스탬프 정보 저장
                sttProvider = sttProvider,
                status = JobStatus.TRANSCRIBED,
                updatedAt = now,
                updatedBy = "SYSTEM"
            )

        } catch (e: Exception) {
            logger.error("Transcribe 실패: jobId={}, error={}", job.id, e.message, e)
            return null
        }
    }

    private fun cleanupTempFiles(audioPath: String, audioS3Key: String) {
        try {
            File(audioPath).delete()
            s3Service.delete(audioS3Key)
            logger.debug("임시 파일 정리 완료")
        } catch (e: Exception) {
            logger.warn("임시 파일 정리 실패", e)
        }
    }
}
