package me.onetwo.growsnap.domain.user.repository

import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.jooq.generated.tables.records.UsersRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

/**
 * 사용자 Repository
 *
 * JOOQ를 사용하여 users 테이블에 접근합니다.
 *
 * @property dsl JOOQ DSL Context
 */
@Repository
class UserRepository(
    private val dsl: DSLContext
) {
    /**
     * 사용자 저장
     *
     * @param user 사용자 정보
     * @return 저장된 사용자 (ID 포함)
     */
    fun save(user: User): Mono<User> {
        val userId = user.id ?: UUID.randomUUID()
        return Mono.from(
            dsl.insertInto(USERS)
                .set(USERS.ID, userId.toString())
                .set(USERS.EMAIL, user.email)
                .set(USERS.PROVIDER, user.provider.name)
                .set(USERS.PROVIDER_ID, user.providerId)
                .set(USERS.ROLE, user.role.name)
        ).thenReturn(user.copy(id = userId))
    }

    /**
     * 이메일로 사용자 조회
     *
     * @param email 이메일
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findByEmail(email: String): Mono<User> {
        return Mono.from(
            dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .and(USERS.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> mapToUser(record) }
    }

    /**
     * Provider와 Provider ID로 사용자 조회
     *
     * @param provider OAuth Provider
     * @param providerId Provider에서 제공한 사용자 ID
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findByProviderAndProviderId(provider: OAuthProvider, providerId: String): Mono<User> {
        return Mono.from(
            dsl.selectFrom(USERS)
                .where(USERS.PROVIDER.eq(provider.name))
                .and(USERS.PROVIDER_ID.eq(providerId))
                .and(USERS.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> mapToUser(record) }
    }

    /**
     * ID로 사용자 조회
     *
     * @param id 사용자 ID
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findById(id: UUID): Mono<User> {
        return Mono.from(
            dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id.toString()))
                .and(USERS.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> mapToUser(record) }
    }

    /**
     * 사용자 삭제 (Soft Delete)
     *
     * @param id 사용자 ID
     * @param deletedBy 삭제를 수행한 사용자 ID
     */
    fun softDelete(id: UUID, deletedBy: UUID): Mono<Void> {
        return Mono.from(
            dsl.update(USERS)
                .set(USERS.DELETED_AT, LocalDateTime.now())
                .set(USERS.UPDATED_AT, LocalDateTime.now())
                .set(USERS.UPDATED_BY, deletedBy.toString())
                .where(USERS.ID.eq(id.toString()))
                .and(USERS.DELETED_AT.isNull)  // 이미 삭제된 데이터는 제외
        ).then()
    }

    /**
     * JOOQ Record를 User 도메인 모델로 변환
     */
    private fun mapToUser(record: UsersRecord): User {
        return User(
            id = UUID.fromString(record.id!!),
            email = record.email!!,
            provider = OAuthProvider.valueOf(record.provider!!),
            providerId = record.providerId!!,
            role = UserRole.valueOf(record.role!!),
            createdAt = record.createdAt!!,
            updatedAt = record.updatedAt!!
        )
    }
}
