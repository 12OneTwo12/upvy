package me.onetwo.growsnap.domain.notification.service

import me.onetwo.growsnap.domain.notification.dto.PushTokenResponse
import me.onetwo.growsnap.domain.notification.dto.RegisterPushTokenRequest
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 푸시 토큰 서비스 인터페이스
 *
 * Expo Push Token 등록, 삭제 관련 비즈니스 로직을 정의합니다.
 */
interface PushTokenService {

    /**
     * 푸시 토큰 등록/갱신
     *
     * 디바이스 ID가 이미 등록되어 있으면 토큰을 갱신합니다.
     *
     * @param userId 사용자 ID
     * @param request 푸시 토큰 등록 요청
     * @return 등록된 푸시 토큰 정보
     */
    fun registerToken(userId: UUID, request: RegisterPushTokenRequest): Mono<PushTokenResponse>

    /**
     * 특정 디바이스의 푸시 토큰 삭제
     *
     * @param userId 사용자 ID
     * @param deviceId 삭제할 디바이스 ID
     * @return 완료 신호
     */
    fun deleteToken(userId: UUID, deviceId: String): Mono<Void>

    /**
     * 모든 디바이스의 푸시 토큰 삭제
     *
     * 로그아웃 시 모든 디바이스에서 푸시 알림을 받지 않도록 합니다.
     *
     * @param userId 사용자 ID
     * @return 완료 신호
     */
    fun deleteAllTokens(userId: UUID): Mono<Void>
}
