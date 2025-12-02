package me.onetwo.growsnap.domain.block.dto

import java.time.Instant

/**
 * 차단한 사용자 항목 응답 DTO
 *
 * 차단한 사용자 목록 조회 시 사용됩니다.
 *
 * @property blockId 차단 ID
 * @property userId 차단된 사용자 ID
 * @property nickname 차단된 사용자 닉네임
 * @property profileImageUrl 차단된 사용자 프로필 이미지 URL
 * @property blockedAt 차단 시각
 */
data class BlockedUserItemResponse(
    val blockId: Long,
    val userId: String,
    val nickname: String,
    val profileImageUrl: String?,
    val blockedAt: Instant
)
