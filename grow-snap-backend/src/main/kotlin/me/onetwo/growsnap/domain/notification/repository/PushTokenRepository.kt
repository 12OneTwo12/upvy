package me.onetwo.growsnap.domain.notification.repository

import me.onetwo.growsnap.domain.notification.model.DeviceType
import me.onetwo.growsnap.domain.notification.model.PushProvider
import me.onetwo.growsnap.domain.notification.model.PushToken
import me.onetwo.growsnap.jooq.generated.tables.records.PushTokensRecord
import me.onetwo.growsnap.jooq.generated.tables.references.PUSH_TOKENS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 푸시 토큰 Repository
 *
 * JOOQ를 사용하여 push_tokens 테이블에 접근합니다.
 * 다양한 푸시 제공자(Expo, FCM, APNs)를 지원합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class PushTokenRepository(
    private val dsl: DSLContext
) {

    /**
     * 푸시 토큰 저장 또는 갱신
     *
     * user_id + device_id 조합이 존재하면 토큰을 갱신하고, 없으면 새로 생성합니다.
     *
     * @param token 푸시 토큰 정보
     * @return 저장된 푸시 토큰
     */
    fun saveOrUpdate(token: PushToken): Mono<PushToken> {
        return findByUserIdAndDeviceId(token.userId, token.deviceId)
            .flatMap { existing ->
                update(existing.copy(
                    token = token.token,
                    deviceType = token.deviceType,
                    provider = token.provider
                ))
            }
            .switchIfEmpty(Mono.defer { save(token) })
    }

    /**
     * 새 푸시 토큰 저장
     *
     * @param token 푸시 토큰 정보
     * @return 저장된 푸시 토큰 (ID 포함)
     */
    fun save(token: PushToken): Mono<PushToken> {
        return Mono.from(
            dsl.insertInto(PUSH_TOKENS)
                .set(PUSH_TOKENS.USER_ID, token.userId.toString())
                .set(PUSH_TOKENS.TOKEN, token.token)
                .set(PUSH_TOKENS.DEVICE_ID, token.deviceId)
                .set(PUSH_TOKENS.DEVICE_TYPE, token.deviceType.name)
                .set(PUSH_TOKENS.PROVIDER, token.provider.name)
                .set(PUSH_TOKENS.CREATED_BY, token.userId.toString())
                .set(PUSH_TOKENS.UPDATED_BY, token.userId.toString())
                .returningResult(PUSH_TOKENS.ID)
        ).map { record ->
            token.copy(id = record.get(PUSH_TOKENS.ID))
        }
    }

    /**
     * 푸시 토큰 업데이트
     *
     * @param token 업데이트할 푸시 토큰
     * @return 업데이트된 푸시 토큰
     */
    fun update(token: PushToken): Mono<PushToken> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(PUSH_TOKENS)
                .set(PUSH_TOKENS.TOKEN, token.token)
                .set(PUSH_TOKENS.DEVICE_TYPE, token.deviceType.name)
                .set(PUSH_TOKENS.PROVIDER, token.provider.name)
                .set(PUSH_TOKENS.UPDATED_AT, now)
                .set(PUSH_TOKENS.UPDATED_BY, token.userId.toString())
                .where(PUSH_TOKENS.ID.eq(token.id))
                .and(PUSH_TOKENS.DELETED_AT.isNull)
        ).then(Mono.just(token.copy(updatedAt = now)))
    }

    /**
     * 사용자 ID와 디바이스 ID로 푸시 토큰 조회
     *
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     * @return 푸시 토큰 (존재하지 않으면 empty)
     */
    fun findByUserIdAndDeviceId(userId: UUID, deviceId: String): Mono<PushToken> {
        return Mono.from(
            dsl.selectFrom(PUSH_TOKENS)
                .where(PUSH_TOKENS.USER_ID.eq(userId.toString()))
                .and(PUSH_TOKENS.DEVICE_ID.eq(deviceId))
                .and(PUSH_TOKENS.DELETED_AT.isNull)
        ).map { record -> mapToPushToken(record) }
    }

    /**
     * 사용자 ID로 모든 푸시 토큰 조회
     *
     * @param userId 사용자 ID
     * @return 푸시 토큰 목록
     */
    fun findByUserId(userId: UUID): Flux<PushToken> {
        return Flux.from(
            dsl.selectFrom(PUSH_TOKENS)
                .where(PUSH_TOKENS.USER_ID.eq(userId.toString()))
                .and(PUSH_TOKENS.DELETED_AT.isNull)
                .orderBy(PUSH_TOKENS.CREATED_AT.desc())
        ).map { record -> mapToPushToken(record) }
    }

    /**
     * 사용자 ID와 제공자로 푸시 토큰 조회
     *
     * @param userId 사용자 ID
     * @param provider 푸시 제공자
     * @return 푸시 토큰 목록
     */
    fun findByUserIdAndProvider(userId: UUID, provider: PushProvider): Flux<PushToken> {
        return Flux.from(
            dsl.selectFrom(PUSH_TOKENS)
                .where(PUSH_TOKENS.USER_ID.eq(userId.toString()))
                .and(PUSH_TOKENS.PROVIDER.eq(provider.name))
                .and(PUSH_TOKENS.DELETED_AT.isNull)
                .orderBy(PUSH_TOKENS.CREATED_AT.desc())
        ).map { record -> mapToPushToken(record) }
    }

    /**
     * 사용자 ID로 모든 유효한 토큰 문자열 조회
     *
     * @param userId 사용자 ID
     * @return 토큰 문자열 목록
     */
    fun findTokensByUserId(userId: UUID): Flux<String> {
        return Flux.from(
            dsl.select(PUSH_TOKENS.TOKEN)
                .from(PUSH_TOKENS)
                .where(PUSH_TOKENS.USER_ID.eq(userId.toString()))
                .and(PUSH_TOKENS.DELETED_AT.isNull)
        ).map { record -> record.get(PUSH_TOKENS.TOKEN)!! }
    }

    /**
     * 특정 디바이스의 푸시 토큰 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @param deviceId 디바이스 ID
     * @return 완료 신호
     */
    fun deleteByUserIdAndDeviceId(userId: UUID, deviceId: String): Mono<Void> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(PUSH_TOKENS)
                .set(PUSH_TOKENS.DELETED_AT, now)
                .set(PUSH_TOKENS.UPDATED_AT, now)
                .set(PUSH_TOKENS.UPDATED_BY, userId.toString())
                .where(PUSH_TOKENS.USER_ID.eq(userId.toString()))
                .and(PUSH_TOKENS.DEVICE_ID.eq(deviceId))
                .and(PUSH_TOKENS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * 사용자의 모든 푸시 토큰 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @return 완료 신호
     */
    fun deleteAllByUserId(userId: UUID): Mono<Void> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(PUSH_TOKENS)
                .set(PUSH_TOKENS.DELETED_AT, now)
                .set(PUSH_TOKENS.UPDATED_AT, now)
                .set(PUSH_TOKENS.UPDATED_BY, userId.toString())
                .where(PUSH_TOKENS.USER_ID.eq(userId.toString()))
                .and(PUSH_TOKENS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * JOOQ Record를 PushToken 도메인 모델로 변환
     */
    private fun mapToPushToken(record: PushTokensRecord): PushToken {
        return PushToken(
            id = record.id,
            userId = UUID.fromString(record.userId),
            token = record.token!!,
            deviceId = record.deviceId!!,
            deviceType = DeviceType.valueOf(record.deviceType ?: "UNKNOWN"),
            provider = PushProvider.valueOf(record.provider ?: "EXPO"),
            createdAt = record.createdAt ?: Instant.now(),
            createdBy = record.createdBy,
            updatedAt = record.updatedAt ?: Instant.now(),
            updatedBy = record.updatedBy,
            deletedAt = record.deletedAt
        )
    }
}
