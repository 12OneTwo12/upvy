package me.onetwo.upvy.domain.auth.repository

import me.onetwo.upvy.domain.auth.exception.InvalidVerificationTokenException
import me.onetwo.upvy.domain.auth.model.EmailVerificationToken
import me.onetwo.upvy.jooq.generated.tables.EmailVerificationTokens
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 이메일 인증 토큰 Repository 구현체
 *
 * JOOQ와 R2DBC를 사용하여 email_verification_tokens 테이블에 접근합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class EmailVerificationTokenRepositoryImpl(
    private val dsl: DSLContext
) : EmailVerificationTokenRepository {

    companion object {
        private val TOKEN = EmailVerificationTokens.EMAIL_VERIFICATION_TOKENS
    }

    /**
     * 인증 토큰 저장
     *
     * @param token 인증 토큰
     * @return 저장된 인증 토큰 (ID 포함)
     */
    override fun save(token: EmailVerificationToken): Mono<EmailVerificationToken> {
        return Mono.from(
            dsl.insertInto(TOKEN)
                .set(TOKEN.USER_ID, token.userId.toString())
                .set(TOKEN.TOKEN, token.token)
                .set(TOKEN.EXPIRES_AT, token.expiresAt)
                .set(TOKEN.CREATED_AT, token.createdAt)
                .set(TOKEN.CREATED_BY, token.createdBy)
                .set(TOKEN.UPDATED_AT, token.updatedAt)
                .set(TOKEN.UPDATED_BY, token.updatedBy)
                .set(TOKEN.DELETED_AT, token.deletedAt)
                .returningResult(TOKEN.ID)
        ).map { result ->
            token.copy(id = result.getValue(TOKEN.ID))
        }
    }

    /**
     * 토큰 문자열로 인증 토큰 조회
     *
     * Soft Delete된 토큰은 제외합니다.
     *
     * @param token 토큰 문자열
     * @return 인증 토큰 (존재하지 않으면 empty)
     */
    override fun findByToken(token: String): Mono<EmailVerificationToken> {
        return Mono.from(
            dsl.select(
                TOKEN.ID,
                TOKEN.USER_ID,
                TOKEN.TOKEN,
                TOKEN.EXPIRES_AT,
                TOKEN.CREATED_AT,
                TOKEN.CREATED_BY,
                TOKEN.UPDATED_AT,
                TOKEN.UPDATED_BY,
                TOKEN.DELETED_AT
            )
                .from(TOKEN)
                .where(TOKEN.TOKEN.eq(token))
                .and(TOKEN.DELETED_AT.isNull)
        ).map { record -> mapToEmailVerificationToken(record) }
            .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
    }

    /**
     * 사용자 ID와 코드로 인증 토큰 조회
     *
     * 6자리 코드는 충돌 가능성이 있으므로 userId와 함께 검증합니다.
     * Soft Delete된 토큰은 제외합니다.
     *
     * @param userId 사용자 ID
     * @param token 인증 코드
     * @return 인증 토큰 (존재하지 않으면 empty)
     */
    override fun findByUserIdAndToken(userId: UUID, token: String): Mono<EmailVerificationToken> {
        return Mono.from(
            dsl.select(
                TOKEN.ID,
                TOKEN.USER_ID,
                TOKEN.TOKEN,
                TOKEN.EXPIRES_AT,
                TOKEN.CREATED_AT,
                TOKEN.CREATED_BY,
                TOKEN.UPDATED_AT,
                TOKEN.UPDATED_BY,
                TOKEN.DELETED_AT
            )
                .from(TOKEN)
                .where(TOKEN.USER_ID.eq(userId.toString()))
                .and(TOKEN.TOKEN.eq(token))
                .and(TOKEN.DELETED_AT.isNull)
        ).map { record -> mapToEmailVerificationToken(record) }
            .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
    }

    /**
     * 사용자 ID로 인증 토큰 조회 (가장 최근 토큰)
     *
     * Soft Delete된 토큰은 제외하고, created_at 기준 내림차순으로 정렬하여 가장 최근 토큰을 반환합니다.
     *
     * @param userId 사용자 ID
     * @return 인증 토큰 (존재하지 않으면 empty)
     */
    override fun findLatestByUserId(userId: UUID): Mono<EmailVerificationToken> {
        return Mono.from(
            dsl.select(
                TOKEN.ID,
                TOKEN.USER_ID,
                TOKEN.TOKEN,
                TOKEN.EXPIRES_AT,
                TOKEN.CREATED_AT,
                TOKEN.CREATED_BY,
                TOKEN.UPDATED_AT,
                TOKEN.UPDATED_BY,
                TOKEN.DELETED_AT
            )
                .from(TOKEN)
                .where(TOKEN.USER_ID.eq(userId.toString()))
                .and(TOKEN.DELETED_AT.isNull)
                .orderBy(TOKEN.CREATED_AT.desc())
                .limit(1)
        ).map { record -> mapToEmailVerificationToken(record) }
            .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
    }

    /**
     * 인증 토큰 삭제 (Soft Delete)
     *
     * @param id 토큰 ID
     * @return Mono<Void>
     */
    override fun softDelete(id: Long): Mono<Void> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(TOKEN)
                .set(TOKEN.DELETED_AT, now)
                .set(TOKEN.UPDATED_AT, now)
                .where(TOKEN.ID.eq(id))
                .and(TOKEN.DELETED_AT.isNull)
        ).then()
    }

    /**
     * 사용자의 모든 인증 토큰 삭제 (Soft Delete)
     *
     * 이메일 인증 완료 시 또는 재발급 시 기존 토큰들을 무효화합니다.
     *
     * @param userId 사용자 ID
     * @return Mono<Void>
     */
    override fun softDeleteAllByUserId(userId: UUID): Mono<Void> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(TOKEN)
                .set(TOKEN.DELETED_AT, now)
                .set(TOKEN.UPDATED_AT, now)
                .where(TOKEN.USER_ID.eq(userId.toString()))
                .and(TOKEN.DELETED_AT.isNull)
        ).then()
    }

    /**
     * JOOQ Record를 EmailVerificationToken 도메인 모델로 변환
     *
     * @param record JOOQ Record
     * @return EmailVerificationToken 도메인 모델
     */
    private fun mapToEmailVerificationToken(record: org.jooq.Record): EmailVerificationToken {
        return EmailVerificationToken(
            id = record.getValue(TOKEN.ID),
            userId = UUID.fromString(record.getValue(TOKEN.USER_ID)),
            token = record.getValue(TOKEN.TOKEN)!!,
            expiresAt = record.getValue(TOKEN.EXPIRES_AT)!!,
            createdAt = record.getValue(TOKEN.CREATED_AT)!!,
            createdBy = record.getValue(TOKEN.CREATED_BY),
            updatedAt = record.getValue(TOKEN.UPDATED_AT)!!,
            updatedBy = record.getValue(TOKEN.UPDATED_BY),
            deletedAt = record.getValue(TOKEN.DELETED_AT)
        )
    }
}
