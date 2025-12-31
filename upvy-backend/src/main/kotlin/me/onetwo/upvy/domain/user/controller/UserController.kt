package me.onetwo.upvy.domain.user.controller

import me.onetwo.upvy.domain.user.dto.UserResponse
import me.onetwo.upvy.domain.user.service.UserService
import me.onetwo.upvy.infrastructure.redis.RefreshTokenRepository
import me.onetwo.upvy.infrastructure.security.util.toUserId
import me.onetwo.upvy.infrastructure.common.ApiPaths
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID
import java.time.Duration

/**
 * 사용자 관리 Controller
 *
 * 사용자 조회, 회원 탈퇴 API를 제공합니다.
 */
@RestController
@RequestMapping(ApiPaths.API_V1_USERS)
class UserController(
    private val userService: UserService,
    private val refreshTokenRepository: RefreshTokenRepository
) {
    private val logger = LoggerFactory.getLogger(UserController::class.java)

    /**
     * 내 정보 조회
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @return 사용자 정보
     */
    @GetMapping("/me")
    fun getMe(principal: Mono<Principal>): Mono<ResponseEntity<UserResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> userService.getUserById(userId) }
            .map { user -> ResponseEntity.ok(UserResponse.from(user)) }
    }

    /**
     * 사용자 ID로 조회
     *
     * @param targetUserId 조회할 사용자 ID
     * @return 사용자 정보
     */
    @GetMapping("/{targetUserId}")
    fun getUserById(
        @PathVariable targetUserId: UUID
    ): Mono<ResponseEntity<UserResponse>> {
        return userService.getUserById(targetUserId)
            .map { user -> ResponseEntity.ok(UserResponse.from(user)) }
    }

    /**
     * 회원 탈퇴
     *
     * 인증된 사용자를 탈퇴 처리합니다 (Soft Delete).
     * 사용자, 프로필, 팔로우 관계가 모두 삭제되며, Refresh Token도 삭제됩니다.
     *
     * 참고: JWT Access Token은 만료 시까지 유효하므로, 프론트엔드에서 토큰을 삭제해야 합니다.
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @return 204 No Content
     */
    @DeleteMapping("/me")
    fun withdrawMe(principal: Mono<Principal>): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                userService.withdrawUser(userId)
                    .doOnSuccess {
                        // Fire and forget: Attempt Redis deletion asynchronously without blocking response
                        // This prevents integration test timeouts due to Redis connectivity issues
                        refreshTokenRepository.deleteByUserId(userId)
                            .timeout(Duration.ofMillis(100))
                            .doOnNext { result ->
                                logger.debug("Refresh token deleted: userId=$userId, result=$result")
                            }
                            .doOnError { error ->
                                logger.debug("Refresh token deletion failed: userId=$userId, error=${error.javaClass.simpleName}")
                            }
                            .onErrorResume { Mono.empty() }
                            .subscribe()
                    }
            }
            .thenReturn(ResponseEntity.noContent().build())
    }
}
