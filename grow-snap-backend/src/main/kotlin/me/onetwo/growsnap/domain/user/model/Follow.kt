package me.onetwo.growsnap.domain.user.model

import java.time.Instant
import java.util.UUID

data class Follow(
    val id: Long? = null,
    val followerId: UUID,
    val followingId: UUID,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
)
