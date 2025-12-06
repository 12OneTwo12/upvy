package me.onetwo.growsnap.domain.notification.repository

import me.onetwo.growsnap.domain.notification.model.DeliveryStatus
import me.onetwo.growsnap.domain.notification.model.Notification
import me.onetwo.growsnap.domain.notification.model.NotificationTargetType
import me.onetwo.growsnap.domain.notification.model.NotificationType
import me.onetwo.growsnap.jooq.generated.tables.records.NotificationsRecord
import me.onetwo.growsnap.jooq.generated.tables.references.NOTIFICATIONS
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 알림 Repository
 *
 * JOOQ를 사용하여 notifications 테이블에 접근합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class NotificationRepository(
    private val dsl: DSLContext
) {

    /**
     * 알림 저장
     *
     * @param notification 알림 정보
     * @return 저장된 알림 (ID 포함)
     */
    fun save(notification: Notification): Mono<Notification> {
        return Mono.from(
            dsl.insertInto(NOTIFICATIONS)
                .set(NOTIFICATIONS.USER_ID, notification.userId.toString())
                .set(NOTIFICATIONS.TYPE, notification.type.name)
                .set(NOTIFICATIONS.TITLE, notification.title)
                .set(NOTIFICATIONS.BODY, notification.body)
                .set(NOTIFICATIONS.DATA, notification.data?.let { JSON.json(it) })
                .set(NOTIFICATIONS.IS_READ, notification.isRead)
                .set(NOTIFICATIONS.DELIVERY_STATUS, notification.deliveryStatus.name)
                .set(NOTIFICATIONS.ACTOR_ID, notification.actorId?.toString())
                .set(NOTIFICATIONS.TARGET_TYPE, notification.targetType?.name)
                .set(NOTIFICATIONS.TARGET_ID, notification.targetId?.toString())
                .set(NOTIFICATIONS.CREATED_BY, notification.actorId?.toString())
                .set(NOTIFICATIONS.UPDATED_BY, notification.actorId?.toString())
                .returningResult(NOTIFICATIONS.ID)
        ).map { record ->
            notification.copy(id = record.get(NOTIFICATIONS.ID))
        }
    }

    /**
     * 알림 발송 상태 업데이트
     *
     * @param id 알림 ID
     * @param status 새로운 발송 상태
     * @return 업데이트 성공 여부
     */
    fun updateDeliveryStatus(id: Long, status: DeliveryStatus): Mono<Boolean> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(NOTIFICATIONS)
                .set(NOTIFICATIONS.DELIVERY_STATUS, status.name)
                .set(NOTIFICATIONS.UPDATED_AT, now)
                .where(NOTIFICATIONS.ID.eq(id))
                .and(NOTIFICATIONS.DELETED_AT.isNull)
        ).map { rowsAffected -> rowsAffected > 0 }
    }

    /**
     * 알림 ID로 조회
     *
     * @param id 알림 ID
     * @return 알림 (존재하지 않으면 empty)
     */
    fun findById(id: Long): Mono<Notification> {
        return Mono.from(
            dsl.selectFrom(NOTIFICATIONS)
                .where(NOTIFICATIONS.ID.eq(id))
                .and(NOTIFICATIONS.DELETED_AT.isNull)
        ).map { record -> mapToNotification(record) }
    }

    /**
     * 사용자 ID로 알림 목록 조회 (커서 기반 페이징)
     *
     * @param userId 사용자 ID
     * @param cursor 마지막으로 조회한 알림 ID (없으면 null)
     * @param limit 조회할 개수
     * @return 알림 목록
     */
    fun findByUserId(userId: UUID, cursor: Long?, limit: Int): Flux<Notification> {
        val query = dsl.selectFrom(NOTIFICATIONS)
            .where(NOTIFICATIONS.USER_ID.eq(userId.toString()))
            .and(NOTIFICATIONS.DELETED_AT.isNull)

        val cursorQuery = if (cursor != null) {
            query.and(NOTIFICATIONS.ID.lt(cursor))
        } else {
            query
        }

        return Flux.from(
            cursorQuery
                .orderBy(NOTIFICATIONS.ID.desc())
                .limit(limit)
        ).map { record -> mapToNotification(record) }
    }

    /**
     * 읽지 않은 알림 수 조회
     *
     * @param userId 사용자 ID
     * @return 읽지 않은 알림 수
     */
    fun countUnreadByUserId(userId: UUID): Mono<Long> {
        return Mono.from(
            dsl.selectCount()
                .from(NOTIFICATIONS)
                .where(NOTIFICATIONS.USER_ID.eq(userId.toString()))
                .and(NOTIFICATIONS.IS_READ.eq(false))
                .and(NOTIFICATIONS.DELETED_AT.isNull)
        ).map { record -> record.value1().toLong() }
    }

    /**
     * 개별 알림 읽음 처리
     *
     * @param id 알림 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 업데이트된 알림
     */
    fun markAsRead(id: Long, userId: UUID): Mono<Notification> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(NOTIFICATIONS)
                .set(NOTIFICATIONS.IS_READ, true)
                .set(NOTIFICATIONS.UPDATED_AT, now)
                .set(NOTIFICATIONS.UPDATED_BY, userId.toString())
                .where(NOTIFICATIONS.ID.eq(id))
                .and(NOTIFICATIONS.USER_ID.eq(userId.toString()))
                .and(NOTIFICATIONS.DELETED_AT.isNull)
        ).then(findById(id))
    }

    /**
     * 모든 알림 읽음 처리
     *
     * @param userId 사용자 ID
     * @return 완료 신호
     */
    fun markAllAsRead(userId: UUID): Mono<Void> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(NOTIFICATIONS)
                .set(NOTIFICATIONS.IS_READ, true)
                .set(NOTIFICATIONS.UPDATED_AT, now)
                .set(NOTIFICATIONS.UPDATED_BY, userId.toString())
                .where(NOTIFICATIONS.USER_ID.eq(userId.toString()))
                .and(NOTIFICATIONS.IS_READ.eq(false))
                .and(NOTIFICATIONS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * 개별 알림 삭제 (Soft Delete)
     *
     * @param id 알림 ID
     * @param userId 사용자 ID (권한 확인용)
     * @return 완료 신호
     */
    fun deleteById(id: Long, userId: UUID): Mono<Void> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(NOTIFICATIONS)
                .set(NOTIFICATIONS.DELETED_AT, now)
                .set(NOTIFICATIONS.UPDATED_AT, now)
                .set(NOTIFICATIONS.UPDATED_BY, userId.toString())
                .where(NOTIFICATIONS.ID.eq(id))
                .and(NOTIFICATIONS.USER_ID.eq(userId.toString()))
                .and(NOTIFICATIONS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * JOOQ Record를 Notification 도메인 모델로 변환
     */
    private fun mapToNotification(record: NotificationsRecord): Notification {
        return Notification(
            id = record.id,
            userId = UUID.fromString(record.userId),
            type = NotificationType.valueOf(record.type!!),
            title = record.title!!,
            body = record.body!!,
            data = record.data?.toString(),
            isRead = record.isRead ?: false,
            deliveryStatus = record.deliveryStatus?.let { DeliveryStatus.valueOf(it) } ?: DeliveryStatus.PENDING,
            actorId = record.actorId?.let { UUID.fromString(it) },
            targetType = record.targetType?.let { NotificationTargetType.valueOf(it) },
            targetId = record.targetId?.let { UUID.fromString(it) },
            createdAt = record.createdAt ?: Instant.now(),
            createdBy = record.createdBy,
            updatedAt = record.updatedAt ?: Instant.now(),
            updatedBy = record.updatedBy,
            deletedAt = record.deletedAt
        )
    }
}
