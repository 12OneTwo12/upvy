package me.onetwo.growsnap.domain.user.model

import java.time.Instant
import java.util.UUID

data class User(
    val id: UUID? = null,
    val email: String,
    val provider: OAuthProvider,
    val providerId: String,
    val role: UserRole = UserRole.USER,
    val status: UserStatus = UserStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
) {
    fun isDeleted(): Boolean = status == UserStatus.DELETED
    fun isActive(): Boolean = status == UserStatus.ACTIVE
    fun isSuspended(): Boolean = status == UserStatus.SUSPENDED
}

enum class OAuthProvider {
    GOOGLE,
    NAVER,
    KAKAO,
    SYSTEM  // AI 크롤러 등 시스템 계정용
}

enum class UserRole {
    USER,
    ADMIN
}

/**
 * 사용자 계정 상태
 *
 * - ACTIVE: 활성 계정 (정상 사용 가능)
 * - DELETED: 탈퇴한 계정 (재가입 시 복원 가능)
 * - SUSPENDED: 정지된 계정 (관리자 제재)
 */
enum class UserStatus {
    ACTIVE,
    DELETED,
    SUSPENDED
}
