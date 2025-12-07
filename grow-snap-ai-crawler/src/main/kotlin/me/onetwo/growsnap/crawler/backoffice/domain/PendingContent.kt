package me.onetwo.growsnap.crawler.backoffice.domain

import jakarta.persistence.*
import me.onetwo.growsnap.crawler.domain.Difficulty
import me.onetwo.growsnap.crawler.domain.ReviewPriority
import java.time.Instant

/**
 * 승인 대기 콘텐츠 엔티티
 *
 * AI 크롤러가 생성한 콘텐츠 중 품질 검수를 통과하여
 * 관리자 승인을 기다리는 콘텐츠입니다.
 */
@Entity
@Table(name = "pending_contents")
class PendingContent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "ai_content_job_id", nullable = false)
    val aiContentJobId: Long,

    @Column(nullable = false, length = 200)
    var title: String,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    var category: Category,

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    var difficulty: Difficulty? = null,

    @Column(columnDefinition = "JSON")
    var tags: String? = null,  // JSON 배열로 저장

    @Column(name = "video_s3_key", nullable = false, length = 500)
    val videoS3Key: String,

    @Column(name = "thumbnail_s3_key", length = 500)
    val thumbnailS3Key: String? = null,

    @Column(name = "duration_seconds")
    val durationSeconds: Int? = null,

    @Column
    val width: Int = 1080,

    @Column
    val height: Int = 1920,

    @Column(name = "youtube_video_id", length = 20)
    val youtubeVideoId: String? = null,

    @Column(name = "youtube_title", length = 500)
    val youtubeTitle: String? = null,

    @Column(name = "youtube_channel", length = 200)
    val youtubeChannel: String? = null,

    @Column(name = "quality_score", nullable = false)
    val qualityScore: Int,

    @Column(length = 5)
    val language: String = "ko",  // 콘텐츠 언어 (ko, en, ja)

    @Column(name = "review_priority", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val reviewPriority: ReviewPriority,

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    var status: PendingContentStatus = PendingContentStatus.PENDING_REVIEW,

    @Column(name = "reviewed_by", length = 100)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    var rejectionReason: String? = null,

    @Column(name = "published_content_id", length = 36)
    var publishedContentId: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by", length = 100)
    val createdBy: String = "SYSTEM_AI_CRAWLER",

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", length = 100)
    var updatedBy: String? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null
) {
    /**
     * 콘텐츠 승인
     */
    fun approve(reviewerUsername: String, publishedContentId: String) {
        this.status = PendingContentStatus.APPROVED
        this.reviewedBy = reviewerUsername
        this.reviewedAt = Instant.now()
        this.publishedContentId = publishedContentId
        this.updatedBy = reviewerUsername
        this.updatedAt = Instant.now()
    }

    /**
     * 콘텐츠 거절
     */
    fun reject(reviewerUsername: String, reason: String) {
        this.status = PendingContentStatus.REJECTED
        this.reviewedBy = reviewerUsername
        this.reviewedAt = Instant.now()
        this.rejectionReason = reason
        this.updatedBy = reviewerUsername
        this.updatedAt = Instant.now()
    }

    /**
     * 메타데이터 수정
     */
    fun updateMetadata(
        title: String,
        description: String?,
        category: Category,
        difficulty: Difficulty?,
        tags: List<String>,
        updatedBy: String
    ) {
        this.title = title
        this.description = description
        this.category = category
        this.difficulty = difficulty
        this.tags = if (tags.isNotEmpty()) "[\"${tags.joinToString("\", \"")}\"]" else null
        this.updatedBy = updatedBy
        this.updatedAt = Instant.now()
    }

    /**
     * 태그를 리스트로 변환
     */
    fun getTagsList(): List<String> {
        if (tags.isNullOrBlank()) return emptyList()
        return tags!!
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    }
}

/**
 * 승인 대기 콘텐츠 상태
 */
enum class PendingContentStatus {
    PENDING_REVIEW,  // 검토 대기
    APPROVED,        // 승인됨 (백엔드 contents 테이블에 INSERT 완료)
    REJECTED,        // 거절됨
    PUBLISHED        // 게시됨 (contents.status = PUBLISHED)
}

/**
 * 콘텐츠 카테고리
 */
enum class Category(val displayName: String) {
    LANGUAGE("언어"),
    SCIENCE("과학"),
    HISTORY("역사"),
    MATHEMATICS("수학"),
    ART("예술"),
    STARTUP("스타트업"),
    MARKETING("마케팅"),
    PROGRAMMING("프로그래밍"),
    DESIGN("디자인"),
    PRODUCTIVITY("생산성"),
    PSYCHOLOGY("심리학"),
    FINANCE("재테크"),
    HEALTH("건강"),
    PARENTING("육아"),
    COOKING("요리"),
    TRAVEL("여행"),
    HOBBY("취미"),
    TREND("트렌드"),
    OTHER("기타"),
    FUN("재미");

    companion object {
        fun fromString(value: String?): Category {
            if (value == null) return OTHER
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}
