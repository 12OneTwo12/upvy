package me.onetwo.upvy.domain.interaction.dto

data class SaveResponse(
    val contentId: String,
    val saveCount: Int,
    val isSaved: Boolean
)
