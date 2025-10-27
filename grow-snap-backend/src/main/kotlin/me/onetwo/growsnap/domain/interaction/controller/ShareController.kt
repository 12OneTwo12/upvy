package me.onetwo.growsnap.domain.interaction.controller

import me.onetwo.growsnap.domain.interaction.dto.ShareLinkResponse
import me.onetwo.growsnap.domain.interaction.dto.ShareResponse
import me.onetwo.growsnap.domain.interaction.service.ShareService
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 공유 컨트롤러
 *
 * 콘텐츠 공유 관련 HTTP 요청을 처리합니다.
 *
 * @property shareService 공유 서비스
 */
@RestController
@RequestMapping("/api/v1/videos")
class ShareController(
    private val shareService: ShareService
) {

    /**
     * 콘텐츠 공유 (카운트 증가)
     *
     * POST /api/v1/videos/{videoId}/share
     *
     * @param principal 인증된 사용자 Principal
     * @param videoId 비디오(콘텐츠) ID
     * @return 공유 응답
     */
    @PostMapping("/{videoId}/share")
    fun shareVideo(
        principal: Mono<Principal>,
        @PathVariable videoId: String
    ): Mono<ResponseEntity<ShareResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                val contentId = UUID.fromString(videoId)
                shareService.shareContent(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 공유 링크 조회
     *
     * GET /api/v1/videos/{videoId}/share-link
     *
     * @param videoId 비디오(콘텐츠) ID
     * @return 공유 링크 응답
     */
    @GetMapping("/{videoId}/share-link")
    fun getShareLink(
        @PathVariable videoId: String
    ): Mono<ResponseEntity<ShareLinkResponse>> {
        val contentId = UUID.fromString(videoId)

        return shareService.getShareLink(contentId)
            .map { response -> ResponseEntity.ok(response) }
    }
}
