package me.onetwo.growsnap.crawler.domain.content

import jakarta.persistence.*
import java.time.Instant

/**
 * 게시된 콘텐츠 엔티티 (백엔드 contents 테이블)
 *
 * AI 크롤러에서 승인된 콘텐츠를 백엔드 contents 테이블에 저장하기 위한 Entity입니다.
 */
@Entity
@Table(name = "contents")
data class PublishedContent(
    @Id
    @Column(length = 36)
    val id: String,

    @Column(name = "creator_id", nullable = false, length = 36)
    val creatorId: String,

    @Column(name = "content_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val contentType: ContentType = ContentType.VIDEO,

    @Column(nullable = false, length = 500)
    val url: String,

    @Column(name = "thumbnail_url", nullable = false, length = 500)
    val thumbnailUrl: String,

    @Column
    val duration: Int? = null,

    @Column(nullable = false)
    val width: Int = 1080,

    @Column(nullable = false)
    val height: Int = 1920,

    @Column(length = 5)
    val language: String = "ko",  // 콘텐츠 언어 (ko, en, ja)

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    val status: ContentStatus = ContentStatus.PUBLISHED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "created_by", length = 36)
    val createdBy: String? = null,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),

    @Column(name = "updated_by", length = 36)
    val updatedBy: String? = null,

    @Column(name = "deleted_at")
    val deletedAt: Instant? = null
)

/**
 * 콘텐츠 타입
 */
enum class ContentType {
    VIDEO,
    PHOTO
}

/**
 * 콘텐츠 상태
 */
enum class ContentStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    HIDDEN,
    DELETED
}
