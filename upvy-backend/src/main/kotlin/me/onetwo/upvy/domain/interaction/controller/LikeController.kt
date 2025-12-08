package me.onetwo.upvy.domain.interaction.controller

import me.onetwo.upvy.domain.interaction.dto.LikeCountResponse
import me.onetwo.upvy.domain.interaction.dto.LikeResponse
import me.onetwo.upvy.domain.interaction.dto.LikeStatusResponse
import me.onetwo.upvy.domain.interaction.service.LikeService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 좋아요 컨트롤러
 *
 * 콘텐츠 좋아요 관련 HTTP 요청을 처리합니다.
 *
 * @property likeService 좋아요 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
class LikeController(
    private val likeService: LikeService
) {

    /**
     * 좋아요
     *
     * POST /api/v1/contents/{contentId}/like
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 콘텐츠 ID
     * @return 좋아요 응답
     */
    @PostMapping("/contents/{contentId}/like")
    fun likeContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<LikeResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                likeService.likeContent(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 좋아요 취소
     *
     * DELETE /api/v1/contents/{contentId}/like
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 비디오(콘텐츠) ID
     * @return 좋아요 응답
     */
    @DeleteMapping("/contents/{contentId}/like")
    fun unlikeContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<LikeResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                likeService.unlikeContent(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 좋아요 수 조회
     *
     * GET /api/v1/contents/{contentId}/likes
     *
     * @param videoId 비디오(콘텐츠) ID
     * @return 좋아요 수 응답
     */
    @GetMapping("/contents/{contentId}/likes")
    fun getLikeCount(
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<LikeCountResponse>> {
        return likeService.getLikeCount(contentId)
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 좋아요 상태 조회
     *
     * 특정 콘텐츠에 대한 사용자의 좋아요 상태를 확인합니다.
     *
     * GET /api/v1/contents/{contentId}/like/status
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 콘텐츠 ID
     * @return 좋아요 상태 응답
     */
    @GetMapping("/contents/{contentId}/like/status")
    fun getLikeStatus(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<LikeStatusResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                likeService.getLikeStatus(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }
}
