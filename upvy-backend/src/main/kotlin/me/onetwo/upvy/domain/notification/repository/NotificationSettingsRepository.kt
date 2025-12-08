package me.onetwo.upvy.domain.notification.repository

import me.onetwo.upvy.domain.notification.model.NotificationSettings
import me.onetwo.upvy.jooq.generated.tables.records.NotificationSettingsRecord
import me.onetwo.upvy.jooq.generated.tables.references.NOTIFICATION_SETTINGS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 알림 설정 Repository
 *
 * JOOQ를 사용하여 notification_settings 테이블에 접근합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class NotificationSettingsRepository(
    private val dsl: DSLContext
) {

    /**
     * 알림 설정 저장
     *
     * @param settings 알림 설정
     * @return 저장된 알림 설정 (ID 포함)
     */
    fun save(settings: NotificationSettings): Mono<NotificationSettings> {
        return Mono.from(
            dsl.insertInto(NOTIFICATION_SETTINGS)
                .set(NOTIFICATION_SETTINGS.USER_ID, settings.userId.toString())
                .set(NOTIFICATION_SETTINGS.ALL_NOTIFICATIONS_ENABLED, settings.allNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.LIKE_NOTIFICATIONS_ENABLED, settings.likeNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.COMMENT_NOTIFICATIONS_ENABLED, settings.commentNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.FOLLOW_NOTIFICATIONS_ENABLED, settings.followNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.CREATED_BY, settings.userId.toString())
                .returningResult(NOTIFICATION_SETTINGS.ID)
        ).map { record ->
            settings.copy(id = record.get(NOTIFICATION_SETTINGS.ID))
        }
    }

    /**
     * 사용자 ID로 알림 설정 조회
     *
     * @param userId 사용자 ID
     * @return 알림 설정 (존재하지 않으면 empty)
     */
    fun findByUserId(userId: UUID): Mono<NotificationSettings> {
        return Mono.from(
            dsl.selectFrom(NOTIFICATION_SETTINGS)
                .where(NOTIFICATION_SETTINGS.USER_ID.eq(userId.toString()))
                .and(NOTIFICATION_SETTINGS.DELETED_AT.isNull)
        ).map { record -> mapToNotificationSettings(record) }
    }

    /**
     * 알림 설정 업데이트
     *
     * @param settings 업데이트할 알림 설정
     * @return 업데이트된 알림 설정
     */
    fun update(settings: NotificationSettings): Mono<NotificationSettings> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(NOTIFICATION_SETTINGS)
                .set(NOTIFICATION_SETTINGS.ALL_NOTIFICATIONS_ENABLED, settings.allNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.LIKE_NOTIFICATIONS_ENABLED, settings.likeNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.COMMENT_NOTIFICATIONS_ENABLED, settings.commentNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.FOLLOW_NOTIFICATIONS_ENABLED, settings.followNotificationsEnabled)
                .set(NOTIFICATION_SETTINGS.UPDATED_AT, now)
                .set(NOTIFICATION_SETTINGS.UPDATED_BY, settings.userId.toString())
                .where(NOTIFICATION_SETTINGS.USER_ID.eq(settings.userId.toString()))
                .and(NOTIFICATION_SETTINGS.DELETED_AT.isNull)
        ).then(findByUserId(settings.userId))
    }

    /**
     * 사용자 ID로 알림 설정 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 존재 여부
     */
    fun existsByUserId(userId: UUID): Mono<Boolean> {
        return Mono.from(
            dsl.selectCount()
                .from(NOTIFICATION_SETTINGS)
                .where(NOTIFICATION_SETTINGS.USER_ID.eq(userId.toString()))
                .and(NOTIFICATION_SETTINGS.DELETED_AT.isNull)
        ).map { record -> record.value1() > 0 }
    }

    /**
     * JOOQ Record를 NotificationSettings 도메인 모델로 변환
     */
    private fun mapToNotificationSettings(record: NotificationSettingsRecord): NotificationSettings {
        return NotificationSettings(
            id = record.id,
            userId = UUID.fromString(record.userId),
            allNotificationsEnabled = record.allNotificationsEnabled ?: true,
            likeNotificationsEnabled = record.likeNotificationsEnabled ?: true,
            commentNotificationsEnabled = record.commentNotificationsEnabled ?: true,
            followNotificationsEnabled = record.followNotificationsEnabled ?: true,
            createdAt = record.createdAt ?: Instant.now(),
            createdBy = record.createdBy,
            updatedAt = record.updatedAt ?: Instant.now(),
            updatedBy = record.updatedBy,
            deletedAt = record.deletedAt
        )
    }
}
