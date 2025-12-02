package me.onetwo.growsnap.domain.user.repository

import me.onetwo.growsnap.jooq.generated.tables.references.USERS
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.model.UserStatus
import me.onetwo.growsnap.jooq.generated.tables.records.UsersRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
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
                .set(USERS.STATUS, user.status.name)
        ).thenReturn(user.copy(id = userId))
    }

    /**
     * 이메일로 사용자 조회 (ACTIVE 상태만)
     *
     * @param email 이메일
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findByEmail(email: String): Mono<User> {
        return Mono.from(
            dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                .and(USERS.STATUS.ne(UserStatus.DELETED.name))
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
                .and(USERS.STATUS.ne(UserStatus.DELETED.name))
        ).map { record -> mapToUser(record) }
    }

    /**
     * 이메일로 사용자 조회 (Soft Delete 포함)
     *
     * 재가입 시 탈퇴한 계정을 복원하기 위해 사용됩니다.
     *
     * @param email 이메일
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findByEmailIncludingDeleted(email: String): Mono<User> {
        return Mono.from(
            dsl.selectFrom(USERS)
                .where(USERS.EMAIL.eq(email))
                // Soft delete 필터링 없음 (deleted_at IS NOT NULL 포함)
        ).map { record -> mapToUser(record) }
    }

    /**
     * 사용자 상태 업데이트
     *
     * @param id 사용자 ID
     * @param status 새로운 상태
     * @param updatedBy 업데이트한 사용자 ID
     * @return 업데이트된 사용자 정보
     */
    fun updateStatus(id: UUID, status: UserStatus, updatedBy: UUID): Mono<User> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(USERS)
                .set(USERS.STATUS, status.name)
                .set(USERS.UPDATED_AT, now)
                .set(USERS.UPDATED_BY, updatedBy.toString())
                // DELETED로 변경 시 deleted_at 설정
                .set(USERS.DELETED_AT, if (status == UserStatus.DELETED) now else null as Instant?)
                .where(USERS.ID.eq(id.toString()))
        ).then(findByIdIncludingDeleted(id))
    }

    /**
     * ID로 사용자 조회 (상태 무관)
     *
     * @param id 사용자 ID
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findByIdIncludingDeleted(id: UUID): Mono<User> {
        return Mono.from(
            dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id.toString()))
        ).map { record -> mapToUser(record) }
    }

    /**
     * 사용자 Provider 정보 업데이트
     *
     * 다른 OAuth 제공자로 재가입 시 사용됩니다.
     *
     * @param id 사용자 ID
     * @param provider 새로운 OAuth Provider
     * @param providerId 새로운 Provider ID
     * @return 업데이트된 사용자 정보
     */
    fun updateProvider(id: UUID, provider: OAuthProvider, providerId: String): Mono<User> {
        return Mono.from(
            dsl.update(USERS)
                .set(USERS.PROVIDER, provider.name)
                .set(USERS.PROVIDER_ID, providerId)
                .set(USERS.UPDATED_AT, Instant.now())
                .set(USERS.UPDATED_BY, id.toString())
                .where(USERS.ID.eq(id.toString()))
        ).then(findByIdIncludingDeleted(id))
    }

    /**
     * ID로 사용자 조회 (ACTIVE/SUSPENDED만)
     *
     * @param id 사용자 ID
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findById(id: UUID): Mono<User> {
        return Mono.from(
            dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id.toString()))
                .and(USERS.STATUS.ne(UserStatus.DELETED.name))
        ).map { record -> mapToUser(record) }
    }

    /**
     * 사용자 삭제 (Soft Delete)
     *
     * @param id 사용자 ID
     * @param deletedBy 삭제를 수행한 사용자 ID
     */
    fun softDelete(id: UUID, deletedBy: UUID): Mono<Void> {
        val now = Instant.now()
        return Mono.from(
            dsl.update(USERS)
                .set(USERS.DELETED_AT, now)
                .set(USERS.UPDATED_AT, now)
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
            status = UserStatus.valueOf(record.status ?: "ACTIVE"),
            createdAt = record.createdAt!!,
            updatedAt = record.updatedAt!!,
            deletedAt = record.deletedAt
        )
    }
}
