package me.onetwo.upvy.crawler.batch.step.crawl

import me.onetwo.upvy.crawler.client.video.YtDlpWrapper
import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.EvaluatedVideo
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.service.S3Service
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant

/**
 * 비디오 다운로드 및 S3 업로드 Processor
 *
 * 평가된 비디오를 다운로드하고 S3에 업로드한 후
 * AiContentJob 엔티티를 생성합니다.
 */
@Component
class VideoDownloadProcessor(
    private val ytDlpWrapper: YtDlpWrapper,
    private val s3Service: S3Service,
    @Value("\${s3.prefix.raw-videos}") private val rawVideosPrefix: String
) : ItemProcessor<EvaluatedVideo, AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(VideoDownloadProcessor::class.java)
    }

    override fun process(evaluatedVideo: EvaluatedVideo): AiContentJob? {
        val candidate = evaluatedVideo.candidate
        logger.info(
            "비디오 처리 시작: videoId={}, title={}, score={}",
            candidate.videoId,
            candidate.title,
            evaluatedVideo.predictedQuality
        )

        try {
            // 1. yt-dlp로 비디오 다운로드
            val localPath = ytDlpWrapper.download(candidate.videoId)
            logger.debug("다운로드 완료: videoId={}, path={}", candidate.videoId, localPath)

            // 2. S3에 업로드 (단일 버킷 + prefix)
            val s3Key = "$rawVideosPrefix/${candidate.videoId}.mp4"
            s3Service.upload(localPath, s3Key)
            logger.debug("S3 업로드 완료: videoId={}, s3Key={}", candidate.videoId, s3Key)

            // 3. 로컬 파일 삭제
            try {
                File(localPath).delete()
                logger.debug("로컬 파일 삭제 완료: {}", localPath)
            } catch (e: Exception) {
                logger.warn("로컬 파일 삭제 실패: {}", localPath, e)
            }

            // 4. AiContentJob 엔티티 생성
            val now = Instant.now()
            val job = AiContentJob(
                youtubeVideoId = candidate.videoId,
                youtubeChannelId = candidate.channelId,
                youtubeChannelTitle = candidate.channelTitle,
                youtubeTitle = candidate.title,
                status = JobStatus.CRAWLED,
                rawVideoS3Key = s3Key,
                qualityScore = evaluatedVideo.predictedQuality,
                language = evaluatedVideo.language.code,  // 콘텐츠 언어
                createdAt = now,
                createdBy = "SYSTEM",
                updatedAt = now,
                updatedBy = "SYSTEM"
            )

            logger.info(
                "비디오 처리 완료: videoId={}, jobStatus={}, language={}",
                candidate.videoId,
                job.status,
                evaluatedVideo.language.code
            )

            return job

        } catch (e: Exception) {
            logger.error(
                "비디오 처리 실패: videoId={}, error={}",
                candidate.videoId,
                e.message,
                e
            )
            // null 반환 시 해당 아이템 스킵
            return null
        }
    }
}
