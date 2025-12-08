package me.onetwo.upvy.domain.notification.controller

import me.onetwo.upvy.domain.notification.dto.NotificationSettingsResponse
import me.onetwo.upvy.domain.notification.dto.UpdateNotificationSettingsRequest
import me.onetwo.upvy.domain.notification.service.NotificationSettingsService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * 알림 설정 Controller
 *
 * 알림 설정 조회, 수정 API를 제공합니다.
 */
@RestController
@RequestMapping(ApiPaths.API_V1_NOTIFICATIONS)
class NotificationSettingsController(
    private val notificationSettingsService: NotificationSettingsService
) {

    /**
     * 내 알림 설정 조회
     *
     * 설정이 존재하지 않으면 기본 설정을 생성하여 반환합니다.
     *
     * @param principal 인증된 사용자 Principal
     * @return 알림 설정
     */
    @GetMapping("/settings")
    fun getMySettings(principal: Mono<Principal>): Mono<ResponseEntity<NotificationSettingsResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> notificationSettingsService.getSettings(userId) }
            .map { settings -> ResponseEntity.ok(NotificationSettingsResponse.from(settings)) }
    }

    /**
     * 내 알림 설정 수정
     *
     * 요청에 포함된 필드만 수정됩니다 (PATCH semantics).
     *
     * @param principal 인증된 사용자 Principal
     * @param request 알림 설정 수정 요청
     * @return 수정된 알림 설정
     */
    @PatchMapping("/settings")
    fun updateMySettings(
        principal: Mono<Principal>,
        @RequestBody request: UpdateNotificationSettingsRequest
    ): Mono<ResponseEntity<NotificationSettingsResponse>> {
        return principal
            .toUserId()
            .flatMap { userId -> notificationSettingsService.updateSettings(userId, request) }
            .map { settings -> ResponseEntity.ok(NotificationSettingsResponse.from(settings)) }
    }
}
