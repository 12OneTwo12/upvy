package me.onetwo.upvy.domain.tag.dto

/**
 * 인기 태그 목록 응답 DTO
 */
data class PopularTagsResponse(
    val tags: List<TagResponse>,
    val count: Int
)
