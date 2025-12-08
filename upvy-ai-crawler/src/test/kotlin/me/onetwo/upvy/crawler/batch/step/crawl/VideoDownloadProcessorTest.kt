package me.onetwo.upvy.crawler.batch.step.crawl

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import me.onetwo.upvy.crawler.client.video.YtDlpException
import me.onetwo.upvy.crawler.client.video.YtDlpWrapper
import me.onetwo.upvy.crawler.domain.EvaluatedVideo
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.Recommendation
import me.onetwo.upvy.crawler.domain.VideoCandidate
import me.onetwo.upvy.crawler.service.S3Service
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
@DisplayName("VideoDownloadProcessor 테스트")
class VideoDownloadProcessorTest {

    @MockK
    private lateinit var ytDlpWrapper: YtDlpWrapper

    @MockK
    private lateinit var s3Service: S3Service

    private val rawVideosPrefix = "raw"

    private val processor: VideoDownloadProcessor by lazy {
        VideoDownloadProcessor(ytDlpWrapper, s3Service, rawVideosPrefix)
    }

    @Nested
    @DisplayName("process - 비디오 다운로드 및 S3 업로드")
    inner class Process {

        @Test
        @DisplayName("유효한 비디오 후보가 주어지면 다운로드 후 AiContentJob을 반환한다")
        fun process_WithValidCandidate_ReturnsAiContentJob() {
            // Given: 추천된 비디오 후보
            val candidate = VideoCandidate(
                videoId = "abc123",
                title = "Kotlin Programming Tutorial",
                channelId = "channel123",
                channelTitle = "Tech Channel"
            )
            val evaluatedVideo = EvaluatedVideo(
                candidate = candidate,
                relevanceScore = 85,
                educationalValue = 80,
                shortFormSuitability = 75,
                predictedQuality = 82,
                recommendation = Recommendation.RECOMMENDED,
                reasoning = "Good educational content"
            )

            val localPath = "/tmp/abc123.mp4"
            val s3Key = "raw/abc123.mp4"

            every { ytDlpWrapper.download(candidate.videoId) } returns localPath
            every { s3Service.upload(localPath, any()) } returns s3Key

            // When: 프로세서 실행
            val result = processor.process(evaluatedVideo)

            // Then: AiContentJob이 생성됨
            assertThat(result).isNotNull
            assertThat(result!!.youtubeVideoId).isEqualTo("abc123")
            assertThat(result.youtubeTitle).isEqualTo("Kotlin Programming Tutorial")
            assertThat(result.status).isEqualTo(JobStatus.CRAWLED)
            assertThat(result.rawVideoS3Key).isEqualTo(s3Key)
            assertThat(result.qualityScore).isEqualTo(82)

            verify(exactly = 1) { ytDlpWrapper.download("abc123") }
            verify(exactly = 1) { s3Service.upload(localPath, any()) }
        }

        @Test
        @DisplayName("다운로드 실패 시 null을 반환한다 (스킵 처리)")
        fun process_WhenDownloadFails_ReturnsNull() {
            // Given: 다운로드 실패 상황
            val candidate = VideoCandidate(
                videoId = "invalid123",
                title = "Invalid Video",
                channelId = "channel123"
            )
            val evaluatedVideo = EvaluatedVideo(
                candidate = candidate,
                relevanceScore = 50,
                educationalValue = 50,
                shortFormSuitability = 50,
                predictedQuality = 50,
                recommendation = Recommendation.MAYBE,
                reasoning = "Average content"
            )

            every { ytDlpWrapper.download(any()) } throws YtDlpException("Download failed")

            // When: 프로세서 실행
            val result = processor.process(evaluatedVideo)

            // Then: null 반환 (스킵)
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("S3 업로드 실패 시 null을 반환한다")
        fun process_WhenS3UploadFails_ReturnsNull() {
            // Given: S3 업로드 실패 상황
            val candidate = VideoCandidate(
                videoId = "xyz789",
                title = "Test Video",
                channelId = "channel123"
            )
            val evaluatedVideo = EvaluatedVideo(
                candidate = candidate,
                relevanceScore = 80,
                educationalValue = 75,
                shortFormSuitability = 70,
                predictedQuality = 78,
                recommendation = Recommendation.RECOMMENDED,
                reasoning = "Good content"
            )

            every { ytDlpWrapper.download(any()) } returns "/tmp/xyz789.mp4"
            every { s3Service.upload(any<String>(), any()) } throws RuntimeException("S3 upload failed")

            // When: 프로세서 실행
            val result = processor.process(evaluatedVideo)

            // Then: null 반환 (스킵)
            assertThat(result).isNull()
        }
    }
}
