package me.onetwo.growsnap.domain.block.controller

import me.onetwo.growsnap.domain.block.dto.BlockedUsersResponse
import me.onetwo.growsnap.domain.block.dto.UserBlockResponse
import me.onetwo.growsnap.domain.block.service.UserBlockService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.security.util.toUserId
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
 * 사용자 차단 컨트롤러
 *
 * 사용자 간 차단 관련 HTTP 요청을 처리합니다.
 *
 * @property userBlockService 사용자 차단 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1)
class UserBlockController(
    private val userBlockService: UserBlockService
) {

    /**
     * 사용자 차단
     *
     * POST /api/v1/users/{userId}/block
     *
     * 사용자가 다른 사용자를 차단합니다.
     * 차단된 사용자의 콘텐츠는 피드, 검색 등 모든 조회에서 제외됩니다.
     * 로그인한 사용자만 차단할 수 있으며, 자기 자신은 차단할 수 없습니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param userId 차단할 사용자 ID
     * @return 201 Created와 사용자 차단 응답
     */
    @PostMapping("/users/{userId}/block")
    fun blockUser(
        principal: Mono<Principal>,
        @PathVariable userId: UUID
    ): Mono<ResponseEntity<UserBlockResponse>> {
        return principal
            .toUserId()
            .flatMap { blockerId ->
                userBlockService.blockUser(blockerId, userId)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    /**
     * 사용자 차단 해제
     *
     * DELETE /api/v1/users/{userId}/block
     *
     * 사용자가 차단한 사용자를 해제합니다.
     * 차단하지 않은 사용자는 해제할 수 없습니다.
     * 로그인한 사용자만 차단 해제할 수 있습니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param userId 차단 해제할 사용자 ID
     * @return 204 No Content
     */
    @DeleteMapping("/users/{userId}/block")
    fun unblockUser(
        principal: Mono<Principal>,
        @PathVariable userId: UUID
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { blockerId ->
                userBlockService.unblockUser(blockerId, userId)
            }
            .thenReturn(ResponseEntity.status(HttpStatus.NO_CONTENT).build<Void>())
    }

    /**
     * 차단한 사용자 목록 조회
     *
     * GET /api/v1/users/blocks
     *
     * 로그인한 사용자가 차단한 사용자 목록을 조회합니다.
     * 커서 기반 페이지네이션을 사용하여 무한 스크롤을 지원합니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param cursor 커서 (차단 ID)
     * @param limit 조회 개수 (기본값: 20)
     * @return 200 OK와 차단한 사용자 목록
     */
    @GetMapping("/users/blocks")
    fun getBlockedUsers(
        principal: Mono<Principal>,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): Mono<ResponseEntity<BlockedUsersResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                userBlockService.getBlockedUsers(userId, cursor, limit)
            }
            .map { response -> ResponseEntity.ok(response) }
    }
}
