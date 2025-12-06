package me.onetwo.growsnap.domain.notification.service

import me.onetwo.growsnap.domain.notification.model.NotificationSettings
import me.onetwo.growsnap.domain.notification.repository.NotificationSettingsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 알림 설정 서비스 구현체
 *
 * 알림 설정 조회, 수정 등의 비즈니스 로직을 처리합니다.
 *
 * @property notificationSettingsRepository 알림 설정 Repository
 */
@Service
@Transactional(readOnly = true)
class NotificationSettingsServiceImpl(
    private val notificationSettingsRepository: NotificationSettingsRepository
) : NotificationSettingsService {

    private val logger = LoggerFactory.getLogger(NotificationSettingsServiceImpl::class.java)

    /**
     * 사용자의 알림 설정 조회
     *
     * 설정이 존재하지 않으면 기본 설정을 생성합니다.
     *
     * @param userId 사용자 ID
     * @return 알림 설정
     */
    @Transactional
    override fun getSettings(userId: UUID): Mono<NotificationSettings> {
        return notificationSettingsRepository.findByUserId(userId)
            .switchIfEmpty(
                Mono.defer {
                    logger.info("Creating default notification settings: userId=$userId")
                    createDefaultSettings(userId)
                }
            )
    }

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
    @Transactional
    override fun updateSettings(
        userId: UUID,
        allNotificationsEnabled: Boolean?,
        likeNotificationsEnabled: Boolean?,
        commentNotificationsEnabled: Boolean?,
        followNotificationsEnabled: Boolean?
    ): Mono<NotificationSettings> {
        return getSettings(userId)
            .flatMap { currentSettings ->
                val updatedSettings = currentSettings.copy(
                    allNotificationsEnabled = allNotificationsEnabled ?: currentSettings.allNotificationsEnabled,
                    likeNotificationsEnabled = likeNotificationsEnabled ?: currentSettings.likeNotificationsEnabled,
                    commentNotificationsEnabled = commentNotificationsEnabled ?: currentSettings.commentNotificationsEnabled,
                    followNotificationsEnabled = followNotificationsEnabled ?: currentSettings.followNotificationsEnabled
                )

                logger.info(
                    "Updating notification settings: userId=$userId, " +
                        "all=${updatedSettings.allNotificationsEnabled}, " +
                        "like=${updatedSettings.likeNotificationsEnabled}, " +
                        "comment=${updatedSettings.commentNotificationsEnabled}, " +
                        "follow=${updatedSettings.followNotificationsEnabled}"
                )

                notificationSettingsRepository.update(updatedSettings)
            }
    }

    /**
     * 신규 사용자를 위한 기본 알림 설정 생성
     *
     * @param userId 사용자 ID
     * @return 생성된 알림 설정
     */
    @Transactional
    override fun createDefaultSettings(userId: UUID): Mono<NotificationSettings> {
        val defaultSettings = NotificationSettings.createDefault(userId)
        return notificationSettingsRepository.save(defaultSettings)
            .doOnNext { logger.info("Default notification settings created: userId=$userId") }
    }
}
