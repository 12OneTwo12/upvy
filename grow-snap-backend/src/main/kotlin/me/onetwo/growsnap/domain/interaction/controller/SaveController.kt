package me.onetwo.growsnap.domain.interaction.controller

import me.onetwo.growsnap.domain.interaction.dto.SaveResponse
import me.onetwo.growsnap.domain.interaction.dto.SaveStatusResponse
import me.onetwo.growsnap.domain.interaction.dto.SavedContentResponse
import me.onetwo.growsnap.domain.interaction.service.SaveService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 저장 컨트롤러
 *
 * 콘텐츠 저장 관련 HTTP 요청을 처리합니다.
 *
 * @property saveService 저장 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
class SaveController(
    private val saveService: SaveService
) {

    /**
     * 콘텐츠 저장
     *
     * POST /api/v1/contents/{contentId}/save
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 비디오(콘텐츠) ID
     * @return 저장 응답
     */
    @PostMapping("/contents/{contentId}/save")
    fun saveContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<SaveResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                saveService.saveContent(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 콘텐츠 저장 취소
     *
     * DELETE /api/v1/contents/{contentId}/save
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 비디오(콘텐츠) ID
     * @return 저장 취소 응답
     */
    @DeleteMapping("/contents/{contentId}/save")
    fun unsaveContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<SaveResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                saveService.unsaveContent(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 저장한 콘텐츠 목록 조회
     *
     * GET /api/v1/users/me/saved-contents
     *
     * @param principal 인증된 사용자 Principal
     * @return 저장한 콘텐츠 목록
     */
    @GetMapping("/users/me/saved-contents")
    fun getSavedContents(
        principal: Mono<Principal>
    ): Flux<SavedContentResponse> {
        return principal
            .toUserId()
            .flatMapMany { userId ->
                saveService.getSavedContents(userId)
            }
    }

    /**
     * 저장 상태 조회
     *
     * 특정 콘텐츠에 대한 사용자의 저장 상태를 확인합니다.
     *
     * GET /api/v1/contents/{contentId}/save/status
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 비디오(콘텐츠) ID
     * @return 저장 상태 응답
     */
    @GetMapping("/contents/{contentId}/save/status")
    fun getSaveStatus(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<SaveStatusResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                saveService.getSaveStatus(userId, contentId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }
}
