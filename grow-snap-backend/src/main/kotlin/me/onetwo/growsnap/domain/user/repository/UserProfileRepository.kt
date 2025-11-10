package me.onetwo.growsnap.domain.user.repository

import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.domain.user.dto.UserInfo
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.jooq.generated.tables.records.UserProfilesRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 프로필 Repository
 */
@Repository
class UserProfileRepository(
    private val dsl: DSLContext
) {
    fun save(profile: UserProfile): Mono<UserProfile> {
        return Mono.from(
            dsl.insertInto(USER_PROFILES)
                .set(USER_PROFILES.USER_ID, profile.userId.toString())
                .set(USER_PROFILES.NICKNAME, profile.nickname)
                .set(USER_PROFILES.PROFILE_IMAGE_URL, profile.profileImageUrl)
                .set(USER_PROFILES.BIO, profile.bio)
                .returningResult(USER_PROFILES.ID)
        ).map { record ->
            profile.copy(id = record.getValue(USER_PROFILES.ID))
        }
    }

    fun findByUserId(userId: UUID): Mono<UserProfile> {
        return Mono.from(
            dsl.selectFrom(USER_PROFILES)
                .where(USER_PROFILES.USER_ID.eq(userId.toString()))
                .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> mapToUserProfile(record) }
    }

    fun findByNickname(nickname: String): Mono<UserProfile> {
        return Mono.from(
            dsl.selectFrom(USER_PROFILES)
                .where(USER_PROFILES.NICKNAME.eq(nickname))
                .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> mapToUserProfile(record) }
    }

    fun update(profile: UserProfile): Mono<UserProfile> {
        return Mono.from(
            dsl.update(USER_PROFILES)
                .set(USER_PROFILES.NICKNAME, profile.nickname)
                .set(USER_PROFILES.PROFILE_IMAGE_URL, profile.profileImageUrl)
                .set(USER_PROFILES.BIO, profile.bio)
                .set(USER_PROFILES.FOLLOWER_COUNT, profile.followerCount)
                .set(USER_PROFILES.FOLLOWING_COUNT, profile.followingCount)
                .where(USER_PROFILES.USER_ID.eq(profile.userId.toString()))
                .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
        ).thenReturn(profile)
    }

    fun existsByNickname(nickname: String): Mono<Boolean> {
        return Mono.from(
            dsl.selectCount()
                .from(USER_PROFILES)
                .where(USER_PROFILES.NICKNAME.eq(nickname))
                .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 사용자 ID로 프로필 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 프로필 존재 여부
     */
    fun existsByUserId(userId: UUID): Mono<Boolean> {
        return Mono.from(
            dsl.selectCount()
                .from(USER_PROFILES)
                .where(USER_PROFILES.USER_ID.eq(userId.toString()))
                .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 프로필 Soft Delete
     *
     * @param userId 사용자 ID
     * @param deletedBy 삭제한 사용자 ID
     */
    fun softDelete(userId: UUID, deletedBy: UUID): Mono<Void> {
        return Mono.from(
            dsl.update(USER_PROFILES)
                .set(USER_PROFILES.DELETED_AT, java.time.LocalDateTime.now())
                .set(USER_PROFILES.UPDATED_AT, java.time.LocalDateTime.now())
                .set(USER_PROFILES.UPDATED_BY, deletedBy.toString())
                .where(USER_PROFILES.USER_ID.eq(userId.toString()))
                .and(USER_PROFILES.DELETED_AT.isNull)
        ).then()
    }

    /**
     * 프로필 복원 (Soft Delete 취소)
     *
     * 탈퇴한 사용자가 재가입할 때 기존 프로필을 복원합니다.
     *
     * @param userId 사용자 ID
     * @return 복원된 프로필 정보
     */
    fun restore(userId: UUID): Mono<UserProfile> {
        return Mono.from(
            dsl.update(USER_PROFILES)
                .set(USER_PROFILES.DELETED_AT, null as java.time.LocalDateTime?)
                .set(USER_PROFILES.UPDATED_AT, java.time.LocalDateTime.now())
                .set(USER_PROFILES.UPDATED_BY, userId.toString())
                .where(USER_PROFILES.USER_ID.eq(userId.toString()))
                .and(USER_PROFILES.DELETED_AT.isNotNull)  // 삭제된 데이터만 복원
        ).then(findByUserId(userId))
    }

    /**
     * 여러 사용자의 프로필 정보를 일괄 조회
     *
     * N+1 쿼리 문제를 방지하기 위해 IN 절을 사용하여 한 번에 조회합니다.
     *
     * @param userIds 조회할 사용자 ID 목록
     * @return 사용자 ID를 키로 하는 UserInfo Map을 담은 Mono
     */
    fun findUserInfosByUserIds(userIds: Set<UUID>): Mono<Map<UUID, UserInfo>> {
        if (userIds.isEmpty()) {
            return Mono.just(emptyMap())
        }

        return Flux.from(
            dsl
                .select(USER_PROFILES.USER_ID, USER_PROFILES.NICKNAME, USER_PROFILES.PROFILE_IMAGE_URL)
                .from(USER_PROFILES)
                .where(USER_PROFILES.USER_ID.`in`(userIds.map { it.toString() }))
                .and(USER_PROFILES.DELETED_AT.isNull)
        )
            .collectMap(
                { UUID.fromString(it.getValue(USER_PROFILES.USER_ID)) },
                {
                    UserInfo(
                        nickname = it.getValue(USER_PROFILES.NICKNAME) ?: "Unknown",
                        profileImageUrl = it.getValue(USER_PROFILES.PROFILE_IMAGE_URL)
                    )
                }
            )
    }

    /**
     * 여러 사용자의 전체 프로필을 일괄 조회
     *
     * N+1 쿼리 문제를 방지하기 위해 IN 절을 사용하여 한 번에 조회합니다.
     *
     * @param userIds 조회할 사용자 ID 목록
     * @return UserProfile 목록
     */
    fun findByUserIds(userIds: Set<UUID>): Flux<UserProfile> {
        if (userIds.isEmpty()) {
            return Flux.empty()
        }

        return Flux.from(
            dsl.selectFrom(USER_PROFILES)
                .where(USER_PROFILES.USER_ID.`in`(userIds.map { it.toString() }))
                .and(USER_PROFILES.DELETED_AT.isNull)
        ).map { record -> mapToUserProfile(record) }
    }

    private fun mapToUserProfile(record: UserProfilesRecord): UserProfile {
        return UserProfile(
            id = record.id,
            userId = UUID.fromString(record.userId!!),
            nickname = record.nickname!!,
            profileImageUrl = record.profileImageUrl,
            bio = record.bio,
            followerCount = record.followerCount ?: 0,
            followingCount = record.followingCount ?: 0,
            createdAt = record.createdAt!!,
            updatedAt = record.updatedAt!!
        )
    }
}
