package me.onetwo.upvy.crawler.domain.content

import jakarta.persistence.*
import java.time.Instant

/**
 * 게시된 콘텐츠 인터랙션 엔티티 (백엔드 content_interactions 테이블)
 */
@Entity
@Table(name = "content_interactions")
data class PublishedContentInteraction(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "content_id", nullable = false, length = 36)
    val contentId: String,

    @Column(name = "like_count", nullable = false)
    val likeCount: Int = 0,

    @Column(name = "comment_count", nullable = false)
    val commentCount: Int = 0,

    @Column(name = "save_count", nullable = false)
    val saveCount: Int = 0,

    @Column(name = "share_count", nullable = false)
    val shareCount: Int = 0,

    @Column(name = "view_count", nullable = false)
    val viewCount: Int = 0,

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
