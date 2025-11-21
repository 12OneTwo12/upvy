package me.onetwo.growsnap.domain.user.repository

import me.onetwo.growsnap.domain.user.model.UserStatus
import me.onetwo.growsnap.domain.user.model.UserStatusHistory
import me.onetwo.growsnap.jooq.generated.tables.references.USER_STATUS_HISTORY
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 상태 변경 이력 Repository
 *
 * JOOQ를 사용하여 user_status_history 테이블에 접근합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class UserStatusHistoryRepository(
    private val dsl: DSLContext
) {

    /**
     * 상태 변경 이력 저장
     *
     * @param history 상태 변경 이력
     * @return 저장된 이력 (ID 포함)
     */
    fun save(history: UserStatusHistory): Mono<UserStatusHistory> {
        return Mono.from(
            dsl.insertInto(USER_STATUS_HISTORY)
                .set(USER_STATUS_HISTORY.USER_ID, history.userId.toString())
                .set(USER_STATUS_HISTORY.PREVIOUS_STATUS, history.previousStatus?.name)
                .set(USER_STATUS_HISTORY.NEW_STATUS, history.newStatus.name)
                .set(USER_STATUS_HISTORY.REASON, history.reason)
                .set(USER_STATUS_HISTORY.METADATA, history.metadata?.let { org.jooq.JSON.valueOf(it) })
                .set(USER_STATUS_HISTORY.CHANGED_AT, history.changedAt)
                .set(USER_STATUS_HISTORY.CHANGED_BY, history.changedBy)
                .returningResult(USER_STATUS_HISTORY.ID)
        ).map { record ->
            history.copy(id = record.getValue(USER_STATUS_HISTORY.ID))
        }
    }

    /**
     * 사용자 ID로 상태 변경 이력 조회
     *
     * @param userId 사용자 ID
     * @return 상태 변경 이력 목록 (최신순)
     */
    fun findByUserId(userId: UUID): Flux<UserStatusHistory> {
        return Flux.from(
            dsl.select(
                USER_STATUS_HISTORY.ID,
                USER_STATUS_HISTORY.USER_ID,
                USER_STATUS_HISTORY.PREVIOUS_STATUS,
                USER_STATUS_HISTORY.NEW_STATUS,
                USER_STATUS_HISTORY.REASON,
                USER_STATUS_HISTORY.METADATA,
                USER_STATUS_HISTORY.CHANGED_AT,
                USER_STATUS_HISTORY.CHANGED_BY
            )
                .from(USER_STATUS_HISTORY)
                .where(USER_STATUS_HISTORY.USER_ID.eq(userId.toString()))
                .orderBy(USER_STATUS_HISTORY.CHANGED_AT.desc())
        ).map { record ->
            UserStatusHistory(
                id = record.getValue(USER_STATUS_HISTORY.ID),
                userId = UUID.fromString(record.getValue(USER_STATUS_HISTORY.USER_ID)!!),
                previousStatus = record.getValue(USER_STATUS_HISTORY.PREVIOUS_STATUS)?.let { UserStatus.valueOf(it) },
                newStatus = UserStatus.valueOf(record.getValue(USER_STATUS_HISTORY.NEW_STATUS)!!),
                reason = record.getValue(USER_STATUS_HISTORY.REASON),
                metadata = record.getValue(USER_STATUS_HISTORY.METADATA)?.data(),
                changedAt = record.getValue(USER_STATUS_HISTORY.CHANGED_AT)!!,
                changedBy = record.getValue(USER_STATUS_HISTORY.CHANGED_BY)
            )
        }
    }

    /**
     * 사용자의 최근 상태 변경 이력 조회
     *
     * @param userId 사용자 ID
     * @return 가장 최근 상태 변경 이력
     */
    fun findLatestByUserId(userId: UUID): Mono<UserStatusHistory> {
        return Mono.from(
            dsl.select(
                USER_STATUS_HISTORY.ID,
                USER_STATUS_HISTORY.USER_ID,
                USER_STATUS_HISTORY.PREVIOUS_STATUS,
                USER_STATUS_HISTORY.NEW_STATUS,
                USER_STATUS_HISTORY.REASON,
                USER_STATUS_HISTORY.METADATA,
                USER_STATUS_HISTORY.CHANGED_AT,
                USER_STATUS_HISTORY.CHANGED_BY
            )
                .from(USER_STATUS_HISTORY)
                .where(USER_STATUS_HISTORY.USER_ID.eq(userId.toString()))
                .orderBy(USER_STATUS_HISTORY.CHANGED_AT.desc())
                .limit(1)
        ).map { record ->
            UserStatusHistory(
                id = record.getValue(USER_STATUS_HISTORY.ID),
                userId = UUID.fromString(record.getValue(USER_STATUS_HISTORY.USER_ID)!!),
                previousStatus = record.getValue(USER_STATUS_HISTORY.PREVIOUS_STATUS)?.let { UserStatus.valueOf(it) },
                newStatus = UserStatus.valueOf(record.getValue(USER_STATUS_HISTORY.NEW_STATUS)!!),
                reason = record.getValue(USER_STATUS_HISTORY.REASON),
                metadata = record.getValue(USER_STATUS_HISTORY.METADATA)?.data(),
                changedAt = record.getValue(USER_STATUS_HISTORY.CHANGED_AT)!!,
                changedBy = record.getValue(USER_STATUS_HISTORY.CHANGED_BY)
            )
        }
    }
}
