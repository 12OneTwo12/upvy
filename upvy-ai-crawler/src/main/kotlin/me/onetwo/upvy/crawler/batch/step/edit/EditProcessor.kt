package me.onetwo.upvy.crawler.batch.step.edit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import me.onetwo.upvy.crawler.client.video.FFmpegWrapper
import me.onetwo.upvy.crawler.client.video.SrtGenerator
import me.onetwo.upvy.crawler.domain.AiContentJob
import me.onetwo.upvy.crawler.domain.ClipSegment
import me.onetwo.upvy.crawler.domain.EditPlan
import me.onetwo.upvy.crawler.domain.JobStatus
import me.onetwo.upvy.crawler.domain.Segment
import me.onetwo.upvy.crawler.domain.TranscriptSegment
import me.onetwo.upvy.crawler.service.S3Service
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.File
import java.net.URL
import java.time.Instant

/**
 * EditStep Processor
 *
 * 비디오를 클리핑하고 쇼츠 포맷으로 변환한 후 S3에 업로드합니다.
 */
@Component
class EditProcessor(
    private val ffmpegWrapper: FFmpegWrapper,
    private val s3Service: S3Service,
    private val srtGenerator: SrtGenerator,
    @Value("\${ffmpeg.temp-dir:/tmp/ai-crawler}") private val tempDir: String,
    @Value("\${ffmpeg.font-path:/System/Library/Fonts/Supplemental/Arial Unicode.ttf}") private val fontPath: String,
    @Value("\${s3.prefix.edited-videos}") private val editedVideosPrefix: String,
    @Value("\${s3.prefix.thumbnails}") private val thumbnailsPrefix: String
) : ItemProcessor<AiContentJob, AiContentJob> {

    companion object {
        private val logger = LoggerFactory.getLogger(EditProcessor::class.java)
        private val objectMapper = jacksonObjectMapper()
        private const val DEFAULT_CLIP_START_MS = 30000L   // 30초부터 시작 (fallback)
        private const val DEFAULT_CLIP_DURATION_MS = 60000L // 60초 클립 (fallback)
        private const val MAX_CLIP_DURATION_MS = 210000L   // 최대 3분 30초
        private const val MIN_CLIP_DURATION_MS = 30000L    // 최소 30초
    }

    override fun process(job: AiContentJob): AiContentJob? {
        logger.info("Edit 시작: jobId={}, videoId={}", job.id, job.youtubeVideoId)

        if (job.rawVideoS3Key == null) {
            logger.warn("rawVideoS3Key가 없음: jobId={}", job.id)
            return null
        }

        val tempFilesToCleanup = mutableListOf<String>()

        try {
            // 1. S3에서 원본 비디오 다운로드
            val presignedUrl = s3Service.generatePresignedUrl(job.rawVideoS3Key!!)
            val originalVideoPath = downloadFromUrl(presignedUrl, "${tempDir}/edit_${job.id}.mp4")
            tempFilesToCleanup.add(originalVideoPath)
            logger.debug("원본 비디오 다운로드 완료: jobId={}, path={}", job.id, originalVideoPath)

            // 2. 비디오 정보 조회
            val videoInfo = ffmpegWrapper.getVideoInfo(originalVideoPath)
            logger.debug("비디오 정보: duration={}ms, width={}, height={}",
                videoInfo.durationMs, videoInfo.width, videoInfo.height)

            // 3. EditPlan 파싱
            var editPlan = parseEditPlan(job.segments)
            if (editPlan == null) {
                logger.warn("EditPlan이 없음, 기본 클립 생성")
                // Fallback: 영상 전체 또는 기본 범위 사용
                val startMs = if (videoInfo.durationMs > MIN_CLIP_DURATION_MS + DEFAULT_CLIP_START_MS) {
                    DEFAULT_CLIP_START_MS
                } else {
                    0L
                }
                val endMs = (startMs + DEFAULT_CLIP_DURATION_MS).coerceAtMost(videoInfo.durationMs)
                editPlan = EditPlan(
                    clips = listOf(ClipSegment(0, startMs, endMs, "기본 클립")),
                    totalDurationMs = endMs - startMs,
                    editingStrategy = "fallback",
                    transitionStyle = "hard_cut"
                )
            }

            logger.info("EditPlan: {} 클립, 전략={}, 총 {}ms",
                editPlan.clips.size, editPlan.editingStrategy, editPlan.totalDurationMs)

            // 4. EditPlan 유효성 검증 및 Fallback
            if (!validateEditPlan(editPlan, videoInfo.durationMs)) {
                logger.warn("EditPlan 검증 실패, 기본 클립 생성")

                // EditPlan이 완전히 비어있거나 유효하지 않으면 기본 클립 생성
                val startMs = if (videoInfo.durationMs > MIN_CLIP_DURATION_MS + DEFAULT_CLIP_START_MS) {
                    DEFAULT_CLIP_START_MS
                } else {
                    0L
                }
                val endMs = (startMs + DEFAULT_CLIP_DURATION_MS).coerceAtMost(videoInfo.durationMs)

                editPlan = EditPlan(
                    clips = listOf(ClipSegment(
                        orderIndex = 0,
                        startTimeMs = startMs,
                        endTimeMs = endMs,
                        title = "기본 클립",
                        description = "EditPlan 생성 실패로 인한 기본 클립"
                    )),
                    totalDurationMs = endMs - startMs,
                    editingStrategy = "fallback_default",
                    transitionStyle = "hard_cut"
                )
                logger.info("기본 클립 생성: {}ms ~ {}ms", startMs, endMs)
            }

            // 5. 전체 자막 파싱
            val allSubtitles = parseTranscriptSegments(job.transcriptSegments)
            logger.debug("전체 자막: {} 세그먼트", allSubtitles.size)

            // 6. 각 클립 처리 (orderIndex 순서대로)
            val clipPaths = mutableListOf<String>()
            val srtPaths = mutableListOf<String>()
            val clipDurations = mutableListOf<Long>()

            editPlan.clips.sortedBy { it.orderIndex }.forEachIndexed { index, clip ->
                logger.info("클립 #{} 처리: orderIndex={}, {}ms ~ {}ms, title={}",
                    index + 1, clip.orderIndex, clip.startTimeMs, clip.endTimeMs, clip.title)

                // 6-1. 클립 추출
                val clipPath = "${tempDir}/clip_${job.id}_${index}.mp4"
                ffmpegWrapper.clip(originalVideoPath, clipPath, clip.startTimeMs, clip.endTimeMs)
                clipPaths.add(clipPath)
                tempFilesToCleanup.add(clipPath)
                clipDurations.add(clip.endTimeMs - clip.startTimeMs)
                logger.debug("클립 추출 완료: {}", clipPath)

                // 6-2. 클립 자막 추출 및 조정
                if (allSubtitles.isNotEmpty()) {
                    val clipSubtitles = extractClipSubtitles(allSubtitles, clip.startTimeMs, clip.endTimeMs)
                    if (clipSubtitles.isNotEmpty()) {
                        val srtPath = "${tempDir}/subtitle_${job.id}_${index}.srt"
                        srtGenerator.generateSrtFile(clipSubtitles, srtPath)
                        srtPaths.add(srtPath)
                        tempFilesToCleanup.add(srtPath)
                        logger.debug("클립 자막 생성: {} ({} 세그먼트)", srtPath, clipSubtitles.size)
                    }
                }
            }

            // 7. 클립 병합 (2개 이상인 경우)
            val concatenatedPath = if (clipPaths.size > 1) {
                val concatPath = "${tempDir}/concatenated_${job.id}.mp4"
                ffmpegWrapper.concat(clipPaths, concatPath)
                tempFilesToCleanup.add(concatPath)
                logger.info("클립 병합 완료: {} 클립 → {}", clipPaths.size, concatPath)
                concatPath
            } else {
                logger.debug("단일 클립, 병합 스킵")
                clipPaths.first()
            }

            // 8. 자막 병합 (2개 이상인 경우)
            val finalSrtPath = if (srtPaths.size > 1) {
                val mergedSrtPath = "${tempDir}/subtitle_merged_${job.id}.srt"
                mergeSubtitleFiles(clipDurations, srtPaths, mergedSrtPath)
                tempFilesToCleanup.add(mergedSrtPath)
                logger.info("자막 병합 완료: {} 파일 → {}", srtPaths.size, mergedSrtPath)
                mergedSrtPath
            } else {
                srtPaths.firstOrNull()
            }

            // 9. 세로 포맷으로 리사이징 (9:16)
            val resizedPath = "${tempDir}/resized_${job.id}.mp4"
            ffmpegWrapper.resizeVertical(concatenatedPath, resizedPath)
            tempFilesToCleanup.add(resizedPath)
            logger.debug("세로 리사이징 완료: {}", resizedPath)

            // 10. 텍스트 오버레이 추가 (제목 + 자막)
            val overlayedPath = "${tempDir}/overlayed_${job.id}.mp4"
            val title = job.generatedTitle ?: "교육 콘텐츠"  // fallback
            ffmpegWrapper.addTextOverlay(resizedPath, overlayedPath, title, finalSrtPath, fontPath)
            tempFilesToCleanup.add(overlayedPath)
            logger.debug("텍스트 오버레이 완료: {}", overlayedPath)

            // 11. 썸네일 추출 (최종 영상 중간 지점)
            val thumbnailPath = "${tempDir}/thumb_${job.id}.jpg"
            val thumbnailTimeMs = editPlan.totalDurationMs / 2
            ffmpegWrapper.thumbnail(overlayedPath, thumbnailPath, thumbnailTimeMs)
            tempFilesToCleanup.add(thumbnailPath)
            logger.debug("썸네일 추출 완료: {}", thumbnailPath)

            // 12. S3 업로드
            val editedS3Key = "$editedVideosPrefix/clips/${job.youtubeVideoId}/${job.id}.mp4"
            s3Service.upload(overlayedPath, editedS3Key, publicRead = true)
            logger.debug("편집 비디오 S3 업로드 완료: {}", editedS3Key)

            val thumbnailS3Key = "$thumbnailsPrefix/${job.youtubeVideoId}/${job.id}.jpg"
            s3Service.upload(thumbnailPath, thumbnailS3Key, publicRead = true)
            logger.debug("썸네일 S3 업로드 완료: {}", thumbnailS3Key)

            // 13. 임시 파일 정리
            cleanupTempFiles(tempFilesToCleanup)

            // 14. Job 업데이트
            val now = Instant.now()
            return job.copy(
                editedVideoS3Key = editedS3Key,
                thumbnailS3Key = thumbnailS3Key,
                status = JobStatus.EDITED,
                updatedAt = now,
                updatedBy = "SYSTEM"
            )

        } catch (e: Exception) {
            logger.error("Edit 실패: jobId={}, error={}", job.id, e.message, e)
            // 실패 시에도 임시 파일 정리 시도
            cleanupTempFiles(tempFilesToCleanup)
            return null
        }
    }

    /**
     * EditPlan JSON 파싱 (하위 호환성 지원)
     *
     * - 새로운 EditPlan 형식: {"clips": [...], "totalDurationMs": ..., ...}
     * - 기존 Segment 리스트 형식: [{"startTimeMs": ..., "endTimeMs": ..., ...}, ...]
     */
    private fun parseEditPlan(segmentsJson: String?): EditPlan? {
        if (segmentsJson.isNullOrBlank()) {
            logger.debug("EditPlan 정보 없음")
            return null
        }

        return try {
            val json = objectMapper.readTree(segmentsJson)

            if (json.has("clips")) {
                // 새로운 EditPlan 형식
                val editPlan = objectMapper.readValue<EditPlan>(segmentsJson)
                logger.info("EditPlan 파싱 완료: {} 클립, 전략={}", editPlan.clips.size, editPlan.editingStrategy)
                editPlan
            } else if (json.isArray && json.size() > 0) {
                // 기존 Segment 리스트 형식 (하위 호환성)
                val segments = objectMapper.readValue<List<Segment>>(segmentsJson)
                val first = segments.first()
                logger.info("기존 Segment 형식 감지, EditPlan으로 변환: {}ms ~ {}ms", first.startTimeMs, first.endTimeMs)

                EditPlan(
                    clips = listOf(ClipSegment(
                        orderIndex = 0,
                        startTimeMs = first.startTimeMs,
                        endTimeMs = first.endTimeMs,
                        title = first.title,
                        description = first.description,
                        keywords = first.keywords
                    )),
                    totalDurationMs = first.endTimeMs - first.startTimeMs,
                    editingStrategy = "single_clip",
                    transitionStyle = "hard_cut"
                )
            } else {
                logger.warn("알 수 없는 세그먼트 형식")
                null
            }
        } catch (e: Exception) {
            logger.warn("EditPlan 파싱 실패: {}", e.message, e)
            null
        }
    }

    /**
     * EditPlan 유효성 검증
     */
    private fun validateEditPlan(editPlan: EditPlan, videoDurationMs: Long): Boolean {
        if (editPlan.clips.isEmpty()) {
            logger.warn("EditPlan에 클립이 없음")
            return false
        }

        if (editPlan.clips.size > 10) {
            logger.warn("EditPlan 클립이 너무 많음: {}", editPlan.clips.size)
            return false
        }

        editPlan.clips.forEach { clip ->
            if (clip.startTimeMs < 0 || clip.endTimeMs > videoDurationMs) {
                logger.warn("클립 타임스탬프가 비디오 범위를 벗어남: {}ms ~ {}ms (비디오: {}ms)",
                    clip.startTimeMs, clip.endTimeMs, videoDurationMs)
                return false
            }
            if (clip.endTimeMs <= clip.startTimeMs) {
                logger.warn("클립 종료 시간이 시작 시간보다 이전: {}ms ~ {}ms", clip.startTimeMs, clip.endTimeMs)
                return false
            }
            if (clip.endTimeMs - clip.startTimeMs < 10000) {
                logger.warn("클립이 너무 짧음 (10초 미만): {}ms", clip.endTimeMs - clip.startTimeMs)
                return false
            }
        }

        if (editPlan.totalDurationMs > MAX_CLIP_DURATION_MS) {
            logger.warn("EditPlan 전체 길이가 너무 김: {}ms", editPlan.totalDurationMs)
            return false
        }

        return true
    }

    /**
     * TranscriptSegments JSON 파싱
     */
    private fun parseTranscriptSegments(transcriptSegmentsJson: String?): List<TranscriptSegment> {
        if (transcriptSegmentsJson.isNullOrBlank()) {
            return emptyList()
        }

        return try {
            objectMapper.readValue<List<TranscriptSegment>>(transcriptSegmentsJson)
        } catch (e: Exception) {
            logger.warn("TranscriptSegments 파싱 실패: {}", e.message)
            emptyList()
        }
    }

    /**
     * 클립에 해당하는 자막 세그먼트 추출 및 타임스탬프 조정
     *
     * 예시:
     * - 원본 자막: 30000ms~35000ms "변수를 선언합니다"
     * - 클립 범위: 30000ms~40000ms
     * - 결과: 0ms~5000ms "변수를 선언합니다" (0부터 시작하도록 조정)
     */
    private fun extractClipSubtitles(
        allSubtitles: List<TranscriptSegment>,
        clipStartMs: Long,
        clipEndMs: Long
    ): List<TranscriptSegment> {
        return allSubtitles
            .filter { it.endTimeMs > clipStartMs && it.startTimeMs < clipEndMs }
            .map { segment ->
                TranscriptSegment(
                    startTimeMs = (segment.startTimeMs - clipStartMs).coerceAtLeast(0L),
                    endTimeMs = (segment.endTimeMs - clipStartMs).coerceAtMost(clipEndMs - clipStartMs),
                    text = segment.text
                )
            }
            .filter { it.endTimeMs > it.startTimeMs }
    }

    /**
     * SRT 파일 파싱
     */
    private fun parseSrtFile(srtPath: String): List<TranscriptSegment> {
        if (!File(srtPath).exists()) {
            return emptyList()
        }

        val content = File(srtPath).readText(Charsets.UTF_8)
        val segments = mutableListOf<TranscriptSegment>()

        // SRT 형식 정규표현식:
        // 1
        // 00:00:00,000 --> 00:00:05,000
        // 텍스트 내용
        val pattern = """(\d+)\s+(\d{2}):(\d{2}):(\d{2}),(\d{3})\s+-->\s+(\d{2}):(\d{2}):(\d{2}),(\d{3})\s+(.+?)(?=\n\n|\n\d+\n|\z)"""
            .toRegex(RegexOption.DOT_MATCHES_ALL)

        pattern.findAll(content).forEach { match ->
            val (_, startH, startM, startS, startMs, endH, endM, endS, endMs, text) = match.destructured
            val startTimeMs = startH.toLong() * 3600000 + startM.toLong() * 60000 + startS.toLong() * 1000 + startMs.toLong()
            val endTimeMs = endH.toLong() * 3600000 + endM.toLong() * 60000 + endS.toLong() * 1000 + endMs.toLong()
            segments.add(TranscriptSegment(startTimeMs, endTimeMs, text.trim()))
        }

        return segments
    }

    /**
     * 여러 SRT 파일을 하나로 병합하며 타임스탬프 조정
     *
     * 예시:
     * - clip_0 (10초): 0~5초 "안녕하세요", 5~10초 "변수입니다"
     * - clip_1 (10초): 0~5초 "함수입니다", 5~10초 "사용합니다"
     *
     * 병합 결과:
     * - 0~5초 "안녕하세요"
     * - 5~10초 "변수입니다"
     * - 10~15초 "함수입니다" ← 10초 누적
     * - 15~20초 "사용합니다" ← 10초 누적
     */
    private fun mergeSubtitleFiles(
        clipDurationsMs: List<Long>,
        srtPaths: List<String>,
        outputPath: String
    ): String {
        val mergedSegments = mutableListOf<TranscriptSegment>()
        var cumulativeTimeMs = 0L

        srtPaths.forEachIndexed { index, srtPath ->
            if (!File(srtPath).exists()) {
                logger.debug("SRT 파일 없음, 스킵: {}", srtPath)
                cumulativeTimeMs += clipDurationsMs[index]
                return@forEachIndexed
            }

            val segments = parseSrtFile(srtPath)
            logger.debug("SRT 파일 파싱: {} ({} 세그먼트)", srtPath, segments.size)

            // 누적 시간 추가
            val adjusted = segments.map { segment ->
                TranscriptSegment(
                    startTimeMs = segment.startTimeMs + cumulativeTimeMs,
                    endTimeMs = segment.endTimeMs + cumulativeTimeMs,
                    text = segment.text
                )
            }

            mergedSegments.addAll(adjusted)
            cumulativeTimeMs += clipDurationsMs[index]
        }

        logger.info("자막 병합 완료: {} 파일 → {} 세그먼트", srtPaths.size, mergedSegments.size)
        srtGenerator.generateSrtFile(mergedSegments, outputPath)
        return outputPath
    }

    /**
     * URL에서 파일 다운로드
     */
    private fun downloadFromUrl(url: URL, outputPath: String): String {
        File(tempDir).mkdirs()

        url.openStream().use { input ->
            File(outputPath).outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return outputPath
    }

    /**
     * 임시 파일 정리
     */
    private fun cleanupTempFiles(paths: List<String>) {
        paths.forEach { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                logger.warn("임시 파일 삭제 실패: path={}", path, e)
            }
        }
    }
}
