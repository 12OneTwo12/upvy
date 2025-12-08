package me.onetwo.upvy.crawler.backoffice.domain

import jakarta.persistence.*
import java.time.Instant

/**
 * 백오피스 관리자 계정 엔티티
 */
@Entity
@Table(name = "backoffice_users")
class BackofficeUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 50)
    val username: String,

    @Column(nullable = false, length = 255)
    var password: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    var role: BackofficeRole = BackofficeRole.ADMIN,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null
) {
    fun updateLastLogin() {
        this.lastLoginAt = Instant.now()
        this.updatedAt = Instant.now()
    }

    fun disable() {
        this.enabled = false
        this.updatedAt = Instant.now()
    }

    fun enable() {
        this.enabled = true
        this.updatedAt = Instant.now()
    }
}

/**
 * 백오피스 사용자 역할
 */
enum class BackofficeRole {
    ADMIN,      // 전체 관리 권한
    REVIEWER    // 콘텐츠 검토 권한만
}
