package me.onetwo.upvy.crawler.client.video

import me.onetwo.upvy.crawler.domain.TranscriptSegment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File

/**
 * SRT 자막 파일 생성기
 *
 * TranscriptSegment 리스트를 SRT (SubRip Subtitle) 파일 형식으로 변환합니다.
 *
 * SRT 파일 형식:
 * ```
 * 1
 * 00:00:00,000 --> 00:00:03,000
 * 첫 번째 자막 텍스트
 *
 * 2
 * 00:00:03,000 --> 00:00:06,500
 * 두 번째 자막 텍스트
 * ```
 */
@Component
class SrtGenerator {

    companion object {
        private val logger = LoggerFactory.getLogger(SrtGenerator::class.java)
    }

    /**
     * TranscriptSegment 리스트를 SRT 파일로 변환
     *
     * @param segments 자막 세그먼트 리스트
     * @param outputPath 출력 SRT 파일 경로
     * @return 출력 파일 경로
     */
    fun generateSrtFile(segments: List<TranscriptSegment>, outputPath: String): String {
        logger.debug("SRT 파일 생성 시작: outputPath={}, segmentCount={}", outputPath, segments.size)

        val srtContent = buildString {
            segments.forEachIndexed { index, segment ->
                // 자막 번호 (1부터 시작)
                append("${index + 1}\n")

                // 타임스탬프 (시작 --> 종료)
                val startTime = formatTimestamp(segment.startTimeMs)

                // 다음 자막이 시작하기 전까지 현재 자막 유지
                val extendedEndTimeMs = if (index < segments.size - 1) {
                    // 다음 세그먼트가 있으면, 다음 세그먼트 시작 시점까지 유지
                    segments[index + 1].startTimeMs
                } else {
                    // 마지막 세그먼트는 원래 종료 시점 + 500ms 추가 (자연스러운 여유)
                    segment.endTimeMs + 500
                }

                val endTime = formatTimestamp(extendedEndTimeMs)
                append("$startTime --> $endTime\n")

                // 자막 텍스트
                append("${segment.text}\n")

                // 빈 줄 (구분자)
                append("\n")
            }
        }

        File(outputPath).writeText(srtContent, Charsets.UTF_8)
        logger.debug("SRT 파일 생성 완료: outputPath={}, fileSize={}", outputPath, srtContent.length)

        return outputPath
    }

    /**
     * 밀리초를 SRT 타임스탬프 형식으로 변환
     *
     * 형식: HH:MM:SS,mmm
     * 예시: 01:23:45,678
     *
     * @param ms 밀리초
     * @return SRT 타임스탬프 문자열
     */
    private fun formatTimestamp(ms: Long): String {
        val totalSeconds = ms / 1000
        val milliseconds = ms % 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds)
    }
}
