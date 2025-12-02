package me.onetwo.growsnap.domain.user.repository

import me.onetwo.growsnap.jooq.generated.tables.references.FOLLOWS
import me.onetwo.growsnap.domain.user.model.Follow
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 팔로우 Repository
 */
@Repository
class FollowRepository(
    private val dsl: DSLContext
) {
    fun save(follow: Follow): Mono<Follow> {
        return Mono.from(
            dsl.insertInto(FOLLOWS)
                .set(FOLLOWS.FOLLOWER_ID, follow.followerId.toString())
                .set(FOLLOWS.FOLLOWING_ID, follow.followingId.toString())
                .returningResult(FOLLOWS.ID)
        ).map { record ->
            follow.copy(id = record.getValue(FOLLOWS.ID))
        }
    }

    /**
     * 팔로우 삭제 (Soft Delete)
     *
     * @param followerId 팔로워 ID
     * @param followingId 팔로잉 ID
     */
    fun softDelete(followerId: UUID, followingId: UUID): Mono<Void> {
        return Mono.from(
            dsl.update(FOLLOWS)
                .set(FOLLOWS.DELETED_AT, Instant.now())
                .set(FOLLOWS.UPDATED_AT, Instant.now())
                .set(FOLLOWS.UPDATED_BY, followerId.toString())  // 언팔로우를 수행한 사용자
                .where(FOLLOWS.FOLLOWER_ID.eq(followerId.toString()))
                .and(FOLLOWS.FOLLOWING_ID.eq(followingId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)  // 이미 삭제된 데이터는 제외
        ).then()
    }

    fun existsByFollowerIdAndFollowingId(followerId: UUID, followingId: UUID): Mono<Boolean> {
        return Mono.from(
            dsl.selectCount()
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWER_ID.eq(followerId.toString()))
                .and(FOLLOWS.FOLLOWING_ID.eq(followingId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    fun countByFollowerId(followerId: UUID): Mono<Int> {
        return Mono.from(
            dsl.selectCount()
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWER_ID.eq(followerId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> record.value1() }
            .defaultIfEmpty(0)
    }

    fun countByFollowingId(followingId: UUID): Mono<Int> {
        return Mono.from(
            dsl.selectCount()
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWING_ID.eq(followingId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)  // Soft delete 필터링
        ).map { record -> record.value1() }
            .defaultIfEmpty(0)
    }

    /**
     * 사용자의 모든 팔로우 관계 soft delete (탈퇴 시 사용)
     *
     * 사용자가 팔로우한 관계와 사용자를 팔로우한 관계 모두 삭제합니다.
     *
     * @param userId 사용자 ID
     * @param deletedBy 삭제한 사용자 ID
     */
    fun softDeleteAllByUserId(userId: UUID, deletedBy: UUID): Mono<Void> {
        val now = Instant.now()

        // 사용자가 팔로우한 관계 삭제
        val deleteFollowing = Mono.from(
            dsl.update(FOLLOWS)
                .set(FOLLOWS.DELETED_AT, now)
                .set(FOLLOWS.UPDATED_AT, now)
                .set(FOLLOWS.UPDATED_BY, deletedBy.toString())
                .where(FOLLOWS.FOLLOWER_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)
        )

        // 사용자를 팔로우한 관계 삭제
        val deleteFollowers = Mono.from(
            dsl.update(FOLLOWS)
                .set(FOLLOWS.DELETED_AT, now)
                .set(FOLLOWS.UPDATED_AT, now)
                .set(FOLLOWS.UPDATED_BY, deletedBy.toString())
                .where(FOLLOWS.FOLLOWING_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)
        )

        return deleteFollowing.then(deleteFollowers).then()
    }

    /**
     * 사용자의 팔로워 사용자 ID 목록 조회
     *
     * 해당 사용자를 팔로우하는 사용자들의 ID 목록을 반환합니다.
     * Soft Delete된 팔로우 관계는 제외됩니다.
     *
     * @param userId 사용자 ID
     * @return 팔로워 사용자 ID 목록 (Set)
     */
    fun findFollowerUserIds(userId: UUID): Flux<UUID> {
        return Flux.from(
            dsl.select(FOLLOWS.FOLLOWER_ID)
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWING_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)
        ).map { record -> UUID.fromString(record.getValue(FOLLOWS.FOLLOWER_ID)) }
    }

    /**
     * 사용자의 팔로잉 사용자 ID 목록 조회
     *
     * 해당 사용자가 팔로우하는 사용자들의 ID 목록을 반환합니다.
     * Soft Delete된 팔로우 관계는 제외됩니다.
     *
     * @param userId 사용자 ID
     * @return 팔로잉 사용자 ID 목록 (Set)
     */
    fun findFollowingUserIds(userId: UUID): Flux<UUID> {
        return Flux.from(
            dsl.select(FOLLOWS.FOLLOWING_ID)
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWER_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT.isNull)
        ).map { record -> UUID.fromString(record.getValue(FOLLOWS.FOLLOWING_ID)) }
    }
}
