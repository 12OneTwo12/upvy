package me.onetwo.growsnap.domain.feed.dto

import java.util.UUID

/**
 * 크리에이터 정보 응답 DTO
 *
 * 피드 아이템에 포함되는 크리에이터 기본 정보입니다.
 *
 * @property userId 사용자 ID
 * @property nickname 닉네임
 * @property profileImageUrl 프로필 이미지 URL
 * @property followerCount 팔로워 수
 * @property isFollowing 현재 사용자가 이 크리에이터를 팔로우하고 있는지 여부
 */
data class CreatorInfoResponse(
    val userId: UUID,
    val nickname: String,
    val profileImageUrl: String?,
    val followerCount: Int,
    val isFollowing: Boolean
)
