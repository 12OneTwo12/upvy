package me.onetwo.upvy.domain.user.dto

import me.onetwo.upvy.domain.user.model.Follow
import java.time.Instant
import java.util.UUID

/**
 * 팔로우 응답 DTO
 */
data class FollowResponse(
    val id: Long,
    val followerId: UUID,
    val followingId: UUID,
    val createdAt: Instant
) {
    companion object {
        fun from(follow: Follow): FollowResponse {
            return FollowResponse(
                id = follow.id!!,
                followerId = follow.followerId,
                followingId = follow.followingId,
                createdAt = follow.createdAt
            )
        }
    }
}

/**
 * 팔로우 관계 확인 응답 DTO
 */
data class FollowCheckResponse(
    val followerId: UUID,
    val followingId: UUID,
    val isFollowing: Boolean
)

/**
 * 팔로우 통계 응답 DTO
 */
data class FollowStatsResponse(
    val userId: UUID,
    val followerCount: Int,
    val followingCount: Int
)
