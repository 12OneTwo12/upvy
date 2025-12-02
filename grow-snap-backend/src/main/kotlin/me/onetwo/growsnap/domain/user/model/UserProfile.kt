package me.onetwo.growsnap.domain.user.model

import java.time.Instant
import java.util.UUID

data class UserProfile(
    val id: Long? = null,
    val userId: UUID,
    val nickname: String,
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null,
    val deletedAtUnix: Long = 0L
)
