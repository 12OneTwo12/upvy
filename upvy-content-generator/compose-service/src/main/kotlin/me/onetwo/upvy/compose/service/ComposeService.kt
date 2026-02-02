package me.onetwo.upvy.compose.service

import me.onetwo.upvy.compose.dto.ComposeRequest
import me.onetwo.upvy.compose.dto.ComposeResponse
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.UUID

private val logger = KotlinLogging.logger {}

/**
 * 영상 합성 서비스
 *
 * FFmpeg를 사용하여 비주얼 클립 + 오디오 + 자막을 합성합니다.
 *
 * 합성 파이프라인:
 * 1. GCS에서 클립/오디오 다운로드
 * 2. SRT 자막 파일 생성
 * 3. FFmpeg 합성 (concat + amix + subtitles + overlay)
 * 4. 결과물 GCS 업로드
 * 5. 임시 파일 정리
 */
@Service
class ComposeService(
    private val gcsService: GcsService,
    private val ffmpegService: FFmpegService
) {
    /**
     * 영상 합성 실행
     *
     * @param request 합성 요청
     * @return 합성 결과
     */
    suspend fun compose(request: ComposeRequest): ComposeResponse {
        val composeId = UUID.randomUUID().toString()
        logger.info { "합성 시작: composeId=$composeId" }

        try {
            // 1. GCS에서 클립 다운로드
            logger.debug { "클립 다운로드 시작: ${request.clips.size}개" }
            val clipFiles = request.clips.map { clip ->
                gcsService.download(clip.gcsUri)
            }

            // 2. GCS에서 오디오 다운로드
            logger.debug { "오디오 다운로드: ${request.audio.gcsUri}" }
            val audioFile = gcsService.download(request.audio.gcsUri)

            // 3. SRT 자막 파일 생성
            logger.debug { "자막 파일 생성: ${request.subtitles.size}개" }
            val srtFile = ffmpegService.generateSrtFile(request.subtitles, composeId)

            // 4. FFmpeg 합성
            logger.info { "FFmpeg 합성 시작" }
            val composeResult = ffmpegService.compose(
                composeId = composeId,
                clips = clipFiles,
                clipInfos = request.clips,
                audioFile = audioFile,
                srtFile = srtFile,
                metadata = request.metadata
            )

            // 5. GCS 업로드
            logger.debug { "결과물 업로드 시작" }
            val videoGcsUri = gcsService.upload(
                localFile = composeResult.videoFile,
                gcsUri = "${request.output.gcsUri}${composeId}/final.mp4"
            )
            val thumbnailGcsUri = gcsService.upload(
                localFile = composeResult.thumbnailFile,
                gcsUri = "${request.output.gcsUri}${composeId}/thumbnail.jpg"
            )

            // 6. 임시 파일 정리
            logger.debug { "임시 파일 정리" }
            ffmpegService.cleanup(composeId)

            logger.info { "합성 완료: composeId=$composeId, duration=${composeResult.duration}s" }

            return ComposeResponse(
                composeId = composeId,
                videoGcsUri = videoGcsUri,
                thumbnailGcsUri = thumbnailGcsUri,
                duration = composeResult.duration,
                resolution = "1080x1920",
                fileSize = composeResult.fileSize
            )
        } catch (e: Exception) {
            logger.error(e) { "합성 실패: composeId=$composeId" }
            // 임시 파일 정리 시도
            try {
                ffmpegService.cleanup(composeId)
            } catch (cleanupError: Exception) {
                logger.warn(cleanupError) { "임시 파일 정리 실패" }
            }
            throw e
        }
    }
}
