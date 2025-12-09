package me.onetwo.upvy.crawler.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 오디오 청크 정보
 */
data class AudioChunk(
    val filePath: String,
    val startTimeSeconds: Double,
    val durationSeconds: Double
)

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

    /**
     * S3 URL에서 오디오를 청크로 분할하여 추출
     *
     * @param s3Url S3 Presigned URL
     * @param chunkDurationSeconds 청크 길이 (초)
     * @return 추출된 오디오 청크 목록
     */
    fun extractAudioChunksFromUrl(s3Url: String, chunkDurationSeconds: Int = 55): List<AudioChunk>

    /**
     * URL에서 오디오 길이 확인
     *
     * @param url 오디오/비디오 URL
     * @return 오디오 길이 (초)
     */
    fun getAudioDuration(url: String): Double
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

    override fun extractAudioChunksFromUrl(s3Url: String, chunkDurationSeconds: Int): List<AudioChunk> {
        logger.info("URL에서 오디오 청크 추출 시작: url={}, chunkDuration={}초", s3Url, chunkDurationSeconds)

        // 전체 오디오 길이 확인
        val totalDuration = getAudioDuration(s3Url)
        logger.info("전체 오디오 길이: {}초", totalDuration)

        val chunks = mutableListOf<AudioChunk>()
        var currentTime = 0.0

        var chunkIndex = 0
        while (currentTime < totalDuration) {
            val remainingDuration = totalDuration - currentTime
            val chunkDuration = minOf(chunkDurationSeconds.toDouble(), remainingDuration)

            val outputPath = "$tempDir/audio_chunk_${chunkIndex}.ogg"

            // FFmpeg로 청크 추출 (-ss: 시작시간, -t: 지속시간)
            val command = listOf(
                ffmpegPath,
                "-ss", currentTime.toString(),
                "-i", s3Url,
                "-t", chunkDuration.toString(),
                "-vn",
                "-acodec", "libopus",
                "-ar", "16000",
                "-ac", "1",
                "-b:a", "32k",
                "-y",
                outputPath
            )

            executeCommand(command, "Audio chunk extraction")

            chunks.add(AudioChunk(
                filePath = outputPath,
                startTimeSeconds = currentTime,
                durationSeconds = chunkDuration
            ))

            logger.info("청크 추출 완료: index={}, start={}초, duration={}초, output={}",
                chunkIndex, currentTime, chunkDuration, outputPath)

            currentTime += chunkDuration
            chunkIndex++
        }

        logger.info("모든 청크 추출 완료: 총 {}개 청크", chunks.size)
        return chunks
    }

    override fun getAudioDuration(url: String): Double {
        logger.debug("오디오 길이 확인: url={}", url)

        // ffprobe를 사용하여 오디오 길이 확인
        val ffprobePath = ffmpegPath.replace("ffmpeg", "ffprobe")
        val command = listOf(
            ffprobePath,
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            url
        )

        logger.debug("FFprobe 명령어: {}", command.joinToString(" "))

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(30, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                throw AudioExtractException("FFprobe timeout")
            }

            val exitCode = process.exitValue()
            if (exitCode != 0) {
                val errorOutput = process.inputStream.bufferedReader().readText()
                logger.error("FFprobe 실패: exitCode={}, output={}", exitCode, errorOutput)
                throw AudioExtractException("FFprobe failed with exit code $exitCode")
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            val duration = output.toDoubleOrNull()
                ?: throw AudioExtractException("Failed to parse duration: $output")

            logger.debug("오디오 길이 확인 완료: {}초", duration)
            return duration

        } catch (e: AudioExtractException) {
            throw e
        } catch (e: Exception) {
            logger.error("FFprobe 실행 실패", e)
            throw AudioExtractException("FFprobe execution failed", e)
        }
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
