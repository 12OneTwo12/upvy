package me.onetwo.upvy.domain.user.dto

import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.model.UserStatus
import java.time.Instant
import java.util.UUID

/**
 * 사용자 응답 DTO
 *
 * 계정 통합 아키텍처 적용 후:
 * - provider 정보는 user_authentication_methods 테이블에서 관리
 * - User 엔티티에는 핵심 정보만 포함 (email, role, status)
 */
data class UserResponse(
    val id: UUID,
    val email: String,
    val role: UserRole,
    val status: UserStatus,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(user: User): UserResponse {
            return UserResponse(
                id = user.id!!,
                email = user.email,
                role = user.role,
                status = user.status,
                createdAt = user.createdAt,
                updatedAt = user.updatedAt
            )
        }
    }
}
