package me.onetwo.growsnap.crawler.domain

/**
 * YouTube 비디오 후보
 *
 * YouTube Data API에서 검색된 CC 라이선스 비디오 정보
 */
data class VideoCandidate(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelTitle: String? = null,
    val description: String? = null,
    val publishedAt: String? = null,
    val duration: String? = null,  // ISO 8601 duration (PT1H2M3S)
    val thumbnailUrl: String? = null
)

/**
 * 핵심 구간 (세그먼트)
 *
 * LLM이 추출한 학습 가치가 높은 비디오 구간
 */
data class Segment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String,
    val description: String? = null,
    val keywords: List<String> = emptyList()
)

/**
 * 음성-텍스트 변환 결과
 */
data class TranscriptResult(
    val text: String,
    val segments: List<TranscriptSegment> = emptyList(),
    val language: String? = null,
    val confidence: Float? = null
)

/**
 * 자막 세그먼트 (타임스탬프 포함)
 */
data class TranscriptSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

/**
 * AI가 생성한 콘텐츠 메타데이터
 */
data class ContentMetadata(
    val title: String,
    val description: String,
    val tags: List<String>,
    val category: String,
    val difficulty: Difficulty
)

/**
 * AI 콘텐츠 세그먼트 엔티티
 *
 * 한 영상에서 추출된 여러 클립 정보
 */
data class AiContentSegment(
    val id: Long? = null,
    val jobId: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val title: String? = null,
    val description: String? = null,
    val keywords: List<String> = emptyList(),
    val s3Key: String? = null,
    val thumbnailS3Key: String? = null,
    val qualityScore: Int? = null,
    val isSelected: Boolean = false
)
