package me.onetwo.upvy.crawler.batch.step.edit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.onetwo.upvy.crawler.client.video.FFmpegWrapper
import me.onetwo.upvy.crawler.client.video.SrtGenerator
import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.Segment
import me.onetwo.upvy.crawler.domain.TranscriptSegment
import me.onetwo.upvy.crawler.service.S3Service
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.net.URL
import java.time.Instant

/**
 * EditStep Processor
 *
 * 비디오를 클리핑하고 쇼츠 포맷으로 변환한 후 S3에 업로드합니다.
 */
@Component
class EditProcessor(
    private val ffmpegWrapper: FFmpegWrapper,
    private val s3Service: S3Service,
    private val srtGenerator: SrtGenerator,
    @Value("\${ffmpeg.temp-dir:/tmp/ai-crawler}") private val tempDir: String,
    @Value("\${ffmpeg.font-path:/System/Library/Fonts/Supplemental/Arial Unicode.ttf}") private val fontPath: String,
    @Value("\${s3.prefix.edited-videos}") private val editedVideosPrefix: String,
    @Value("\${s3.prefix.thumbnails}") private val thumbnailsPrefix: String
) : ItemProcessor<AiContentJob, AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(EditProcessor::class.java)
        private val objectMapper = jacksonObjectMapper()
        private const val DEFAULT_CLIP_START_MS = 30000L   // 30초부터 시작 (fallback)
        private const val DEFAULT_CLIP_DURATION_MS = 60000L // 60초 클립 (fallback)
        private const val MAX_CLIP_DURATION_MS = 180000L   // 최대 3분
        private const val MIN_CLIP_DURATION_MS = 30000L    // 최소 30초
    }

    override fun process(job: AiContentJob): AiContentJob? {
        logger.info("Edit 시작: jobId={}, videoId={}", job.id, job.youtubeVideoId)

        if (job.rawVideoS3Key == null) {
            logger.warn("rawVideoS3Key가 없음: jobId={}", job.id)
            return null
        }

        try {
            // 1. S3에서 원본 비디오 다운로드
            val presignedUrl = s3Service.generatePresignedUrl(job.rawVideoS3Key!!)
            val localVideoPath = downloadFromUrl(presignedUrl, "${tempDir}/edit_${job.id}.mp4")
            logger.debug("원본 비디오 다운로드 완료: jobId={}, path={}", job.id, localVideoPath)

            // 2. 비디오 정보 조회
            val videoInfo = ffmpegWrapper.getVideoInfo(localVideoPath)
            logger.debug("비디오 정보: duration={}ms, width={}, height={}",
                videoInfo.durationMs, videoInfo.width, videoInfo.height)

            // 3. 세그먼트 파싱 및 클리핑 시작/종료 시간 계산
            val segments = parseSegments(job.segments)
            val (startMs, endMs) = calculateClipRange(videoInfo.durationMs, segments)
            logger.info("클리핑 범위 결정: jobId={}, start={}ms, end={}ms, duration={}ms",
                job.id, startMs, endMs, endMs - startMs)

            // 4. 비디오 클리핑
            val clippedPath = "${tempDir}/clip_${job.id}.mp4"
            ffmpegWrapper.clip(localVideoPath, clippedPath, startMs, endMs)
            logger.debug("비디오 클리핑 완료: jobId={}, path={}", job.id, clippedPath)

            // 5. 세로 포맷으로 리사이징 (9:16)
            val resizedPath = "${tempDir}/resized_${job.id}.mp4"
            ffmpegWrapper.resizeVertical(clippedPath, resizedPath)
            logger.debug("세로 리사이징 완료: jobId={}, path={}", job.id, resizedPath)

            // 6. 텍스트 오버레이 추가 (NEW)
            val overlayedPath = "${tempDir}/overlayed_${job.id}.mp4"
            val title = job.generatedTitle ?: "교육 콘텐츠"  // fallback

            // 6-1. SRT 파일 생성 (transcriptSegments가 있는 경우)
            var srtPath: String? = null
            if (!job.transcriptSegments.isNullOrBlank()) {
                try {
                    val transcriptSegments: List<TranscriptSegment> = objectMapper.readValue(job.transcriptSegments!!)
                    if (transcriptSegments.isNotEmpty()) {
                        srtPath = "${tempDir}/subtitle_${job.id}.srt"
                        srtGenerator.generateSrtFile(transcriptSegments, srtPath)
                        logger.debug("SRT 파일 생성 완료: jobId={}, path={}", job.id, srtPath)
                    }
                } catch (e: Exception) {
                    logger.warn("SRT 파일 생성 실패: jobId={}, error={}", job.id, e.message)
                }
            }

            // 6-2. 텍스트 오버레이 적용
            ffmpegWrapper.addTextOverlay(resizedPath, overlayedPath, title, srtPath, fontPath)
            logger.debug("텍스트 오버레이 완료: jobId={}, path={}", job.id, overlayedPath)

            // 7. 썸네일 추출 (overlayed 영상에서)
            val thumbnailPath = "${tempDir}/thumb_${job.id}.jpg"
            val thumbnailTimeMs = (endMs - startMs) / 2 // 중간 지점
            ffmpegWrapper.thumbnail(overlayedPath, thumbnailPath, thumbnailTimeMs)
            logger.debug("썸네일 추출 완료: jobId={}, path={}", job.id, thumbnailPath)

            // 8. S3 업로드 (overlayed 영상)
            val editedS3Key = "$editedVideosPrefix/clips/${job.youtubeVideoId}/${job.id}.mp4"
            s3Service.upload(overlayedPath, editedS3Key, publicRead = true)
            logger.debug("편집 비디오 S3 업로드 완료 (public): jobId={}, s3Key={}", job.id, editedS3Key)

            val thumbnailS3Key = "$thumbnailsPrefix/${job.youtubeVideoId}/${job.id}.jpg"
            s3Service.upload(thumbnailPath, thumbnailS3Key, publicRead = true)
            logger.debug("썸네일 S3 업로드 완료 (public): jobId={}, s3Key={}", job.id, thumbnailS3Key)

            // 9. 임시 파일 정리 (SRT 파일 포함)
            val filesToCleanup = mutableListOf(localVideoPath, clippedPath, resizedPath, overlayedPath, thumbnailPath)
            if (srtPath != null) filesToCleanup.add(srtPath)
            cleanupTempFiles(filesToCleanup)

            // 9. Job 업데이트
            val now = Instant.now()
            return job.copy(
                editedVideoS3Key = editedS3Key,
                thumbnailS3Key = thumbnailS3Key,
                status = JobStatus.EDITED,
                updatedAt = now,
                updatedBy = "SYSTEM"
            )

        } catch (e: Exception) {
            logger.error("Edit 실패: jobId={}, error={}", job.id, e.message, e)
            return null
        }
    }

    /**
     * 세그먼트 JSON 파싱
     */
    private fun parseSegments(segmentsJson: String?): List<Segment> {
        if (segmentsJson.isNullOrBlank()) {
            logger.debug("세그먼트 정보 없음, 기본값 사용")
            return emptyList()
        }

        return try {
            objectMapper.readValue<List<Segment>>(segmentsJson)
        } catch (e: Exception) {
            logger.warn("세그먼트 파싱 실패: {}", e.message)
            emptyList()
        }
    }

    /**
     * 클립 범위 계산 - LLM 분석 세그먼트 기반
     */
    private fun calculateClipRange(totalDurationMs: Long, segments: List<Segment>): Pair<Long, Long> {
        // 비디오가 짧으면 전체 사용
        if (totalDurationMs <= MIN_CLIP_DURATION_MS) {
            logger.info("비디오가 짧아 전체 사용: {}ms", totalDurationMs)
            return Pair(0L, totalDurationMs)
        }

        // 세그먼트가 있으면 첫 번째 (가장 좋은) 세그먼트 사용
        if (segments.isNotEmpty()) {
            val bestSegment = segments.first()
            logger.info("LLM 분석 세그먼트 사용: title={}, {}ms ~ {}ms",
                bestSegment.title, bestSegment.startTimeMs, bestSegment.endTimeMs)

            // 세그먼트 범위 검증 및 조정
            val startMs = bestSegment.startTimeMs.coerceIn(0L, totalDurationMs)
            var endMs = bestSegment.endTimeMs.coerceIn(startMs, totalDurationMs)

            // 세그먼트가 너무 길면 최대 길이로 제한
            if (endMs - startMs > MAX_CLIP_DURATION_MS) {
                endMs = startMs + MAX_CLIP_DURATION_MS
                logger.info("세그먼트가 너무 길어 {}ms로 제한", MAX_CLIP_DURATION_MS)
            }

            // 세그먼트가 너무 짧으면 확장
            if (endMs - startMs < MIN_CLIP_DURATION_MS) {
                val extension = (MIN_CLIP_DURATION_MS - (endMs - startMs)) / 2
                val newStartMs = (startMs - extension).coerceAtLeast(0L)
                val newEndMs = (endMs + extension).coerceAtMost(totalDurationMs)
                logger.info("세그먼트가 짧아 확장: {}ms ~ {}ms -> {}ms ~ {}ms",
                    startMs, endMs, newStartMs, newEndMs)
                return Pair(newStartMs, newEndMs)
            }

            return Pair(startMs, endMs)
        }

        // 세그먼트가 없으면 기본값 사용 (fallback)
        logger.warn("세그먼트 없음, 기본값 사용 (30초~90초)")
        val startMs = DEFAULT_CLIP_START_MS.coerceAtMost(totalDurationMs - DEFAULT_CLIP_DURATION_MS)
        val endMs = (startMs + DEFAULT_CLIP_DURATION_MS).coerceAtMost(totalDurationMs)

        return Pair(startMs, endMs)
    }

    /**
     * URL에서 파일 다운로드
     */
    private fun downloadFromUrl(url: URL, outputPath: String): String {
        File(tempDir).mkdirs()

        url.openStream().use { input ->
            File(outputPath).outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return outputPath
    }

    /**
     * 임시 파일 정리
     */
    private fun cleanupTempFiles(paths: List<String>) {
        paths.forEach { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                logger.warn("임시 파일 삭제 실패: path={}", path, e)
            }
        }
    }
}
