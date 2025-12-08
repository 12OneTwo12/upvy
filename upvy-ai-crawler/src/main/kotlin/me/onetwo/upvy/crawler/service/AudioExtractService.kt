package me.onetwo.upvy.crawler.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 오디오 추출 서비스 인터페이스
 *
 * FFmpeg를 사용하여 비디오에서 오디오를 추출합니다.
 */
interface AudioExtractService {

    /**
     * 비디오에서 오디오 추출
     *
     * @param videoPath 비디오 파일 경로
     * @return 추출된 오디오 파일 경로
     */
    fun extractAudio(videoPath: String): String

    /**
     * S3 URL에서 오디오 추출 (다운로드 후 추출)
     *
     * @param s3Url S3 Presigned URL
     * @param outputPath 출력 파일 경로
     * @return 추출된 오디오 파일 경로
     */
    fun extractAudioFromUrl(s3Url: String, outputPath: String): String
}

/**
 * 오디오 추출 서비스 구현체
 */
@Service
class AudioExtractServiceImpl(
    @Value("\${ffmpeg.path:/usr/bin/ffmpeg}") private val ffmpegPath: String,
    @Value("\${ffmpeg.temp-dir:/tmp/ai-crawler}") private val tempDir: String
) : AudioExtractService {

    companion object {
        private val logger = LoggerFactory.getLogger(AudioExtractServiceImpl::class.java)
        private const val EXTRACT_TIMEOUT_MINUTES = 10L
    }

    init {
        File(tempDir).mkdirs()
    }

    override fun extractAudio(videoPath: String): String {
        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            throw AudioExtractException("Video file not found: $videoPath")
        }

        val audioPath = "${tempDir}/${videoFile.nameWithoutExtension}.ogg"
        logger.info("오디오 추출 시작: video={}, audio={}", videoPath, audioPath)

        val command = listOf(
            ffmpegPath,
            "-i", videoPath,
            "-vn",                    // 비디오 제거
            "-acodec", "libopus",     // Opus 코덱 (음성에 최적화, 작은 파일 크기)
            "-ar", "16000",           // 샘플레이트 16kHz (STT 최적화)
            "-ac", "1",               // 모노
            "-b:a", "32k",            // 비트레이트 (음성에 충분)
            "-y",                     // 덮어쓰기
            audioPath
        )

        executeCommand(command, "Audio extraction")

        logger.info("오디오 추출 완료: audio={}", audioPath)
        return audioPath
    }

    override fun extractAudioFromUrl(s3Url: String, outputPath: String): String {
        logger.info("URL에서 오디오 추출 시작: url={}, output={}", s3Url, outputPath)

        val command = listOf(
            ffmpegPath,
            "-i", s3Url,
            "-vn",
            "-acodec", "libopus",     // Opus 코덱 (음성에 최적화, 작은 파일 크기)
            "-ar", "16000",
            "-ac", "1",
            "-b:a", "32k",            // 비트레이트 (음성에 충분)
            "-y",
            outputPath
        )

        executeCommand(command, "Audio extraction from URL")

        logger.info("URL에서 오디오 추출 완료: output={}", outputPath)
        return outputPath
    }

    /**
     * FFmpeg 명령어 실행
     */
    private fun executeCommand(command: List<String>, operation: String) {
        logger.debug("FFmpeg 명령어: {}", command.joinToString(" "))

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(EXTRACT_TIMEOUT_MINUTES, TimeUnit.MINUTES)

            if (!completed) {
                process.destroyForcibly()
                throw AudioExtractException("$operation timeout after $EXTRACT_TIMEOUT_MINUTES minutes")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.error("FFmpeg 실패: exitCode={}, output={}", exitCode, errorOutput)
                throw AudioExtractException("$operation failed with exit code $exitCode")
            }

        } catch (e: AudioExtractException) {
            throw e
        } catch (e: Exception) {
            logger.error("FFmpeg 실행 실패", e)
            throw AudioExtractException("$operation failed", e)
        }
    }
}

/**
 * 오디오 추출 예외
 */
class AudioExtractException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
