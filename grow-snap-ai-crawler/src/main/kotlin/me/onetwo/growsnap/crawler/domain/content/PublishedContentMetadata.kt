package me.onetwo.growsnap.crawler.domain.content

import jakarta.persistence.*
import java.time.Instant

/**
 * 게시된 콘텐츠 메타데이터 엔티티 (백엔드 content_metadata 테이블)
 */
@Entity
@Table(name = "content_metadata")
data class PublishedContentMetadata(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "content_id", nullable = false, length = 36)
    val contentId: String,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(columnDefinition = "TEXT")
    val description: String? = null,

    @Column(nullable = false, length = 50)
    val category: String,

    @Column(columnDefinition = "JSON")
    val tags: String? = null,

    @Column(name = "difficulty_level", length = 20)
    val difficultyLevel: String? = null,

    @Column(nullable = false, length = 10)
    val language: String = "ko",

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
