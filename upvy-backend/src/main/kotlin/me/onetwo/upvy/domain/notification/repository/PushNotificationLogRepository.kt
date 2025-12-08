package me.onetwo.upvy.domain.notification.repository

import me.onetwo.upvy.domain.notification.model.PushLogStatus
import me.onetwo.upvy.domain.notification.model.PushNotificationLog
import me.onetwo.upvy.domain.notification.model.PushProvider
import me.onetwo.upvy.jooq.generated.tables.records.PushNotificationLogsRecord
import me.onetwo.upvy.jooq.generated.tables.references.PUSH_NOTIFICATION_LOGS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 푸시 알림 발송 로그 Repository
 *
 * JOOQ를 사용하여 push_notification_logs 테이블에 접근합니다.
 * Append-only 테이블로, INSERT만 지원합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class PushNotificationLogRepository(
    private val dsl: DSLContext
) {

    /**
     * 발송 로그 저장
     *
     * @param log 푸시 발송 로그 정보
     * @return 저장된 로그 (ID 포함)
     */
    fun save(log: PushNotificationLog): Mono<PushNotificationLog> {
        return Mono.from(
            dsl.insertInto(PUSH_NOTIFICATION_LOGS)
                .set(PUSH_NOTIFICATION_LOGS.NOTIFICATION_ID, log.notificationId)
                .set(PUSH_NOTIFICATION_LOGS.PUSH_TOKEN_ID, log.pushTokenId)
                .set(PUSH_NOTIFICATION_LOGS.PROVIDER, log.provider.name)
                .set(PUSH_NOTIFICATION_LOGS.STATUS, log.status.name)
                .set(PUSH_NOTIFICATION_LOGS.PROVIDER_MESSAGE_ID, log.providerMessageId)
                .set(PUSH_NOTIFICATION_LOGS.ERROR_CODE, log.errorCode)
                .set(PUSH_NOTIFICATION_LOGS.ERROR_MESSAGE, log.errorMessage)
                .set(PUSH_NOTIFICATION_LOGS.ATTEMPT_COUNT, log.attemptCount)
                .set(PUSH_NOTIFICATION_LOGS.SENT_AT, log.sentAt)
                .set(PUSH_NOTIFICATION_LOGS.DELIVERED_AT, log.deliveredAt)
                .returningResult(PUSH_NOTIFICATION_LOGS.ID)
        ).map { record ->
            log.copy(id = record.get(PUSH_NOTIFICATION_LOGS.ID))
        }
    }

    /**
     * 알림 ID로 발송 로그 목록 조회
     *
     * @param notificationId 알림 ID
     * @return 발송 로그 목록
     */
    fun findByNotificationId(notificationId: Long): Flux<PushNotificationLog> {
        return Flux.from(
            dsl.selectFrom(PUSH_NOTIFICATION_LOGS)
                .where(PUSH_NOTIFICATION_LOGS.NOTIFICATION_ID.eq(notificationId))
                .orderBy(PUSH_NOTIFICATION_LOGS.CREATED_AT.desc())
        ).map { record -> mapToLog(record) }
    }

    /**
     * 푸시 토큰 ID로 발송 로그 목록 조회
     *
     * @param pushTokenId 푸시 토큰 ID
     * @param limit 조회할 개수
     * @return 발송 로그 목록
     */
    fun findByPushTokenId(pushTokenId: Long, limit: Int = 100): Flux<PushNotificationLog> {
        return Flux.from(
            dsl.selectFrom(PUSH_NOTIFICATION_LOGS)
                .where(PUSH_NOTIFICATION_LOGS.PUSH_TOKEN_ID.eq(pushTokenId))
                .orderBy(PUSH_NOTIFICATION_LOGS.CREATED_AT.desc())
                .limit(limit)
        ).map { record -> mapToLog(record) }
    }

    /**
     * 기간별 상태별 발송 로그 개수 조회 (통계용)
     *
     * @param status 발송 상태
     * @param from 시작 시각
     * @param to 종료 시각
     * @return 로그 개수
     */
    fun countByStatusAndPeriod(status: PushLogStatus, from: Instant, to: Instant): Mono<Long> {
        return Mono.from(
            dsl.selectCount()
                .from(PUSH_NOTIFICATION_LOGS)
                .where(PUSH_NOTIFICATION_LOGS.STATUS.eq(status.name))
                .and(PUSH_NOTIFICATION_LOGS.SENT_AT.between(from, to))
        ).map { record -> record.value1().toLong() }
    }

    /**
     * 알림 ID로 최신 발송 로그 조회
     *
     * @param notificationId 알림 ID
     * @return 최신 발송 로그
     */
    fun findLatestByNotificationId(notificationId: Long): Mono<PushNotificationLog> {
        return Mono.from(
            dsl.selectFrom(PUSH_NOTIFICATION_LOGS)
                .where(PUSH_NOTIFICATION_LOGS.NOTIFICATION_ID.eq(notificationId))
                .orderBy(PUSH_NOTIFICATION_LOGS.CREATED_AT.desc())
                .limit(1)
        ).map { record -> mapToLog(record) }
    }

    /**
     * JOOQ Record를 PushNotificationLog 도메인 모델로 변환
     */
    private fun mapToLog(record: PushNotificationLogsRecord): PushNotificationLog {
        return PushNotificationLog(
            id = record.id,
            notificationId = record.notificationId!!,
            pushTokenId = record.pushTokenId,
            provider = PushProvider.valueOf(record.provider!!),
            status = PushLogStatus.valueOf(record.status!!),
            providerMessageId = record.providerMessageId,
            errorCode = record.errorCode,
            errorMessage = record.errorMessage,
            attemptCount = record.attemptCount ?: 1,
            sentAt = record.sentAt!!,
            deliveredAt = record.deliveredAt,
            createdAt = record.createdAt ?: Instant.now()
        )
    }
}
