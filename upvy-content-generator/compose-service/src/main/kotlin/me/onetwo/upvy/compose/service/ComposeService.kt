package me.onetwo.upvy.compose.service

import me.onetwo.upvy.compose.dto.ComposeMetadata
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val logger = KotlinLogging.logger {}

/**
 * 합성 결과 (영상만)
 */
data class ComposeResult(
    val composeId: String,
    val videoFile: File,
    val duration: Double,
    val fileSize: Long
)

/**
 * 합성 결과 (영상 + 썸네일 ZIP)
 */
data class ComposeWithThumbnailResult(
    val composeId: String,
    val zipFile: File,
    val duration: Double,
    val videoSize: Long,
    val thumbnailSize: Long
)

/**
 * 영상 합성 서비스
 *
 * FFmpeg를 사용하여 비주얼 클립 + 오디오 + 자막을 합성합니다.
 * 스토리지 의존성 없이 순수하게 로컬 파일 처리만 수행합니다.
 *
 * 파이프라인:
 * 1. Multipart 파일 → 로컬 임시 파일로 저장
 * 2. FFmpeg 합성 (concat + amix + subtitles + watermark)
 * 3. 결과 바이너리 반환 (n8n이 S3 업로드 처리)
 * 4. 임시 파일 정리
 */
@Service
class ComposeService(
    private val ffmpegService: FFmpegService,
    @Value("\${ffmpeg.temp-dir}")
    private val tempDir: String
) {
    /**
     * 영상 합성 실행 (영상만 반환)
     *
     * @param clips 클립 파일들 (MultipartFile)
     * @param audio 오디오 파일 (MultipartFile)
     * @param metadata 메타데이터 (자막, 워터마크 등)
     * @return 합성 결과 (영상 파일)
     */
    fun compose(
        clips: List<MultipartFile>,
        audio: MultipartFile,
        metadata: ComposeMetadata
    ): ComposeResult {
        val composeId = UUID.randomUUID().toString()
        val workDir = Path.of(tempDir, composeId)
        Files.createDirectories(workDir)

        logger.info { "합성 시작: composeId=$composeId, clips=${clips.size}개" }

        try {
            // 1. Multipart → 로컬 파일 저장 (원본 확장자 유지)
            val clipFiles = clips.mapIndexed { index, clip ->
                val extension = clip.originalFilename?.substringAfterLast('.', "mp4") ?: "mp4"
                saveMultipartToFile(clip, workDir, "clip_$index.$extension")
            }
            val audioFile = saveMultipartToFile(audio, workDir, "audio.mp3")

            // 2. SRT 자막 파일 생성
            val srtFile = if (metadata.subtitles.isNotEmpty()) {
                ffmpegService.generateSrtFile(metadata.subtitles, composeId)
            } else null

            // 3. FFmpeg 합성
            logger.info { "FFmpeg 합성 시작" }
            val ffmpegResult = ffmpegService.compose(
                composeId = composeId,
                clips = clipFiles,
                audioFile = audioFile,
                srtFile = srtFile,
                metadata = metadata
            )

            logger.info { "합성 완료: composeId=$composeId, duration=${ffmpegResult.duration}s" }

            return ComposeResult(
                composeId = composeId,
                videoFile = ffmpegResult.videoFile,
                duration = ffmpegResult.duration,
                fileSize = ffmpegResult.fileSize
            )
        } catch (e: Exception) {
            logger.error(e) { "합성 실패: composeId=$composeId" }
            cleanup(composeId)
            throw e
        }
    }

    /**
     * 영상 합성 실행 (영상 + 썸네일 ZIP 반환)
     *
     * @param clips 클립 파일들
     * @param audio 오디오 파일
     * @param metadata 메타데이터
     * @return 합성 결과 (ZIP 파일)
     */
    fun composeWithThumbnail(
        clips: List<MultipartFile>,
        audio: MultipartFile,
        metadata: ComposeMetadata
    ): ComposeWithThumbnailResult {
        val composeId = UUID.randomUUID().toString()
        val workDir = Path.of(tempDir, composeId)
        Files.createDirectories(workDir)

        logger.info { "합성+썸네일 시작: composeId=$composeId, clips=${clips.size}개" }

        try {
            // 1. Multipart → 로컬 파일 저장 (원본 확장자 유지)
            val clipFiles = clips.mapIndexed { index, clip ->
                val extension = clip.originalFilename?.substringAfterLast('.', "mp4") ?: "mp4"
                saveMultipartToFile(clip, workDir, "clip_$index.$extension")
            }
            val audioFile = saveMultipartToFile(audio, workDir, "audio.mp3")

            // 2. SRT 자막 파일 생성
            val srtFile = if (metadata.subtitles.isNotEmpty()) {
                ffmpegService.generateSrtFile(metadata.subtitles, composeId)
            } else null

            // 3. FFmpeg 합성
            val ffmpegResult = ffmpegService.composeWithThumbnail(
                composeId = composeId,
                clips = clipFiles,
                audioFile = audioFile,
                srtFile = srtFile,
                metadata = metadata
            )

            // 4. ZIP으로 묶기
            val zipFile = createZip(workDir, ffmpegResult.videoFile, ffmpegResult.thumbnailFile, composeId)

            logger.info { "합성+썸네일 완료: composeId=$composeId" }

            return ComposeWithThumbnailResult(
                composeId = composeId,
                zipFile = zipFile,
                duration = ffmpegResult.duration,
                videoSize = ffmpegResult.fileSize,
                thumbnailSize = ffmpegResult.thumbnailFile.length()
            )
        } catch (e: Exception) {
            logger.error(e) { "합성 실패: composeId=$composeId" }
            cleanup(composeId)
            throw e
        }
    }

    /**
     * Multipart 파일을 로컬 파일로 저장
     */
    private fun saveMultipartToFile(multipart: MultipartFile, workDir: Path, fileName: String): File {
        val file = workDir.resolve(fileName).toFile()
        multipart.transferTo(file)
        logger.debug { "파일 저장: ${multipart.originalFilename} → $file (${multipart.size} bytes)" }
        return file
    }

    /**
     * 영상과 썸네일을 ZIP으로 묶기
     */
    private fun createZip(workDir: Path, videoFile: File, thumbnailFile: File, composeId: String): File {
        val zipFile = workDir.resolve("$composeId.zip").toFile()

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            // 영상 추가
            zip.putNextEntry(ZipEntry("video.mp4"))
            videoFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            // 썸네일 추가
            zip.putNextEntry(ZipEntry("thumbnail.jpg"))
            thumbnailFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

        logger.debug { "ZIP 생성: $zipFile (${zipFile.length()} bytes)" }
        return zipFile
    }

    /**
     * 임시 파일 정리
     */
    private fun cleanup(composeId: String) {
        try {
            ffmpegService.cleanup(composeId)
        } catch (e: Exception) {
            logger.warn(e) { "임시 파일 정리 실패: $composeId" }
        }
    }
}
