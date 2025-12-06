package me.onetwo.growsnap.domain.notification.service

import me.onetwo.growsnap.domain.notification.model.NotificationSettings
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
     * @param allNotificationsEnabled 전체 알림 활성화 여부 (null이면 변경하지 않음)
     * @param likeNotificationsEnabled 좋아요 알림 활성화 여부 (null이면 변경하지 않음)
     * @param commentNotificationsEnabled 댓글 알림 활성화 여부 (null이면 변경하지 않음)
     * @param followNotificationsEnabled 팔로우 알림 활성화 여부 (null이면 변경하지 않음)
     * @return 수정된 알림 설정
     */
    fun updateSettings(
        userId: UUID,
        allNotificationsEnabled: Boolean?,
        likeNotificationsEnabled: Boolean?,
        commentNotificationsEnabled: Boolean?,
        followNotificationsEnabled: Boolean?
    ): Mono<NotificationSettings>

    /**
     * 신규 사용자를 위한 기본 알림 설정 생성
     *
     * @param userId 사용자 ID
     * @return 생성된 알림 설정
     */
    fun createDefaultSettings(userId: UUID): Mono<NotificationSettings>
}
