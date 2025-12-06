package me.onetwo.growsnap.domain.notification.controller

import jakarta.validation.Valid
import me.onetwo.growsnap.domain.notification.dto.PushTokenResponse
import me.onetwo.growsnap.domain.notification.dto.RegisterPushTokenRequest
import me.onetwo.growsnap.domain.notification.service.PushTokenService
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * 푸시 토큰 Controller
 *
 * 푸시 토큰 등록, 삭제 API를 제공합니다.
 * 다양한 푸시 제공자(Expo, FCM, APNs)를 지원합니다.
 *
 * @property pushTokenService 푸시 토큰 서비스
 */
@RestController
@RequestMapping(ApiPaths.API_V1_NOTIFICATIONS)
class PushTokenController(
    private val pushTokenService: PushTokenService
) {

    /**
     * 푸시 토큰 등록/갱신
     *
     * 디바이스 ID가 이미 등록되어 있으면 토큰을 갱신합니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param request 푸시 토큰 등록 요청
     * @return 등록된 푸시 토큰 정보
     */
    @PostMapping("/push-tokens")
    fun registerToken(
        principal: Mono<Principal>,
        @Valid @RequestBody request: RegisterPushTokenRequest
    ): Mono<ResponseEntity<PushTokenResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> pushTokenService.registerToken(userId, request) }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    /**
     * 특정 디바이스의 푸시 토큰 삭제
     *
     * @param principal 인증된 사용자 Principal
     * @param deviceId 삭제할 디바이스 ID
     * @return 204 No Content
     */
    @DeleteMapping("/push-tokens/{deviceId}")
    fun deleteToken(
        principal: Mono<Principal>,
        @PathVariable deviceId: String
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId -> pushTokenService.deleteToken(userId, deviceId) }
            .then(Mono.just(ResponseEntity.noContent().build()))
    }

    /**
     * 모든 디바이스의 푸시 토큰 삭제
     *
     * 로그아웃 시 모든 디바이스에서 푸시 알림을 받지 않도록 합니다.
     *
     * @param principal 인증된 사용자 Principal
     * @return 204 No Content
     */
    @DeleteMapping("/push-tokens")
    fun deleteAllTokens(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId -> pushTokenService.deleteAllTokens(userId) }
            .then(Mono.just(ResponseEntity.noContent().build()))
    }
}
