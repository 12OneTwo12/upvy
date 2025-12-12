package me.onetwo.upvy.domain.user.repository

import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.UserAuthenticationMethod
import me.onetwo.upvy.jooq.generated.tables.references.USER_AUTHENTICATION_METHODS
import me.onetwo.upvy.jooq.generated.tables.records.UserAuthenticationMethodsRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 사용자 인증 수단 Repository 구현
 *
 * JOOQ를 사용하여 user_authentication_methods 테이블에 접근합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class UserAuthenticationMethodRepositoryImpl(
    private val dsl: DSLContext
) : UserAuthenticationMethodRepository {

    override fun save(authMethod: UserAuthenticationMethod): Mono<UserAuthenticationMethod> {
        val id = authMethod.id

        return if (id == null) {
            // INSERT
            Mono.from(
                dsl.insertInto(USER_AUTHENTICATION_METHODS)
                    .set(USER_AUTHENTICATION_METHODS.USER_ID, authMethod.userId.toString())
                    .set(USER_AUTHENTICATION_METHODS.PROVIDER, authMethod.provider.name)
                    .set(USER_AUTHENTICATION_METHODS.PROVIDER_ID, authMethod.providerId)
                    .set(USER_AUTHENTICATION_METHODS.PASSWORD, authMethod.password)
                    .set(USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED, authMethod.emailVerified)
                    .set(USER_AUTHENTICATION_METHODS.IS_PRIMARY, authMethod.isPrimary)
                    .set(USER_AUTHENTICATION_METHODS.CREATED_AT, authMethod.createdAt)
                    .set(USER_AUTHENTICATION_METHODS.CREATED_BY, authMethod.createdBy)
                    .set(USER_AUTHENTICATION_METHODS.UPDATED_AT, authMethod.updatedAt)
                    .set(USER_AUTHENTICATION_METHODS.UPDATED_BY, authMethod.updatedBy)
                    .returningResult(USER_AUTHENTICATION_METHODS.ID)
            ).map { result ->
                authMethod.copy(id = result.getValue(USER_AUTHENTICATION_METHODS.ID))
            }
        } else {
            // UPDATE
            Mono.from(
                dsl.update(USER_AUTHENTICATION_METHODS)
                    .set(USER_AUTHENTICATION_METHODS.PROVIDER, authMethod.provider.name)
                    .set(USER_AUTHENTICATION_METHODS.PROVIDER_ID, authMethod.providerId)
                    .set(USER_AUTHENTICATION_METHODS.PASSWORD, authMethod.password)
                    .set(USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED, authMethod.emailVerified)
                    .set(USER_AUTHENTICATION_METHODS.IS_PRIMARY, authMethod.isPrimary)
                    .set(USER_AUTHENTICATION_METHODS.UPDATED_AT, Instant.now())
                    .set(USER_AUTHENTICATION_METHODS.UPDATED_BY, authMethod.updatedBy)
                    .where(USER_AUTHENTICATION_METHODS.ID.eq(id))
                    .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
            ).thenReturn(authMethod)
        }
    }

    override fun findAllByUserId(userId: UUID): Flux<UserAuthenticationMethod> {
        return Flux.from(
            dsl.select(
                USER_AUTHENTICATION_METHODS.ID,
                USER_AUTHENTICATION_METHODS.USER_ID,
                USER_AUTHENTICATION_METHODS.PROVIDER,
                USER_AUTHENTICATION_METHODS.PROVIDER_ID,
                USER_AUTHENTICATION_METHODS.PASSWORD,
                USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED,
                USER_AUTHENTICATION_METHODS.IS_PRIMARY,
                USER_AUTHENTICATION_METHODS.CREATED_AT,
                USER_AUTHENTICATION_METHODS.CREATED_BY,
                USER_AUTHENTICATION_METHODS.UPDATED_AT,
                USER_AUTHENTICATION_METHODS.UPDATED_BY,
                USER_AUTHENTICATION_METHODS.DELETED_AT
            )
                .from(USER_AUTHENTICATION_METHODS)
                .where(USER_AUTHENTICATION_METHODS.USER_ID.eq(userId.toString()))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
                .orderBy(USER_AUTHENTICATION_METHODS.IS_PRIMARY.desc(), USER_AUTHENTICATION_METHODS.CREATED_AT.asc())
        ).map { record -> mapToAuthMethod(record) }
    }

    override fun findByUserIdAndProvider(userId: UUID, provider: OAuthProvider): Mono<UserAuthenticationMethod> {
        return Mono.from(
            dsl.select(
                USER_AUTHENTICATION_METHODS.ID,
                USER_AUTHENTICATION_METHODS.USER_ID,
                USER_AUTHENTICATION_METHODS.PROVIDER,
                USER_AUTHENTICATION_METHODS.PROVIDER_ID,
                USER_AUTHENTICATION_METHODS.PASSWORD,
                USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED,
                USER_AUTHENTICATION_METHODS.IS_PRIMARY,
                USER_AUTHENTICATION_METHODS.CREATED_AT,
                USER_AUTHENTICATION_METHODS.CREATED_BY,
                USER_AUTHENTICATION_METHODS.UPDATED_AT,
                USER_AUTHENTICATION_METHODS.UPDATED_BY,
                USER_AUTHENTICATION_METHODS.DELETED_AT
            )
                .from(USER_AUTHENTICATION_METHODS)
                .where(USER_AUTHENTICATION_METHODS.USER_ID.eq(userId.toString()))
                .and(USER_AUTHENTICATION_METHODS.PROVIDER.eq(provider.name))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
        ).map { record -> mapToAuthMethod(record) }
    }

    override fun findByProviderAndProviderId(provider: OAuthProvider, providerId: String): Mono<UserAuthenticationMethod> {
        return Mono.from(
            dsl.select(
                USER_AUTHENTICATION_METHODS.ID,
                USER_AUTHENTICATION_METHODS.USER_ID,
                USER_AUTHENTICATION_METHODS.PROVIDER,
                USER_AUTHENTICATION_METHODS.PROVIDER_ID,
                USER_AUTHENTICATION_METHODS.PASSWORD,
                USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED,
                USER_AUTHENTICATION_METHODS.IS_PRIMARY,
                USER_AUTHENTICATION_METHODS.CREATED_AT,
                USER_AUTHENTICATION_METHODS.CREATED_BY,
                USER_AUTHENTICATION_METHODS.UPDATED_AT,
                USER_AUTHENTICATION_METHODS.UPDATED_BY,
                USER_AUTHENTICATION_METHODS.DELETED_AT
            )
                .from(USER_AUTHENTICATION_METHODS)
                .where(USER_AUTHENTICATION_METHODS.PROVIDER.eq(provider.name))
                .and(USER_AUTHENTICATION_METHODS.PROVIDER_ID.eq(providerId))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
        ).map { record -> mapToAuthMethod(record) }
    }

    override fun findByUserIdAndProviderAndEmailVerified(
        userId: UUID,
        provider: OAuthProvider,
        verified: Boolean
    ): Mono<UserAuthenticationMethod> {
        return Mono.from(
            dsl.select(
                USER_AUTHENTICATION_METHODS.ID,
                USER_AUTHENTICATION_METHODS.USER_ID,
                USER_AUTHENTICATION_METHODS.PROVIDER,
                USER_AUTHENTICATION_METHODS.PROVIDER_ID,
                USER_AUTHENTICATION_METHODS.PASSWORD,
                USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED,
                USER_AUTHENTICATION_METHODS.IS_PRIMARY,
                USER_AUTHENTICATION_METHODS.CREATED_AT,
                USER_AUTHENTICATION_METHODS.CREATED_BY,
                USER_AUTHENTICATION_METHODS.UPDATED_AT,
                USER_AUTHENTICATION_METHODS.UPDATED_BY,
                USER_AUTHENTICATION_METHODS.DELETED_AT
            )
                .from(USER_AUTHENTICATION_METHODS)
                .where(USER_AUTHENTICATION_METHODS.USER_ID.eq(userId.toString()))
                .and(USER_AUTHENTICATION_METHODS.PROVIDER.eq(provider.name))
                .and(USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED.eq(verified))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
        ).map { record -> mapToAuthMethod(record) }
    }

    override fun updatePassword(id: Long, password: String): Mono<Int> {
        return Mono.from(
            dsl.update(USER_AUTHENTICATION_METHODS)
                .set(USER_AUTHENTICATION_METHODS.PASSWORD, password)
                .set(USER_AUTHENTICATION_METHODS.UPDATED_AT, Instant.now())
                .where(USER_AUTHENTICATION_METHODS.ID.eq(id))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
        )
    }

    override fun updateEmailVerified(id: Long, verified: Boolean): Mono<Int> {
        return Mono.from(
            dsl.update(USER_AUTHENTICATION_METHODS)
                .set(USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED, verified)
                .set(USER_AUTHENTICATION_METHODS.UPDATED_AT, Instant.now())
                .where(USER_AUTHENTICATION_METHODS.ID.eq(id))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
        )
    }

    override fun setPrimary(userId: UUID, id: Long): Mono<Int> {
        // 트랜잭션으로 처리되어야 하지만, 여기서는 순차 실행
        return Mono.from(
            // 1. 기존 primary 모두 해제
            dsl.update(USER_AUTHENTICATION_METHODS)
                .set(USER_AUTHENTICATION_METHODS.IS_PRIMARY, false)
                .set(USER_AUTHENTICATION_METHODS.UPDATED_AT, Instant.now())
                .where(USER_AUTHENTICATION_METHODS.USER_ID.eq(userId.toString()))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
        ).then(
            Mono.from(
                // 2. 새로운 primary 설정
                dsl.update(USER_AUTHENTICATION_METHODS)
                    .set(USER_AUTHENTICATION_METHODS.IS_PRIMARY, true)
                    .set(USER_AUTHENTICATION_METHODS.UPDATED_AT, Instant.now())
                    .where(USER_AUTHENTICATION_METHODS.ID.eq(id))
                    .and(USER_AUTHENTICATION_METHODS.USER_ID.eq(userId.toString()))
                    .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
            )
        )
    }

    override fun softDelete(id: Long): Mono<Int> {
        return Mono.from(
            dsl.update(USER_AUTHENTICATION_METHODS)
                .set(USER_AUTHENTICATION_METHODS.DELETED_AT, Instant.now())
                .set(USER_AUTHENTICATION_METHODS.UPDATED_AT, Instant.now())
                .where(USER_AUTHENTICATION_METHODS.ID.eq(id))
                .and(USER_AUTHENTICATION_METHODS.DELETED_AT.isNull)
        )
    }

    /**
     * JOOQ Record를 UserAuthenticationMethod 도메인 모델로 변환
     */
    private fun mapToAuthMethod(record: org.jooq.Record): UserAuthenticationMethod {
        return UserAuthenticationMethod(
            id = record.get(USER_AUTHENTICATION_METHODS.ID),
            userId = UUID.fromString(record.get(USER_AUTHENTICATION_METHODS.USER_ID)),
            provider = OAuthProvider.valueOf(record.get(USER_AUTHENTICATION_METHODS.PROVIDER)!!),
            providerId = record.get(USER_AUTHENTICATION_METHODS.PROVIDER_ID),
            password = record.get(USER_AUTHENTICATION_METHODS.PASSWORD),
            emailVerified = record.get(USER_AUTHENTICATION_METHODS.EMAIL_VERIFIED) ?: false,
            isPrimary = record.get(USER_AUTHENTICATION_METHODS.IS_PRIMARY) ?: false,
            createdAt = record.get(USER_AUTHENTICATION_METHODS.CREATED_AT)!!,
            createdBy = record.get(USER_AUTHENTICATION_METHODS.CREATED_BY),
            updatedAt = record.get(USER_AUTHENTICATION_METHODS.UPDATED_AT)!!,
            updatedBy = record.get(USER_AUTHENTICATION_METHODS.UPDATED_BY),
            deletedAt = record.get(USER_AUTHENTICATION_METHODS.DELETED_AT)
        )
    }
}
