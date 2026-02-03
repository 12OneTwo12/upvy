package me.onetwo.upvy.compose.service

import me.onetwo.upvy.compose.config.FFmpegException
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
 * FFmpeg 합성 결과 (영상만)
 */
data class FFmpegComposeResult(
    val videoFile: File,
    val duration: Double,
    val fileSize: Long
)

/**
 * FFmpeg 합성 결과 (영상 + 썸네일)
 */
data class FFmpegComposeWithThumbnailResult(
    val videoFile: File,
    val thumbnailFile: File,
    val duration: Double,
    val fileSize: Long
)

/**
 * FFmpeg 영상 합성 서비스
 *
 * 비주얼(이미지 또는 비디오) + 오디오 + 자막 → 최종 숏폼 영상
 *
 * FFmpeg 파이프라인:
 * 1. 입력 처리:
 *    - 이미지: -loop 1로 오디오 길이만큼 반복
 *    - 비디오: concat demuxer로 연결
 * 2. 오디오 트랙 합성
 * 3. 자막 오버레이 (subtitles filter)
 * 4. 출력 인코딩 (H.264, 1080x1920, 30fps)
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
    companion object {
        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    }

    /**
     * 파일이 이미지인지 확인
     */
    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    /**
     * 오디오 파일 길이 조회
     */
    private fun getAudioDuration(audioFile: File): Double {
        val command = listOf(
            ffprobePath,
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            audioFile.absolutePath
        )

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor(30, TimeUnit.SECONDS)

        return output.toDoubleOrNull() ?: 60.0  // 기본값 60초
    }

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
     * 영상 합성 실행 (영상만)
     *
     * @param composeId 합성 작업 ID
     * @param clips 클립 파일 목록 (이미지 또는 비디오)
     * @param audioFile 오디오 파일
     * @param srtFile SRT 자막 파일 (nullable)
     * @param metadata 메타데이터
     * @return 합성 결과
     */
    fun compose(
        composeId: String,
        clips: List<File>,
        audioFile: File,
        srtFile: File?,
        metadata: ComposeMetadata
    ): FFmpegComposeResult {
        val workDir = Path.of(tempDir, composeId)
        Files.createDirectories(workDir)

        val outputVideo = workDir.resolve("final.mp4").toFile()

        // 입력 타입 감지: 이미지들 vs 비디오 클립들
        val allImages = clips.all { isImageFile(it) }
        val isSingleImage = clips.size == 1 && allImages
        val isMultipleImages = clips.size > 1 && allImages

        // FFmpeg 명령어 구성
        val command = if (isSingleImage) {
            val audioDuration = getAudioDuration(audioFile)
            logger.info { "단일 이미지 입력 감지: ${clips.first().name}, 오디오 길이: ${audioDuration}초" }
            buildImageToVideoCommand(
                imageFile = clips.first(),
                audioFile = audioFile,
                audioDuration = audioDuration,
                srtFile = srtFile,
                outputFile = outputVideo,
                metadata = metadata
            )
        } else if (isMultipleImages) {
            // 여러 이미지 → 각각을 클립으로 변환 후 연결
            val audioDuration = getAudioDuration(audioFile)
            val clipDurations = metadata.clipDurations ?: distributeEqualDurations(clips.size, audioDuration)
            logger.info { "다중 이미지 입력 감지: ${clips.size}개, 각 클립 길이: $clipDurations" }
            buildMultiImageToVideoCommand(
                workDir = workDir,
                imageFiles = clips,
                audioFile = audioFile,
                clipDurations = clipDurations,
                srtFile = srtFile,
                outputFile = outputVideo,
                metadata = metadata
            )
        } else {
            val concatFile = createConcatFile(workDir, clips)
            buildFFmpegCommand(
                concatFile = concatFile,
                audioFile = audioFile,
                srtFile = srtFile,
                outputFile = outputVideo,
                metadata = metadata
            )
        }

        // FFmpeg 실행
        logger.info { "FFmpeg 실행: ${command.joinToString(" ")}" }
        executeFFmpeg(command)

        // 결과 반환
        val duration = getVideoDuration(outputVideo)

        return FFmpegComposeResult(
            videoFile = outputVideo,
            duration = duration,
            fileSize = outputVideo.length()
        )
    }

    /**
     * 영상 합성 실행 (영상 + 썸네일)
     *
     * @param composeId 합성 작업 ID
     * @param clips 클립 파일 목록 (이미지 또는 비디오)
     * @param audioFile 오디오 파일
     * @param srtFile SRT 자막 파일 (nullable)
     * @param metadata 메타데이터
     * @return 합성 결과
     */
    fun composeWithThumbnail(
        composeId: String,
        clips: List<File>,
        audioFile: File,
        srtFile: File?,
        metadata: ComposeMetadata
    ): FFmpegComposeWithThumbnailResult {
        val workDir = Path.of(tempDir, composeId)
        Files.createDirectories(workDir)

        val outputVideo = workDir.resolve("final.mp4").toFile()
        val outputThumbnail = workDir.resolve("thumbnail.jpg").toFile()

        // 입력 타입 감지: 이미지들 vs 비디오 클립들
        val allImages = clips.all { isImageFile(it) }
        val isSingleImage = clips.size == 1 && allImages
        val isMultipleImages = clips.size > 1 && allImages

        // FFmpeg 명령어 구성
        val command = if (isSingleImage) {
            val audioDuration = getAudioDuration(audioFile)
            logger.info { "단일 이미지 입력 감지: ${clips.first().name}, 오디오 길이: ${audioDuration}초" }
            buildImageToVideoCommand(
                imageFile = clips.first(),
                audioFile = audioFile,
                audioDuration = audioDuration,
                srtFile = srtFile,
                outputFile = outputVideo,
                metadata = metadata
            )
        } else if (isMultipleImages) {
            // 여러 이미지 → 각각을 클립으로 변환 후 연결
            val audioDuration = getAudioDuration(audioFile)
            val clipDurations = metadata.clipDurations ?: distributeEqualDurations(clips.size, audioDuration)
            logger.info { "다중 이미지 입력 감지: ${clips.size}개, 각 클립 길이: $clipDurations" }
            buildMultiImageToVideoCommand(
                workDir = workDir,
                imageFiles = clips,
                audioFile = audioFile,
                clipDurations = clipDurations,
                srtFile = srtFile,
                outputFile = outputVideo,
                metadata = metadata
            )
        } else {
            val concatFile = createConcatFile(workDir, clips)
            buildFFmpegCommand(
                concatFile = concatFile,
                audioFile = audioFile,
                srtFile = srtFile,
                outputFile = outputVideo,
                metadata = metadata
            )
        }

        // FFmpeg 실행
        logger.info { "FFmpeg 실행: ${command.joinToString(" ")}" }
        executeFFmpeg(command)

        // 썸네일 생성 (3초 지점에서 프레임 추출, 또는 이미지 입력시 원본 사용)
        if (isSingleImage || isMultipleImages) {
            // 이미지 입력인 경우 첫 번째 이미지를 썸네일로 리사이즈
            generateThumbnailFromImage(clips.first(), outputThumbnail)
        } else {
            generateThumbnail(outputVideo, outputThumbnail)
        }

        // 결과 반환
        val duration = getVideoDuration(outputVideo)

        return FFmpegComposeWithThumbnailResult(
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
            val subtitleFilter = "subtitles=${srtFile.absolutePath}:force_style='FontName=Noto Sans CJK KR,FontSize=22,PrimaryColour=&HFFFFFF,OutlineColour=&H000000,Outline=2,BackColour=&H80000000,Bold=1,Alignment=2,MarginV=80,MarginL=60,MarginR=60'"
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
     * 이미지 → 비디오 변환 FFmpeg 명령어 구성
     *
     * 단일 이미지를 오디오 길이만큼 반복하여 비디오 생성
     */
    private fun buildImageToVideoCommand(
        imageFile: File,
        audioFile: File,
        audioDuration: Double,
        srtFile: File?,
        outputFile: File,
        metadata: ComposeMetadata
    ): List<String> {
        val command = mutableListOf(
            ffmpegPath,
            "-y",  // 덮어쓰기
            // 입력: 이미지 (무한 루프)
            "-loop", "1",
            "-i", imageFile.absolutePath,
            // 입력: 오디오
            "-i", audioFile.absolutePath,
            // 오디오 길이로 영상 길이 제한
            "-t", audioDuration.toString()
        )

        // 필터 체인 구성
        val filters = mutableListOf<String>()

        // 비디오 스케일 (9:16)
        filters.add("[0:v]scale=${outputWidth}:${outputHeight}:force_original_aspect_ratio=decrease,pad=${outputWidth}:${outputHeight}:(ow-iw)/2:(oh-ih)/2[scaled]")

        // 자막 오버레이 (있는 경우)
        if (srtFile != null) {
            val subtitleFilter = "subtitles=${srtFile.absolutePath}:force_style='FontName=Noto Sans CJK KR,FontSize=22,PrimaryColour=&HFFFFFF,OutlineColour=&H000000,Outline=2,BackColour=&H80000000,Bold=1,Alignment=2,MarginV=80,MarginL=60,MarginR=60'"
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
                "-pix_fmt", "yuv420p",  // 호환성을 위한 픽셀 포맷
                "-movflags", "+faststart",  // 스트리밍 최적화
                "-shortest",  // 오디오 끝나면 영상도 종료
                outputFile.absolutePath
            )
        )

        return command
    }

    /**
     * 총 시간을 클립 개수로 균등 분배
     */
    private fun distributeEqualDurations(clipCount: Int, totalDuration: Double): List<Double> {
        val durationPerClip = totalDuration / clipCount
        return List(clipCount) { durationPerClip }
    }

    /**
     * 여러 이미지 → 비디오 변환 FFmpeg 명령어 구성
     *
     * 각 이미지를 지정된 시간만큼 표시하고 연결하여 비디오 생성
     */
    private fun buildMultiImageToVideoCommand(
        workDir: Path,
        imageFiles: List<File>,
        audioFile: File,
        clipDurations: List<Double>,
        srtFile: File?,
        outputFile: File,
        metadata: ComposeMetadata
    ): List<String> {
        val command = mutableListOf(
            ffmpegPath,
            "-y"  // 덮어쓰기
        )

        // 각 이미지를 입력으로 추가
        imageFiles.forEach { imageFile ->
            command.addAll(listOf("-loop", "1", "-i", imageFile.absolutePath))
        }

        // 오디오 입력 추가
        command.addAll(listOf("-i", audioFile.absolutePath))

        // 필터 체인 구성
        val filterParts = mutableListOf<String>()
        val scaledStreams = mutableListOf<String>()

        // 각 이미지를 스케일링하고 지정된 시간만큼 트림
        imageFiles.forEachIndexed { index, _ ->
            val duration = clipDurations.getOrElse(index) { clipDurations.last() }
            // 스케일 + 패드 + format (yuv420p) + 트림 + fps 설정
            filterParts.add(
                "[$index:v]scale=${outputWidth}:${outputHeight}:force_original_aspect_ratio=decrease," +
                "pad=${outputWidth}:${outputHeight}:(ow-iw)/2:(oh-ih)/2," +
                "format=yuv420p,setsar=1,fps=${outputFps},trim=duration=$duration,setpts=PTS-STARTPTS[v$index]"
            )
            scaledStreams.add("[v$index]")
        }

        // 모든 비디오 스트림 연결
        val concatInput = scaledStreams.joinToString("")
        filterParts.add("${concatInput}concat=n=${imageFiles.size}:v=1:a=0[concatenated]")

        // 자막 오버레이 (있는 경우)
        val finalVideoStream = if (srtFile != null) {
            val escapedPath = srtFile.absolutePath.replace(":", "\\:").replace("'", "\\'")
            filterParts.add(
                "[concatenated]subtitles='$escapedPath':force_style='FontName=Noto Sans CJK KR,FontSize=22,PrimaryColour=&HFFFFFF,OutlineColour=&H000000,Outline=2,BackColour=&H80000000,Bold=1,Alignment=2,MarginV=80,MarginL=60,MarginR=60'[final]"
            )
            "[final]"
        } else {
            "[concatenated]"
        }

        command.addAll(listOf("-filter_complex", filterParts.joinToString(";")))
        command.addAll(listOf("-map", finalVideoStream))

        // 오디오 매핑 (마지막 입력이 오디오)
        command.addAll(listOf("-map", "${imageFiles.size}:a"))

        // 총 비디오 길이 계산
        val totalDuration = clipDurations.sum()

        // 출력 설정
        command.addAll(
            listOf(
                "-c:v", "libx264",
                "-preset", "medium",
                "-b:v", videoBitrate,
                "-c:a", "aac",
                "-b:a", audioBitrate,
                "-t", totalDuration.toString(),  // 비디오 길이 제한
                "-pix_fmt", "yuv420p",  // 호환성을 위한 픽셀 포맷
                "-movflags", "+faststart",  // 스트리밍 최적화
                "-shortest",  // 오디오/비디오 중 짧은 것에 맞춤
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
        val completed = process.waitFor(5, TimeUnit.MINUTES)

        if (!completed) {
            process.destroyForcibly()
            throw FFmpegException("FFmpeg 타임아웃 (5분 초과)")
        }

        if (process.exitValue() != 0) {
            logger.error { "FFmpeg 실패: $output" }
            throw FFmpegException("FFmpeg 실행 실패: exitCode=${process.exitValue()}, output=$output")
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
     * 이미지에서 썸네일 생성 (리사이즈)
     */
    private fun generateThumbnailFromImage(imageFile: File, thumbnailFile: File) {
        val command = listOf(
            ffmpegPath,
            "-y",
            "-i", imageFile.absolutePath,
            "-vf", "scale=${outputWidth}:${outputHeight}:force_original_aspect_ratio=decrease,pad=${outputWidth}:${outputHeight}:(ow-iw)/2:(oh-ih)/2",
            "-q:v", "2",
            thumbnailFile.absolutePath
        )

        executeFFmpeg(command)
        logger.debug { "이미지에서 썸네일 생성 완료: $thumbnailFile" }
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
