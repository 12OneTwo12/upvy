package me.onetwo.upvy.crawler.client.video

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * FFmpeg 래퍼 인터페이스
 *
 * 비디오 클리핑, 썸네일 추출 등의 기능을 제공합니다.
 */
interface FFmpegWrapper {

    /**
     * 비디오 클리핑
     *
     * @param inputPath 입력 비디오 경로
     * @param outputPath 출력 비디오 경로
     * @param startMs 시작 시간 (밀리초)
     * @param endMs 종료 시간 (밀리초)
     * @return 출력 파일 경로
     */
    fun clip(inputPath: String, outputPath: String, startMs: Long, endMs: Long): String

    /**
     * 썸네일 추출
     *
     * @param inputPath 입력 비디오 경로
     * @param outputPath 출력 이미지 경로
     * @param timeMs 추출할 시간 (밀리초)
     * @return 출력 파일 경로
     */
    fun thumbnail(inputPath: String, outputPath: String, timeMs: Long): String

    /**
     * 세로 리사이징 (9:16 쇼츠 포맷)
     *
     * @param inputPath 입력 비디오 경로
     * @param outputPath 출력 비디오 경로
     * @return 출력 파일 경로
     */
    fun resizeVertical(inputPath: String, outputPath: String): String

    /**
     * 비디오 정보 조회
     *
     * @param inputPath 입력 비디오 경로
     * @return 비디오 정보 (duration, width, height 등)
     */
    fun getVideoInfo(inputPath: String): VideoInfo

    /**
     * 텍스트 오버레이 추가 (상단 제목 + 하단 자막)
     *
     * @param inputPath 입력 비디오 경로
     * @param outputPath 출력 비디오 경로
     * @param title 상단에 표시할 제목
     * @param srtPath SRT 자막 파일 경로 (옵션)
     * @param fontPath 한글 폰트 파일 경로
     * @return 출력 파일 경로
     */
    fun addTextOverlay(
        inputPath: String,
        outputPath: String,
        title: String,
        srtPath: String?,
        fontPath: String
    ): String

    /**
     * 여러 비디오를 하나로 병합 (concat demuxer 사용)
     *
     * FFmpeg의 concat demuxer를 사용하여 재인코딩 없이 빠르게 병합합니다.
     * 모든 클립은 같은 코덱/해상도여야 합니다.
     *
     * @param inputPaths 입력 비디오 경로 리스트 (순서대로 이어붙임)
     * @param outputPath 출력 비디오 경로
     * @return 출력 파일 경로
     */
    fun concat(inputPaths: List<String>, outputPath: String): String
}

/**
 * 비디오 정보
 */
data class VideoInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val codec: String? = null
)

/**
 * FFmpeg 래퍼 구현체
 */
@Component
class FFmpegWrapperImpl(
    @Value("\${ffmpeg.path:/usr/bin/ffmpeg}") private val ffmpegPath: String,
    @Value("\${ffmpeg.temp-dir:/tmp/ai-crawler}") private val tempDir: String
) : FFmpegWrapper {

    companion object {
        private val logger = LoggerFactory.getLogger(FFmpegWrapperImpl::class.java)
        private const val PROCESS_TIMEOUT_MINUTES = 30L
    }

    init {
        File(tempDir).mkdirs()
    }

    override fun clip(inputPath: String, outputPath: String, startMs: Long, endMs: Long): String {
        logger.info("비디오 클리핑 시작: input={}, start={}ms, end={}ms", inputPath, startMs, endMs)

        val startSeconds = startMs / 1000.0
        val durationSeconds = (endMs - startMs) / 1000.0

        val command = listOf(
            ffmpegPath,
            "-ss", String.format("%.3f", startSeconds),
            "-i", inputPath,
            "-t", String.format("%.3f", durationSeconds),
            "-c:v", "libx264",
            "-preset", "fast",
            "-crf", "23",
            "-c:a", "aac",
            "-b:a", "128k",
            "-y",
            outputPath
        )

        executeCommand(command, "Video clipping")

        logger.info("비디오 클리핑 완료: output={}", outputPath)
        return outputPath
    }

    override fun thumbnail(inputPath: String, outputPath: String, timeMs: Long): String {
        logger.info("썸네일 추출 시작: input={}, time={}ms", inputPath, timeMs)

        val timeSeconds = timeMs / 1000.0

        val command = listOf(
            ffmpegPath,
            "-ss", String.format("%.3f", timeSeconds),
            "-i", inputPath,
            "-vframes", "1",
            "-q:v", "2",
            "-y",
            outputPath
        )

        executeCommand(command, "Thumbnail extraction")

        logger.info("썸네일 추출 완료: output={}", outputPath)
        return outputPath
    }

    override fun resizeVertical(inputPath: String, outputPath: String): String {
        logger.info("세로 리사이징 시작: input={}", inputPath)

        // 9:16 비율 (1080x1920)
        val command = listOf(
            ffmpegPath,
            "-i", inputPath,
            "-vf", "scale=1080:1920:force_original_aspect_ratio=decrease,pad=1080:1920:(ow-iw)/2:(oh-ih)/2",
            "-c:v", "libx264",
            "-preset", "fast",
            "-crf", "23",
            "-c:a", "aac",
            "-b:a", "128k",
            "-y",
            outputPath
        )

        executeCommand(command, "Vertical resizing")

        logger.info("세로 리사이징 완료: output={}", outputPath)
        return outputPath
    }

    override fun getVideoInfo(inputPath: String): VideoInfo {
        logger.debug("비디오 정보 조회: input={}", inputPath)

        val ffprobePath = ffmpegPath.replace("ffmpeg", "ffprobe")

        val command = listOf(
            ffprobePath,
            "-v", "quiet",
            "-print_format", "json",
            "-show_streams",
            "-show_format",
            inputPath
        )

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(60, TimeUnit.SECONDS)

            // Jackson을 사용하여 안전하게 JSON 파싱
            val jsonNode = jacksonObjectMapper().readTree(output)
            val formatNode = jsonNode.get("format")
            val streamNode = jsonNode.get("streams")?.get(0)

            val duration = formatNode?.get("duration")?.asText()?.toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
            val width = streamNode?.get("width")?.asInt() ?: 0
            val height = streamNode?.get("height")?.asInt() ?: 0

            return VideoInfo(duration, width, height)

        } catch (e: Exception) {
            logger.warn("비디오 정보 조회 실패: input={}", inputPath, e)
            return VideoInfo(0, 0, 0)
        }
    }

    override fun addTextOverlay(
        inputPath: String,
        outputPath: String,
        title: String,
        srtPath: String?,
        fontPath: String
    ): String {
        logger.info("텍스트 오버레이 추가 시작: input={}, title={}, srtPath={}", inputPath, title, srtPath)

        // 제목용 임시 SRT 파일 생성
        val titleSrtPath = "${inputPath}_title.srt"
        val titleSrtContent = """
            1
            00:00:00,000 --> 99:59:59,999
            $title
        """.trimIndent()

        File(titleSrtPath).writeText(titleSrtContent)
        logger.info("제목 SRT 파일 생성: {}", titleSrtPath)

        try {
            // Filter complex 구성
            val filters = mutableListOf<String>()

            // 1. 상단 제목 (subtitles 필터 사용)
            val escapedTitleSrtPath = titleSrtPath.replace("\\", "/").replace(":", "\\\\:")
            filters.add(
                "subtitles='$escapedTitleSrtPath':" +
                "force_style='FontName=Noto Sans KR," +
                "FontSize=20," +
                "PrimaryColour=&HFFFFFF," +
                "OutlineColour=&H000000," +
                "Outline=2," +
                "BackColour=&H80000000," +  // 반투명 검은 배경
                "Bold=1," +
                "Alignment=8," +  // 상단 중앙
                "MarginV=15'"  // 상단에서 15px 아래 (더 위로)
            )

            // 2. 하단 자막 (subtitles 필터 사용) - SRT 파일이 있는 경우만
            if (srtPath != null && File(srtPath).exists()) {
                // STT 단계에서 이미 짧은 세그먼트로 나뉘므로 추가 처리 불필요
                val escapedSrtPath = srtPath.replace("\\", "/").replace(":", "\\\\:")

                filters.add(
                    "subtitles='$escapedSrtPath':" +
                    "force_style='FontName=Noto Sans KR," +
                    "FontSize=14," +
                    "PrimaryColour=&HFFFFFF," +
                    "OutlineColour=&H000000," +
                    "Outline=1," +
                    "Bold=1," +
                    "Alignment=2," +  // 하단 중앙
                    "MarginV=60'"  // 하단에서 60px 위 (조금 위로)
                )
            }

            val vfFilter = filters.joinToString(",")

            val command = listOf(
                ffmpegPath,
                "-i", inputPath,
                "-vf", vfFilter,
                "-c:v", "libx264",
                "-preset", "fast",
                "-crf", "23",
                "-c:a", "copy",  // 오디오는 복사 (인코딩 생략)
                "-y",
                outputPath
            )

            executeCommand(command, "Text overlay")

            logger.info("텍스트 오버레이 추가 완료: output={}", outputPath)
            return outputPath
        } finally {
            // 임시 제목 SRT 파일 삭제
            try {
                File(titleSrtPath).delete()
                logger.debug("임시 제목 SRT 파일 삭제: {}", titleSrtPath)
            } catch (e: Exception) {
                logger.warn("임시 제목 SRT 파일 삭제 실패: {}", titleSrtPath, e)
            }
        }
    }

    override fun concat(inputPaths: List<String>, outputPath: String): String {
        logger.info("비디오 병합 시작: {} 파일 → {}", inputPaths.size, outputPath)

        require(inputPaths.isNotEmpty()) { "입력 파일이 없습니다" }

        // 단일 파일이면 복사만
        if (inputPaths.size == 1) {
            File(inputPaths.first()).copyTo(File(outputPath), overwrite = true)
            logger.info("단일 파일, 복사 완료: {}", outputPath)
            return outputPath
        }

        // FFmpeg concat demuxer용 파일 리스트 생성
        val concatListPath = "$tempDir/concat_list_${System.currentTimeMillis()}.txt"
        val concatListContent = inputPaths.joinToString("\n") { path ->
            // FFmpeg concat demuxer 형식: file '/path/to/file.mp4'
            // 작은따옴표가 경로에 있을 수 있으므로 이스케이프
            "file '${path.replace("'", "'\\''")}'"
        }
        File(concatListPath).writeText(concatListContent, Charsets.UTF_8)

        logger.debug("Concat 리스트 파일 생성: {}", concatListPath)

        try {
            // FFmpeg concat demuxer 사용 (re-encoding 없이 빠른 병합)
            val command = listOf(
                ffmpegPath,
                "-f", "concat",       // concat demuxer 지정
                "-safe", "0",         // 절대 경로 허용
                "-i", concatListPath, // concat 파일 목록
                "-c", "copy",         // 코덱 복사 (re-encoding 없음)
                "-y",                 // 기존 파일 덮어쓰기
                outputPath
            )

            executeCommand(command, "Video concatenation")

            logger.info("비디오 병합 완료: {}", outputPath)
            return outputPath

        } finally {
            // concat 리스트 파일 정리
            try {
                File(concatListPath).delete()
            } catch (e: Exception) {
                logger.warn("concat 파일 삭제 실패: {}", concatListPath, e)
            }
        }
    }

    /**
     * 제목이 너무 길면 자동 줄바꿈 추가
     *
     * @param title 원본 제목
     * @param maxCharsPerLine 한 줄 최대 문자 수
     * @return 줄바꿈이 추가된 제목
     */
    private fun wrapTitle(title: String, maxCharsPerLine: Int): String {
        if (title.length <= maxCharsPerLine) {
            return title
        }

        // 공백을 기준으로 단어 분리
        val words = title.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"

            if (testLine.length <= maxCharsPerLine) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }

        // 최대 2줄까지만 표시
        return lines.take(2).joinToString("\n")
    }

    /**
     * FFmpeg drawtext 필터용 문자열 이스케이프
     *
     * FFmpeg의 drawtext 필터는 특수문자를 이스케이프해야 합니다.
     * 참고: https://ffmpeg.org/ffmpeg-filters.html#drawtext
     *
     * @param text 이스케이프할 텍스트
     * @return 이스케이프된 텍스트
     */
    private fun escapeDrawtextString(text: String): String {
        return text
            .replace("\\", "\\\\")   // 백슬래시
            .replace(":", "\\:")     // 콜론
            .replace("'", "\\'")     // 작은따옴표
            .replace("%", "\\%")     // 퍼센트
    }

    private fun executeCommand(command: List<String>, operation: String) {
        logger.debug("FFmpeg 명령어: {}", command.joinToString(" "))

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(PROCESS_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            if (!completed) {
                process.destroyForcibly()
                throw FFmpegException("$operation timeout after $PROCESS_TIMEOUT_MINUTES minutes")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.error("FFmpeg 실패: exitCode={}, output={}", exitCode, errorOutput)
                throw FFmpegException("$operation failed with exit code $exitCode")
            }

        } catch (e: FFmpegException) {
            throw e
        } catch (e: Exception) {
            logger.error("FFmpeg 실행 실패", e)
            throw FFmpegException("$operation failed", e)
        }
    }
}

/**
 * FFmpeg 예외
 */
class FFmpegException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
