package me.onetwo.upvy.crawler.client.video

import me.onetwo.upvy.crawler.domain.TranscriptSegment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * SrtGenerator 테스트
 *
 * TranscriptSegment 리스트를 SRT 파일로 변환하는 기능을 검증합니다.
 */
@DisplayName("SrtGenerator 테스트")
class SrtGeneratorTest {

    private val generator = SrtGenerator()

    @Nested
    @DisplayName("generateSrtFile - SRT 파일 생성")
    inner class GenerateSrtFile {

        @Test
        @DisplayName("TranscriptSegment 리스트를 SRT 파일로 변환한다")
        fun generateSrtFile_WithSegmentList_CreatesValidSrtFile(@TempDir tempDir: Path) {
            // Given: 자막 세그먼트 리스트
            val segments = listOf(
                TranscriptSegment(
                    startTimeMs = 0,
                    endTimeMs = 3000,
                    text = "안녕하세요"
                ),
                TranscriptSegment(
                    startTimeMs = 3000,
                    endTimeMs = 6500,
                    text = "오늘은 FFmpeg에 대해 알아보겠습니다"
                ),
                TranscriptSegment(
                    startTimeMs = 6500,
                    endTimeMs = 10000,
                    text = "자막 테스트입니다"
                )
            )
            val outputPath = tempDir.resolve("test.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 파일이 생성되고 내용이 올바른 SRT 형식이다
            assertThat(File(result)).exists()
            val content = File(result).readText(Charsets.UTF_8)

            // SRT 형식 검증
            assertThat(content).contains("1\n")
            assertThat(content).contains("00:00:00,000 --> 00:00:03,000")
            assertThat(content).contains("안녕하세요")

            assertThat(content).contains("2\n")
            assertThat(content).contains("00:00:03,000 --> 00:00:06,500")
            assertThat(content).contains("오늘은 FFmpeg에 대해 알아보겠습니다")

            assertThat(content).contains("3\n")
            assertThat(content).contains("00:00:06,500 --> 00:00:10,000")
            assertThat(content).contains("자막 테스트입니다")
        }

        @Test
        @DisplayName("빈 리스트로 호출 시, 빈 SRT 파일을 생성한다")
        fun generateSrtFile_WithEmptyList_CreatesEmptyFile(@TempDir tempDir: Path) {
            // Given: 빈 세그먼트 리스트
            val segments = emptyList<TranscriptSegment>()
            val outputPath = tempDir.resolve("empty.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 빈 파일이 생성된다
            assertThat(File(result)).exists()
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).isEmpty()
        }

        @Test
        @DisplayName("밀리초 타임스탬프를 SRT 형식으로 올바르게 변환한다")
        fun generateSrtFile_WithMilliseconds_FormatsTimestampCorrectly(@TempDir tempDir: Path) {
            // Given: 다양한 타임스탬프를 가진 세그먼트
            val segments = listOf(
                TranscriptSegment(
                    startTimeMs = 3661500,  // 1시간 1분 1초 500ms
                    endTimeMs = 7322999,    // 2시간 2분 2초 999ms
                    text = "타임스탬프 테스트"
                )
            )
            val outputPath = tempDir.resolve("timestamp.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 타임스탬프가 올바르게 포맷팅된다
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).contains("01:01:01,500 --> 02:02:02,999")
        }

        @Test
        @DisplayName("한글, 영어, 일본어 텍스트를 UTF-8로 올바르게 저장한다")
        fun generateSrtFile_WithMultiLanguageText_SavesAsUtf8(@TempDir tempDir: Path) {
            // Given: 다국어 텍스트를 가진 세그먼트
            val segments = listOf(
                TranscriptSegment(startTimeMs = 0, endTimeMs = 2000, text = "한글 자막"),
                TranscriptSegment(startTimeMs = 2000, endTimeMs = 4000, text = "English subtitle"),
                TranscriptSegment(startTimeMs = 4000, endTimeMs = 6000, text = "日本語字幕")
            )
            val outputPath = tempDir.resolve("multilang.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 모든 언어가 깨지지 않고 저장된다
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).contains("한글 자막")
            assertThat(content).contains("English subtitle")
            assertThat(content).contains("日本語字幕")
        }

        @Test
        @DisplayName("특수문자가 포함된 텍스트를 올바르게 처리한다")
        fun generateSrtFile_WithSpecialCharacters_HandlesCorrectly(@TempDir tempDir: Path) {
            // Given: 특수문자가 포함된 세그먼트
            val segments = listOf(
                TranscriptSegment(
                    startTimeMs = 0,
                    endTimeMs = 3000,
                    text = "특수문자 테스트: !@#$%^&*()[]{}:;'\"<>,.?/"
                ),
                TranscriptSegment(
                    startTimeMs = 3000,
                    endTimeMs = 6000,
                    text = "줄바꿈\n테스트"
                )
            )
            val outputPath = tempDir.resolve("special.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 특수문자가 그대로 저장된다
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).contains("특수문자 테스트: !@#$%^&*()[]{}:;'\"<>,.?/")
            assertThat(content).contains("줄바꿈\n테스트")
        }
    }

    @Nested
    @DisplayName("formatTimestamp - 타임스탬프 포맷팅 (private 메서드 간접 테스트)")
    inner class FormatTimestamp {

        @Test
        @DisplayName("0밀리초를 00:00:00,000으로 변환한다")
        fun formatTimestamp_WithZero_ReturnsCorrectFormat(@TempDir tempDir: Path) {
            // Given: 0밀리초 세그먼트
            val segments = listOf(
                TranscriptSegment(startTimeMs = 0, endTimeMs = 0, text = "test")
            )
            val outputPath = tempDir.resolve("zero.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 올바른 형식으로 변환된다
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).contains("00:00:00,000 --> 00:00:00,000")
        }

        @Test
        @DisplayName("59초 999밀리초를 00:00:59,999으로 변환한다")
        fun formatTimestamp_WithSeconds_ReturnsCorrectFormat(@TempDir tempDir: Path) {
            // Given: 초 단위 세그먼트
            val segments = listOf(
                TranscriptSegment(startTimeMs = 59999, endTimeMs = 59999, text = "test")
            )
            val outputPath = tempDir.resolve("seconds.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 올바른 형식으로 변환된다
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).contains("00:00:59,999")
        }

        @Test
        @DisplayName("59분 59초 999밀리초를 00:59:59,999으로 변환한다")
        fun formatTimestamp_WithMinutes_ReturnsCorrectFormat(@TempDir tempDir: Path) {
            // Given: 분 단위 세그먼트
            val segments = listOf(
                TranscriptSegment(startTimeMs = 3599999, endTimeMs = 3599999, text = "test")
            )
            val outputPath = tempDir.resolve("minutes.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 올바른 형식으로 변환된다
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).contains("00:59:59,999")
        }

        @Test
        @DisplayName("99시간 59분 59초 999밀리초를 99:59:59,999으로 변환한다")
        fun formatTimestamp_WithHours_ReturnsCorrectFormat(@TempDir tempDir: Path) {
            // Given: 시간 단위 세그먼트
            val segments = listOf(
                TranscriptSegment(startTimeMs = 359999999, endTimeMs = 359999999, text = "test")
            )
            val outputPath = tempDir.resolve("hours.srt").toString()

            // When: SRT 파일 생성
            val result = generator.generateSrtFile(segments, outputPath)

            // Then: 올바른 형식으로 변환된다
            val content = File(result).readText(Charsets.UTF_8)
            assertThat(content).contains("99:59:59,999")
        }
    }
}
