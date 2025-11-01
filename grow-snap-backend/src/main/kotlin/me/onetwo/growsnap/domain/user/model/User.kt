package me.onetwo.growsnap.domain.user.model

import java.time.LocalDateTime
import java.util.UUID

data class User(
    val id: UUID? = null,
    val email: String,
    val provider: OAuthProvider,
    val providerId: String,
    val role: UserRole = UserRole.USER,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val createdBy: String? = null,
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val updatedBy: String? = null,
    val deletedAt: LocalDateTime? = null
)

enum class OAuthProvider {
    GOOGLE,
    NAVER,
    KAKAO
}

enum class UserRole {
    USER,
    ADMIN
}
