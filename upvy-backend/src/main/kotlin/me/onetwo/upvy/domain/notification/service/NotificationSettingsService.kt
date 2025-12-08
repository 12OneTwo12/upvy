package me.onetwo.upvy.domain.notification.service

import me.onetwo.upvy.domain.notification.dto.UpdateNotificationSettingsRequest
import me.onetwo.upvy.domain.notification.model.NotificationSettings
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 알림 설정 서비스 인터페이스
 *
 * 알림 설정 조회, 수정 등의 비즈니스 로직을 정의합니다.
 */
interface NotificationSettingsService {

    /**
     * 사용자의 알림 설정 조회
     *
     * 설정이 존재하지 않으면 기본 설정을 생성합니다.
     *
     * @param userId 사용자 ID
     * @return 알림 설정
     */
    fun getSettings(userId: UUID): Mono<NotificationSettings>

    /**
     * 사용자의 알림 설정 수정
     *
     * @param userId 사용자 ID
     * @param request 알림 설정 수정 요청 DTO
     * @return 수정된 알림 설정
     */
    fun updateSettings(userId: UUID, request: UpdateNotificationSettingsRequest): Mono<NotificationSettings>

    /**
     * 신규 사용자를 위한 기본 알림 설정 생성
     *
     * @param userId 사용자 ID
     * @return 생성된 알림 설정
     */
    fun createDefaultSettings(userId: UUID): Mono<NotificationSettings>
}
