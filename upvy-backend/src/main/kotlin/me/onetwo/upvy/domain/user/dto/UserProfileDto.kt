package me.onetwo.upvy.domain.user.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import me.onetwo.upvy.domain.user.model.UserProfile
import java.time.Instant
import java.util.UUID

/**
 * 프로필 생성 요청 DTO
 */
data class CreateProfileRequest(
    @field:NotBlank(message = "닉네임은 필수입니다")
    @field:Size(min = 2, max = 20, message = "닉네임은 2-20자여야 합니다")
    val nickname: String,

    @field:Size(max = 500, message = "프로필 이미지 URL은 500자 이하여야 합니다")
    val profileImageUrl: String? = null,

    @field:Size(max = 500, message = "자기소개는 500자 이하여야 합니다")
    val bio: String? = null
)

/**
 * 프로필 업데이트 요청 DTO
 */
data class UpdateProfileRequest(
    @field:Size(min = 2, max = 20, message = "닉네임은 2-20자여야 합니다")
    val nickname: String? = null,

    @field:Size(max = 500, message = "프로필 이미지 URL은 500자 이하여야 합니다")
    val profileImageUrl: String? = null,

    @field:Size(max = 500, message = "자기소개는 500자 이하여야 합니다")
    val bio: String? = null
)

/**
 * 프로필 응답 DTO
 */
data class UserProfileResponse(
    val id: Long,
    val userId: UUID,
    val nickname: String,
    val profileImageUrl: String?,
    val bio: String?,
    val followerCount: Int,
    val followingCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun from(profile: UserProfile): UserProfileResponse {
            return UserProfileResponse(
                id = profile.id!!,
                userId = profile.userId,
                nickname = profile.nickname,
                profileImageUrl = profile.profileImageUrl,
                bio = profile.bio,
                followerCount = profile.followerCount,
                followingCount = profile.followingCount,
                createdAt = profile.createdAt,
                updatedAt = profile.updatedAt
            )
        }
    }
}

/**
 * 닉네임 중복 확인 응답 DTO
 */
data class NicknameCheckResponse(
    val nickname: String,
    val isDuplicated: Boolean
)

/**
 * 이미지 업로드 응답 DTO
 */
data class ImageUploadResponse(
    val imageUrl: String
)
