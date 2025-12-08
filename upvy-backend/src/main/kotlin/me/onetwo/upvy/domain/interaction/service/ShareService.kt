package me.onetwo.upvy.domain.interaction.service

import me.onetwo.upvy.domain.interaction.dto.ShareLinkResponse
import me.onetwo.upvy.domain.interaction.dto.ShareResponse
import reactor.core.publisher.Mono
import java.util.UUID

interface ShareService {
    fun shareContent(userId: UUID, contentId: UUID): Mono<ShareResponse>
    fun getShareLink(contentId: UUID): Mono<ShareLinkResponse>
}
