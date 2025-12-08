package me.onetwo.upvy.domain.user.dto

import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import java.time.Instant
import java.util.UUID

/**
 * 사용자 응답 DTO
 */
data class UserResponse(
    val id: UUID,
    val email: String,
    val provider: OAuthProvider,
    val role: UserRole,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(user: User): UserResponse {
            return UserResponse(
                id = user.id!!,
                email = user.email,
                provider = user.provider,
                role = user.role,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        }
    }
}
