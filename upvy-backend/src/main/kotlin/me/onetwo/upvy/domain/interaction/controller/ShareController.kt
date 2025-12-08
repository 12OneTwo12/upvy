package me.onetwo.upvy.domain.interaction.controller

import me.onetwo.upvy.domain.interaction.dto.ShareLinkResponse
import me.onetwo.upvy.domain.interaction.dto.ShareResponse
import me.onetwo.upvy.domain.interaction.service.ShareService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
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
@RequestMapping(ApiPaths.API_V1)
class ShareController(
    private val shareService: ShareService
) {

    /**
     * 콘텐츠 공유 (카운트 증가)
     *
     * POST /api/v1/contents/{contentId}/share
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 비디오(콘텐츠) ID
     * @return 공유 응답
     */
    @PostMapping("/contents/{contentId}/share")
    fun shareVideo(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<ShareResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                shareService.shareContent(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 공유 링크 조회
     *
     * GET /api/v1/contents/{contentId}/share-link
     *
     * @param contentId 비디오(콘텐츠) ID
     * @return 공유 링크 응답
     */
    @GetMapping("/contents/{contentId}/share-link")
    fun getShareLink(
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<ShareLinkResponse>> {
        return shareService.getShareLink(contentId)
            .map { response -> ResponseEntity.ok(response) }
    }
}
