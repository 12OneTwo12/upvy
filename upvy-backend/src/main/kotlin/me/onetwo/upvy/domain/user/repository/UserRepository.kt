package me.onetwo.upvy.domain.user.repository

import me.onetwo.upvy.domain.user.exception.UserNotFoundException
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.model.UserStatus
import me.onetwo.upvy.jooq.generated.tables.records.UsersRecord
import me.onetwo.upvy.jooq.generated.tables.references.USERS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 사용자 Repository
 *
 * 계정 통합 아키텍처에서 사용자의 핵심 정보만 관리합니다.
 * 인증 수단은 UserAuthenticationMethodRepository에서 관리합니다.
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
                .set(USERS.ROLE, user.role.name)
                .set(USERS.STATUS, user.status.name)
                .set(USERS.CREATED_AT, user.createdAt)
                .set(USERS.CREATED_BY, user.createdBy)
                .set(USERS.UPDATED_AT, user.updatedAt)
                .set(USERS.UPDATED_BY, user.updatedBy)
        ).thenReturn(user.copy(id = userId))
    }

    /**
     * 이메일로 사용자 조회 (ACTIVE/SUSPENDED 상태만)
     *
     * @param email 이메일
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findByEmail(email: String): Mono<User> {
        return Mono.from(
            dsl.select(
                USERS.ID,
                USERS.EMAIL,
                USERS.ROLE,
                USERS.STATUS,
                USERS.CREATED_AT,
                USERS.CREATED_BY,
                USERS.UPDATED_AT,
                USERS.UPDATED_BY,
                USERS.DELETED_AT
            )
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
                .and(USERS.STATUS.ne(UserStatus.DELETED.name))
                .and(USERS.DELETED_AT.isNull)
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
            dsl.select(
                USERS.ID,
                USERS.EMAIL,
                USERS.ROLE,
                USERS.STATUS,
                USERS.CREATED_AT,
                USERS.CREATED_BY,
                USERS.UPDATED_AT,
                USERS.UPDATED_BY,
                USERS.DELETED_AT
            )
                .from(USERS)
                .where(USERS.EMAIL.eq(email))
        ).map { record -> mapToUser(record) }
    }

    /**
     * ID로 사용자 조회 (ACTIVE/SUSPENDED만)
     *
     * @param id 사용자 ID
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findById(id: UUID): Mono<User> {
        return Mono.from(
            dsl.select(
                USERS.ID,
                USERS.EMAIL,
                USERS.ROLE,
                USERS.STATUS,
                USERS.CREATED_AT,
                USERS.CREATED_BY,
                USERS.UPDATED_AT,
                USERS.UPDATED_BY,
                USERS.DELETED_AT
            )
                .from(USERS)
                .where(USERS.ID.eq(id.toString()))
                .and(USERS.STATUS.ne(UserStatus.DELETED.name))
                .and(USERS.DELETED_AT.isNull)
        ).map { record -> mapToUser(record) }
    }

    /**
     * ID로 사용자 조회 (상태 무관)
     *
     * @param id 사용자 ID
     * @return 사용자 정보 (존재하지 않으면 null)
     */
    fun findByIdIncludingDeleted(id: UUID): Mono<User> {
        return Mono.from(
            dsl.select(
                USERS.ID,
                USERS.EMAIL,
                USERS.ROLE,
                USERS.STATUS,
                USERS.CREATED_AT,
                USERS.CREATED_BY,
                USERS.UPDATED_AT,
                USERS.UPDATED_BY,
                USERS.DELETED_AT
            )
                .from(USERS)
                .where(USERS.ID.eq(id.toString()))
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
                .and(USERS.DELETED_AT.isNull)
        ).then()
    }

    /**
     * JOOQ Record를 User 도메인 모델로 변환
     */
    private fun mapToUser(record: org.jooq.Record): User {
        return User(
            id = UUID.fromString(record.get(USERS.ID)),
            email = record.get(USERS.EMAIL)!!,
            role = UserRole.valueOf(record.get(USERS.ROLE)!!),
            status = UserStatus.valueOf(record.get(USERS.STATUS) ?: "ACTIVE"),
            createdAt = record.get(USERS.CREATED_AT)!!,
            createdBy = record.get(USERS.CREATED_BY),
            updatedAt = record.get(USERS.UPDATED_AT)!!,
            updatedBy = record.get(USERS.UPDATED_BY),
            deletedAt = record.get(USERS.DELETED_AT)
        )
    }
}
