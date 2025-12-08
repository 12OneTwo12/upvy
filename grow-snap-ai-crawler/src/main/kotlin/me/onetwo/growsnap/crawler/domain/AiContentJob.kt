package me.onetwo.growsnap.crawler.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * AI 콘텐츠 생성 작업 엔티티
 *
 * YouTube 비디오 크롤링부터 콘텐츠 게시까지의 전체 파이프라인 상태를 관리합니다.
 */
@Entity
@Table(name = "ai_content_job")
data class AiContentJob(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "youtube_video_id", nullable = false, length = 20)
    val youtubeVideoId: String,

    @Column(name = "youtube_channel_id", length = 50)
    val youtubeChannelId: String? = null,

    @Column(name = "youtube_channel_title", length = 200)
    val youtubeChannelTitle: String? = null,

    @Column(name = "youtube_title", length = 500)
    val youtubeTitle: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: JobStatus = JobStatus.PENDING,

    @Column(name = "quality_score")
    val qualityScore: Int? = null,

    @Column(name = "raw_video_s3_key", length = 500)
    val rawVideoS3Key: String? = null,

    @Column(name = "edited_video_s3_key", length = 500)
    val editedVideoS3Key: String? = null,

    @Column(name = "thumbnail_s3_key", length = 500)
    val thumbnailS3Key: String? = null,

    @Column(columnDefinition = "TEXT")
    val transcript: String? = null,

    @Column(name = "transcript_segments", columnDefinition = "JSON")
    val transcriptSegments: String? = null,  // JSON: List<TranscriptSegment> - STT 타임스탬프 정보

    @Column(name = "generated_title", length = 200)
    val generatedTitle: String? = null,

    @Column(name = "generated_description", columnDefinition = "TEXT")
    val generatedDescription: String? = null,

    @Column(name = "generated_tags", columnDefinition = "JSON")
    val generatedTags: String? = null,  // JSON 문자열로 저장

    @Column(name = "segments", columnDefinition = "JSON")
    val segments: String? = null,  // JSON 문자열: List<Segment> - LLM이 추출한 핵심 구간

    @Column(length = 50)
    val category: String? = null,

    @Enumerated(EnumType.STRING)
    val difficulty: Difficulty? = null,

    @Column(name = "llm_provider", length = 20)
    val llmProvider: String? = null,

    @Column(name = "llm_model", length = 50)
    val llmModel: String? = null,

    @Column(name = "stt_provider", length = 20)
    val sttProvider: String? = null,

    @Column(length = 5)
    val language: String? = null,  // 콘텐츠 언어 (ko, en, ja)

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "reviewed_by")
    val reviewedBy: Long? = null,

    @Column(name = "reviewed_at")
    val reviewedAt: Instant? = null,

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    val rejectionReason: String? = null,

    @Column(name = "published_content_id")
    val publishedContentId: Long? = null,

    // Audit Trail 필드 (필수)
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by", length = 50)
    val createdBy: String? = "SYSTEM",

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", length = 50)
    val updatedBy: String? = "SYSTEM",

    @Column(name = "deleted_at")
    val deletedAt: Instant? = null  // Soft Delete
)

/**
 * AI 콘텐츠 작업 상태
 */
enum class JobStatus {
    PENDING,           // 대기 중
    CRAWLED,           // 다운로드 완료
    TRANSCRIBED,       // 음성-텍스트 변환 완료
    ANALYZED,          // LLM 분석 완료
    EDITED,            // 비디오 편집 완료
    PENDING_APPROVAL,  // 관리자 승인 대기
    APPROVED,          // 승인됨
    REJECTED,          // 거부됨
    PUBLISHED,         // 게시 완료
    FAILED             // 실패
}

/**
 * 콘텐츠 난이도
 */
enum class Difficulty(val displayName: String) {
    BEGINNER("입문"),
    INTERMEDIATE("중급"),
    ADVANCED("고급")
}
