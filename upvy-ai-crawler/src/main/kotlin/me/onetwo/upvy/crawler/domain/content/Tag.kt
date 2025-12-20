package me.onetwo.upvy.crawler.domain.content

import jakarta.persistence.*
import java.time.Instant

/**
 * 태그 엔티티 (tags 테이블)
 *
 * 태그 마스터 테이블로, 중복 없이 태그 정보를 관리합니다.
 */
@Entity
@Table(name = "tags")
data class Tag(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    val name: String,

    @Column(name = "normalized_name", nullable = false, length = 50)
    val normalizedName: String,

    @Column(name = "usage_count", nullable = false)
    val usageCount: Int = 0,

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
) {
    companion object {
        /**
         * 태그 이름을 정규화합니다.
         *
         * - 소문자 변환
         * - 앞뒤 공백 제거
         * - # 제거
         */
        fun normalizeTagName(tagName: String): String {
            return tagName
                .trim()
                .lowercase()
                .removePrefix("#")
        }
    }
}
