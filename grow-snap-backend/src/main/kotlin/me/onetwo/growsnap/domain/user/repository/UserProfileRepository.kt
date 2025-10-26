package me.onetwo.growsnap.domain.user.repository

import me.onetwo.growsnap.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.jooq.generated.tables.records.UserProfilesRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * 사용자 프로필 Repository
 */
@Repository
class UserProfileRepository(
    private val dsl: DSLContext
) {
    fun save(profile: UserProfile): UserProfile {
        val record = dsl.insertInto(USER_PROFILES)
            .set(USER_PROFILES.USER_ID, profile.userId.toString())
            .set(USER_PROFILES.NICKNAME, profile.nickname)
            .set(USER_PROFILES.PROFILE_IMAGE_URL, profile.profileImageUrl)
            .set(USER_PROFILES.BIO, profile.bio)
            .returning()
            .fetchOne()!!

        return profile.copy(id = record.id)
    }

    fun findByUserId(userId: UUID): UserProfile? {
        return dsl.selectFrom(USER_PROFILES)
            .where(USER_PROFILES.USER_ID.eq(userId.toString()))
            .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
            .fetchOne()
            ?.let { mapToUserProfile(it) }
    }

    fun findByNickname(nickname: String): UserProfile? {
        return dsl.selectFrom(USER_PROFILES)
            .where(USER_PROFILES.NICKNAME.eq(nickname))
            .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
            .fetchOne()
            ?.let { mapToUserProfile(it) }
    }

    fun update(profile: UserProfile): UserProfile {
        dsl.update(USER_PROFILES)
            .set(USER_PROFILES.NICKNAME, profile.nickname)
            .set(USER_PROFILES.PROFILE_IMAGE_URL, profile.profileImageUrl)
            .set(USER_PROFILES.BIO, profile.bio)
            .set(USER_PROFILES.FOLLOWER_COUNT, profile.followerCount)
            .set(USER_PROFILES.FOLLOWING_COUNT, profile.followingCount)
            .where(USER_PROFILES.USER_ID.eq(profile.userId.toString()))
            .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
            .execute()

        return profile
    }

    fun existsByNickname(nickname: String): Boolean {
        return dsl.fetchExists(
            dsl.selectFrom(USER_PROFILES)
                .where(USER_PROFILES.NICKNAME.eq(nickname))
                .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
        )
    }

    /**
     * 사용자 ID로 프로필 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 프로필 존재 여부
     */
    fun existsByUserId(userId: UUID): Boolean {
        return dsl.fetchExists(
            dsl.selectFrom(USER_PROFILES)
                .where(USER_PROFILES.USER_ID.eq(userId.toString()))
                .and(USER_PROFILES.DELETED_AT.isNull)  // Soft delete 필터링
        )
    }

    /**
     * 프로필 Soft Delete
     *
     * @param userId 사용자 ID
     * @param deletedBy 삭제한 사용자 ID
     */
    fun softDelete(userId: UUID, deletedBy: UUID) {
        dsl.update(USER_PROFILES)
            .set(USER_PROFILES.DELETED_AT, java.time.LocalDateTime.now())
            .set(USER_PROFILES.UPDATED_AT, java.time.LocalDateTime.now())
            .set(USER_PROFILES.UPDATED_BY, deletedBy.toString())
            .where(USER_PROFILES.USER_ID.eq(userId.toString()))
            .and(USER_PROFILES.DELETED_AT.isNull)
            .execute()
    }

    /**
     * 여러 사용자의 프로필 정보를 일괄 조회
     *
     * N+1 쿼리 문제를 방지하기 위해 IN 절을 사용하여 한 번에 조회합니다.
     *
     * @param userIds 조회할 사용자 ID 목록
     * @return 사용자 ID를 키로 하는 (닉네임, 프로필 이미지 URL) Map
     */
    fun findUserInfosByUserIds(userIds: Set<UUID>): Map<UUID, Pair<String, String?>> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        return dsl
            .select(USER_PROFILES.USER_ID, USER_PROFILES.NICKNAME, USER_PROFILES.PROFILE_IMAGE_URL)
            .from(USER_PROFILES)
            .where(USER_PROFILES.USER_ID.`in`(userIds.map { it.toString() }))
            .and(USER_PROFILES.DELETED_AT.isNull)
            .fetch()
            .associate {
                UUID.fromString(it.getValue(USER_PROFILES.USER_ID)) to
                    Pair(
                        it.getValue(USER_PROFILES.NICKNAME) ?: "Unknown",
                        it.getValue(USER_PROFILES.PROFILE_IMAGE_URL)
                    )
            }
    }

    /**
     * 여러 사용자의 전체 프로필을 일괄 조회
     *
     * N+1 쿼리 문제를 방지하기 위해 IN 절을 사용하여 한 번에 조회합니다.
     *
     * @param userIds 조회할 사용자 ID 목록
     * @return UserProfile 목록
     */
    fun findByUserIds(userIds: Set<UUID>): List<UserProfile> {
        if (userIds.isEmpty()) {
            return emptyList()
        }

        return dsl
            .selectFrom(USER_PROFILES)
            .where(USER_PROFILES.USER_ID.`in`(userIds.map { it.toString() }))
            .and(USER_PROFILES.DELETED_AT.isNull)
            .fetch()
            .map { mapToUserProfile(it) }
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
