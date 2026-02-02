package me.onetwo.upvy.compose.service

import me.onetwo.upvy.compose.dto.ClipInfo
import me.onetwo.upvy.compose.dto.ComposeMetadata
import me.onetwo.upvy.compose.dto.SubtitleInfo
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * FFmpeg 합성 결과
 */
data class ComposeResult(
    val videoFile: File,
    val thumbnailFile: File,
    val duration: Double,
    val fileSize: Long
)

/**
 * FFmpeg 영상 합성 서비스
 *
 * 비주얼 클립 + 오디오 + 자막 → 최종 숏폼 영상
 *
 * FFmpeg 파이프라인:
 * 1. 클립 연결 (concat demuxer)
 * 2. 오디오 트랙 합성 (amix)
 * 3. 자막 오버레이 (ASS filter)
 * 4. 브랜드 워터마크 (overlay)
 * 5. 출력 인코딩 (H.264, 1080x1920, 30fps)
 */
@Service
class FFmpegService(
    @Value("\${ffmpeg.path}")
    private val ffmpegPath: String,
    @Value("\${ffmpeg.ffprobe-path}")
    private val ffprobePath: String,
    @Value("\${ffmpeg.temp-dir}")
    private val tempDir: String,
    @Value("\${ffmpeg.output.width}")
    private val outputWidth: Int,
    @Value("\${ffmpeg.output.height}")
    private val outputHeight: Int,
    @Value("\${ffmpeg.output.fps}")
    private val outputFps: Int,
    @Value("\${ffmpeg.output.video-bitrate}")
    private val videoBitrate: String,
    @Value("\${ffmpeg.output.audio-bitrate}")
    private val audioBitrate: String
) {
    /**
     * SRT 자막 파일 생성
     *
     * @param subtitles 자막 목록
     * @param composeId 합성 작업 ID
     * @return SRT 파일
     */
    fun generateSrtFile(subtitles: List<SubtitleInfo>, composeId: String): File? {
        if (subtitles.isEmpty()) return null

        val srtPath = Path.of(tempDir, composeId, "subtitles.srt")
        Files.createDirectories(srtPath.parent)

        val srtContent = buildString {
            subtitles.forEachIndexed { index, sub ->
                appendLine(index + 1)
                appendLine("${formatSrtTime(sub.start)} --> ${formatSrtTime(sub.end)}")
                appendLine(sub.text)
                appendLine()
            }
        }

        Files.writeString(srtPath, srtContent)
        logger.debug { "SRT 파일 생성: $srtPath (${subtitles.size}개 자막)" }

        return srtPath.toFile()
    }

    /**
     * 영상 합성 실행
     *
     * @param composeId 합성 작업 ID
     * @param clips 클립 파일 목록
     * @param clipInfos 클립 정보 목록
     * @param audioFile 오디오 파일
     * @param srtFile SRT 자막 파일 (nullable)
     * @param metadata 메타데이터
     * @return 합성 결과
     */
    fun compose(
        composeId: String,
        clips: List<File>,
        clipInfos: List<ClipInfo>,
        audioFile: File,
        srtFile: File?,
        metadata: ComposeMetadata
    ): ComposeResult {
        val workDir = Path.of(tempDir, composeId)
        Files.createDirectories(workDir)

        val outputVideo = workDir.resolve("final.mp4").toFile()
        val outputThumbnail = workDir.resolve("thumbnail.jpg").toFile()

        // 1. 클립 목록 파일 생성 (concat demuxer용)
        val concatFile = createConcatFile(workDir, clips)

        // 2. FFmpeg 명령어 구성
        val command = buildFFmpegCommand(
            concatFile = concatFile,
            audioFile = audioFile,
            srtFile = srtFile,
            outputFile = outputVideo,
            metadata = metadata
        )

        // 3. FFmpeg 실행
        logger.info { "FFmpeg 실행: ${command.joinToString(" ")}" }
        executeFFmpeg(command)

        // 4. 썸네일 생성 (3초 지점에서 프레임 추출)
        generateThumbnail(outputVideo, outputThumbnail)

        // 5. 결과 반환
        val duration = getVideoDuration(outputVideo)

        return ComposeResult(
            videoFile = outputVideo,
            thumbnailFile = outputThumbnail,
            duration = duration,
            fileSize = outputVideo.length()
        )
    }

    /**
     * 임시 파일 정리
     */
    fun cleanup(composeId: String) {
        val workDir = Path.of(tempDir, composeId)
        if (Files.exists(workDir)) {
            workDir.toFile().deleteRecursively()
            logger.debug { "임시 파일 정리 완료: $workDir" }
        }
    }

    /**
     * Concat 목록 파일 생성
     */
    private fun createConcatFile(workDir: Path, clips: List<File>): File {
        val concatFile = workDir.resolve("concat.txt").toFile()
        val content = clips.joinToString("\n") { "file '${it.absolutePath}'" }
        concatFile.writeText(content)
        return concatFile
    }

    /**
     * FFmpeg 명령어 구성
     */
    private fun buildFFmpegCommand(
        concatFile: File,
        audioFile: File,
        srtFile: File?,
        outputFile: File,
        metadata: ComposeMetadata
    ): List<String> {
        val command = mutableListOf(
            ffmpegPath,
            "-y",  // 덮어쓰기
            // 입력: 클립 목록 (concat)
            "-f", "concat",
            "-safe", "0",
            "-i", concatFile.absolutePath,
            // 입력: 오디오
            "-i", audioFile.absolutePath
        )

        // 필터 체인 구성
        val filters = mutableListOf<String>()

        // 비디오 스케일 (9:16)
        filters.add("[0:v]scale=${outputWidth}:${outputHeight}:force_original_aspect_ratio=decrease,pad=${outputWidth}:${outputHeight}:(ow-iw)/2:(oh-ih)/2[scaled]")

        // 자막 오버레이 (있는 경우)
        if (srtFile != null) {
            // 자막 스타일 적용
            val subtitleFilter = "subtitles=${srtFile.absolutePath}:force_style='FontSize=48,PrimaryColour=&HFFFFFF,OutlineColour=&H000000,Outline=2,MarginV=100'"
            filters.add("[scaled]$subtitleFilter[subtitled]")
            command.addAll(listOf("-filter_complex", filters.joinToString(";")))
            command.addAll(listOf("-map", "[subtitled]"))
        } else {
            command.addAll(listOf("-filter_complex", filters.joinToString(";")))
            command.addAll(listOf("-map", "[scaled]"))
        }

        // 오디오 매핑
        command.addAll(listOf("-map", "1:a"))

        // 출력 설정
        command.addAll(
            listOf(
                "-c:v", "libx264",
                "-preset", "medium",
                "-b:v", videoBitrate,
                "-c:a", "aac",
                "-b:a", audioBitrate,
                "-r", outputFps.toString(),
                "-movflags", "+faststart",  // 스트리밍 최적화
                outputFile.absolutePath
            )
        )

        return command
    }

    /**
     * FFmpeg 명령어 실행
     */
    private fun executeFFmpeg(command: List<String>) {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor(5, TimeUnit.MINUTES)

        if (!exitCode || process.exitValue() != 0) {
            logger.error { "FFmpeg 실패: $output" }
            throw RuntimeException("FFmpeg 실행 실패: exitCode=${process.exitValue()}")
        }

        logger.debug { "FFmpeg 완료" }
    }

    /**
     * 썸네일 생성 (3초 지점)
     */
    private fun generateThumbnail(videoFile: File, thumbnailFile: File) {
        val command = listOf(
            ffmpegPath,
            "-y",
            "-i", videoFile.absolutePath,
            "-ss", "3",  // 3초 지점
            "-vframes", "1",
            "-q:v", "2",
            thumbnailFile.absolutePath
        )

        executeFFmpeg(command)
        logger.debug { "썸네일 생성 완료: $thumbnailFile" }
    }

    /**
     * 영상 길이 조회
     */
    private fun getVideoDuration(videoFile: File): Double {
        val command = listOf(
            ffprobePath,
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            videoFile.absolutePath
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(30, TimeUnit.SECONDS)

        return output.toDoubleOrNull() ?: 0.0
    }

    /**
     * SRT 시간 포맷 (00:00:00,000)
     */
    private fun formatSrtTime(seconds: Double): String {
        val hours = (seconds / 3600).toInt()
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = (seconds % 60).toInt()
        val millis = ((seconds % 1) * 1000).toInt()
        return "%02d:%02d:%02d,%03d".format(hours, minutes, secs, millis)
    }
}
