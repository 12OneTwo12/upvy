package me.onetwo.upvy.domain.block.dto

import java.time.Instant

/**
 * 차단한 콘텐츠 항목 응답 DTO
 *
 * 차단한 콘텐츠 목록 조회 시 사용됩니다.
 *
 * @property blockId 차단 ID
 * @property contentId 차단된 콘텐츠 ID
 * @property title 콘텐츠 제목
 * @property thumbnailUrl 콘텐츠 썸네일 URL
 * @property creatorNickname 크리에이터 닉네임
 * @property blockedAt 차단 시각
 */
data class BlockedContentItemResponse(
    val blockId: Long,
    val contentId: String,
    val title: String,
    val thumbnailUrl: String,
    val creatorNickname: String,
    val blockedAt: Instant
)
