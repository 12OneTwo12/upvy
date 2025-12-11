package me.onetwo.upvy.crawler.client.video

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * yt-dlp 래퍼 인터페이스
 *
 * YouTube 비디오 다운로드를 담당합니다.
 */
interface YtDlpWrapper {

    /**
     * YouTube 비디오 다운로드
     *
     * @param videoId YouTube 비디오 ID
     * @return 다운로드된 비디오 파일 경로
     */
    fun download(videoId: String): String

    /**
     * 비디오 정보 조회 (다운로드 없이)
     *
     * @param videoId YouTube 비디오 ID
     * @return 비디오 메타데이터 (JSON 문자열)
     */
    fun getVideoInfo(videoId: String): String?
}

/**
 * yt-dlp 래퍼 구현체
 *
 * yt-dlp CLI를 사용하여 YouTube 비디오를 다운로드합니다.
 */
@Component
class YtDlpWrapperImpl(
    @Value("\${ytdlp.path:/usr/local/bin/yt-dlp}") private val ytdlpPath: String,
    @Value("\${ytdlp.output-dir:/tmp/ai-crawler/downloads}") private val outputDir: String
) : YtDlpWrapper {

    companion object {
        private val logger = LoggerFactory.getLogger(YtDlpWrapperImpl::class.java)
        private const val DOWNLOAD_TIMEOUT_MINUTES = 30L
    }

    init {
        // 출력 디렉토리 생성
        File(outputDir).mkdirs()
    }

    override fun download(videoId: String): String {
        logger.info("비디오 다운로드 시작: videoId={}", videoId)

        val outputPath = "$outputDir/$videoId.mp4"
        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"

        val command = listOf(
            ytdlpPath,
            "--format", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
            "--merge-output-format", "mp4",
            "--output", outputPath,
            "--no-playlist",
            "--no-warnings",
            "--quiet",
            "--cookies-from-browser", "chrome",  // Chrome 쿠키 사용 (봇 방지)
            "--user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            youtubeUrl
        )

        logger.debug("yt-dlp 명령어: {}", command.joinToString(" "))

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(DOWNLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            if (!completed) {
                process.destroyForcibly()
                throw YtDlpException("Download timeout after $DOWNLOAD_TIMEOUT_MINUTES minutes: videoId=$videoId")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.error("yt-dlp 실패: exitCode={}, output={}", exitCode, errorOutput)
                throw YtDlpException("yt-dlp failed with exit code $exitCode: videoId=$videoId")
            }

            val outputFile = File(outputPath)
            if (!outputFile.exists()) {
                throw YtDlpException("Downloaded file not found: $outputPath")
            }

            logger.info("비디오 다운로드 완료: videoId={}, path={}, size={}MB",
                videoId, outputPath, outputFile.length() / (1024 * 1024))

            return outputPath

        } catch (e: YtDlpException) {
            throw e
        } catch (e: Exception) {
            logger.error("비디오 다운로드 실패: videoId={}", videoId, e)
            throw YtDlpException("Failed to download video: videoId=$videoId", e)
        }
    }

    override fun getVideoInfo(videoId: String): String? {
        logger.debug("비디오 정보 조회: videoId={}", videoId)

        val youtubeUrl = "https://www.youtube.com/watch?v=$videoId"

        val command = listOf(
            ytdlpPath,
            "--dump-json",
            "--no-warnings",
            "--quiet",
            "--cookies-from-browser", "chrome",  // Chrome 쿠키 사용 (봇 방지)
            "--user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
            youtubeUrl
        )

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val completed = process.waitFor(60, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                logger.warn("비디오 정보 조회 타임아웃: videoId={}", videoId)
                return null
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                logger.warn("비디오 정보 조회 실패: videoId={}, exitCode={}", videoId, exitCode)
                return null
            }

            return process.inputStream.bufferedReader().readText()

        } catch (e: Exception) {
            logger.error("비디오 정보 조회 실패: videoId={}", videoId, e)
            return null
        }
    }
}

/**
 * yt-dlp 예외
 */
class YtDlpException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
