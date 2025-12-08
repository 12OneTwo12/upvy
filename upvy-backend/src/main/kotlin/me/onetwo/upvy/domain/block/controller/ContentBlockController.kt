package me.onetwo.upvy.domain.block.controller

import me.onetwo.upvy.domain.block.dto.BlockedContentsResponse
import me.onetwo.upvy.domain.block.dto.ContentBlockResponse
import me.onetwo.upvy.domain.block.service.ContentBlockService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 콘텐츠 차단 컨트롤러
 *
 * 콘텐츠 차단 관련 HTTP 요청을 처리합니다.
 *
 * @property contentBlockService 콘텐츠 차단 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
class ContentBlockController(
    private val contentBlockService: ContentBlockService
) {

    /**
     * 콘텐츠 차단
     *
     * POST /api/v1/contents/{contentId}/block
     *
     * 사용자가 특정 콘텐츠를 차단합니다.
     * 차단된 콘텐츠는 피드에서 제외됩니다.
     * 로그인한 사용자만 차단할 수 있으며, 자신의 콘텐츠도 차단 가능합니다 (관심없음).
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 차단할 콘텐츠 ID
     * @return 201 Created와 콘텐츠 차단 응답
     */
    @PostMapping("/contents/{contentId}/block")
    fun blockContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<ContentBlockResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                contentBlockService.blockContent(userId, contentId)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    /**
     * 콘텐츠 차단 해제
     *
     * DELETE /api/v1/contents/{contentId}/block
     *
     * 사용자가 차단한 콘텐츠를 해제합니다.
     * 차단하지 않은 콘텐츠는 해제할 수 없습니다.
     * 로그인한 사용자만 차단 해제할 수 있습니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 차단 해제할 콘텐츠 ID
     * @return 204 No Content
     */
    @DeleteMapping("/contents/{contentId}/block")
    fun unblockContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                contentBlockService.unblockContent(userId, contentId)
            }
            .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT).build<Void>())
    }

    /**
     * 차단한 콘텐츠 목록 조회
     *
     * GET /api/v1/contents/blocks
     *
     * 로그인한 사용자가 차단한 콘텐츠 목록을 조회합니다.
     * 커서 기반 페이지네이션을 사용하여 무한 스크롤을 지원합니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수 (기본값: 20)
     * @return 200 OK와 차단한 콘텐츠 목록
     */
    @GetMapping("/contents/blocks")
    fun getBlockedContents(
        principal: Mono<Principal>,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<BlockedContentsResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                contentBlockService.getBlockedContents(userId, cursor, limit)
            }
            .map { response -> ResponseEntity.ok(response) }
    }
}
