package me.onetwo.upvy.domain.tag.dto

/**
 * 태그 응답 DTO
 */
data class TagResponse(
    val id: Long,
    val name: String,
    val usageCount: Int
)
