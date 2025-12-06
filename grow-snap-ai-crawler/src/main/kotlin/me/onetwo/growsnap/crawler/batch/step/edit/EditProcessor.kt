package me.onetwo.growsnap.crawler.batch.step.edit

import me.onetwo.growsnap.crawler.client.video.FFmpegWrapper
import me.onetwo.growsnap.crawler.domain.AiContentJob
import me.onetwo.growsnap.crawler.domain.JobStatus
import me.onetwo.growsnap.crawler.service.S3Service
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
    @Value("\${ffmpeg.temp-dir:/tmp/ai-crawler}") private val tempDir: String,
    @Value("\${s3.bucket.edited-videos}") private val editedVideosBucket: String,
    @Value("\${s3.bucket.thumbnails}") private val thumbnailsBucket: String
) : ItemProcessor<AiContentJob, AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(EditProcessor::class.java)
        private const val DEFAULT_CLIP_START_MS = 30000L   // 30초부터 시작
        private const val DEFAULT_CLIP_DURATION_MS = 60000L // 60초 클립
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

            // 3. 클리핑 시작/종료 시간 계산
            val (startMs, endMs) = calculateClipRange(videoInfo.durationMs)

            // 4. 비디오 클리핑
            val clippedPath = "${tempDir}/clip_${job.id}.mp4"
            ffmpegWrapper.clip(localVideoPath, clippedPath, startMs, endMs)
            logger.debug("비디오 클리핑 완료: jobId={}, path={}", job.id, clippedPath)

            // 5. 세로 포맷으로 리사이징 (9:16)
            val resizedPath = "${tempDir}/resized_${job.id}.mp4"
            ffmpegWrapper.resizeVertical(clippedPath, resizedPath)
            logger.debug("세로 리사이징 완료: jobId={}, path={}", job.id, resizedPath)

            // 6. 썸네일 추출
            val thumbnailPath = "${tempDir}/thumb_${job.id}.jpg"
            val thumbnailTimeMs = (endMs - startMs) / 2 // 중간 지점
            ffmpegWrapper.thumbnail(clippedPath, thumbnailPath, thumbnailTimeMs)
            logger.debug("썸네일 추출 완료: jobId={}, path={}", job.id, thumbnailPath)

            // 7. S3 업로드
            val editedS3Key = "clips/${job.youtubeVideoId}/${job.id}.mp4"
            s3Service.upload(resizedPath, editedS3Key, editedVideosBucket)
            logger.debug("편집 비디오 S3 업로드 완료: jobId={}, s3Key={}", job.id, editedS3Key)

            val thumbnailS3Key = "thumbnails/${job.youtubeVideoId}/${job.id}.jpg"
            s3Service.upload(thumbnailPath, thumbnailS3Key, thumbnailsBucket)
            logger.debug("썸네일 S3 업로드 완료: jobId={}, s3Key={}", job.id, thumbnailS3Key)

            // 8. 임시 파일 정리
            cleanupTempFiles(listOf(localVideoPath, clippedPath, resizedPath, thumbnailPath))

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
     * 클립 범위 계산
     */
    private fun calculateClipRange(totalDurationMs: Long): Pair<Long, Long> {
        // 비디오가 짧으면 전체 사용
        if (totalDurationMs <= DEFAULT_CLIP_DURATION_MS) {
            return Pair(0L, totalDurationMs)
        }

        // 기본: 30초부터 90초까지 (60초 클립)
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
