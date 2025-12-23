package me.onetwo.upvy.crawler.domain.content

import jakarta.persistence.*
import java.time.Instant

/**
 * 콘텐츠-태그 관계 엔티티 (content_tags 테이블)
 *
 * M:N 관계를 관리하는 조인 테이블입니다.
 */
@Entity
@Table(
    name = "content_tags",
    uniqueConstraints = [
        UniqueConstraint(name = "unique_content_tag", columnNames = ["content_id", "tag_id"])
    ]
)
data class ContentTag(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "content_id", nullable = false, length = 36)
    val contentId: String,

    @Column(name = "tag_id", nullable = false)
    val tagId: Long,

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
