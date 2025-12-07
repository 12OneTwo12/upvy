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
 *
 * ## Soft Delete 처리
 * - deleted_at_unix = 0: 활성 상태
 * - deleted_at_unix = Unix timestamp: 삭제된 상태
 * - UNIQUE 제약조건 (follower_id, following_id, deleted_at_unix)으로 중복 방지
 */
@Repository
class FollowRepository(
    private val dsl: DSLContext
) {
    /**
     * 팔로우 생성
     *
     * @param follow 팔로우 정보
     * @return 생성된 팔로우
     */
    fun save(follow: Follow): Mono<Follow> {
        val now = Instant.now()
        val followerIdStr = follow.followerId.toString()

        return Mono.from(
            dsl.insertInto(FOLLOWS)
                .set(FOLLOWS.FOLLOWER_ID, followerIdStr)
                .set(FOLLOWS.FOLLOWING_ID, follow.followingId.toString())
                .set(FOLLOWS.CREATED_AT, now)
                .set(FOLLOWS.CREATED_BY, followerIdStr)
                .set(FOLLOWS.UPDATED_AT, now)
                .set(FOLLOWS.UPDATED_BY, followerIdStr)
                .set(FOLLOWS.DELETED_AT_UNIX, 0L)
                .returningResult(FOLLOWS.ID)
        ).map { record ->
            follow.copy(id = record.getValue(FOLLOWS.ID))
        }
    }

    /**
     * 팔로우 삭제 (Soft Delete)
     *
     * deleted_at_unix를 현재 Unix timestamp로 설정하여 유니크 제약조건을 우회합니다.
     *
     * @param followerId 팔로워 ID
     * @param followingId 팔로잉 ID
     */
    fun softDelete(followerId: UUID, followingId: UUID): Mono<Void> {
        val now = Instant.now()
        val nowUnix = now.epochSecond

        return Mono.from(
            dsl.update(FOLLOWS)
                .set(FOLLOWS.DELETED_AT, now)
                .set(FOLLOWS.DELETED_AT_UNIX, nowUnix)
                .set(FOLLOWS.UPDATED_AT, now)
                .set(FOLLOWS.UPDATED_BY, followerId.toString())
                .where(FOLLOWS.FOLLOWER_ID.eq(followerId.toString()))
                .and(FOLLOWS.FOLLOWING_ID.eq(followingId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
        ).then()
    }

    /**
     * 팔로우 관계 존재 여부 확인
     *
     * 활성 상태 (deleted_at_unix = 0)인 팔로우만 확인합니다.
     *
     * @param followerId 팔로워 ID
     * @param followingId 팔로잉 ID
     * @return 팔로우 여부
     */
    fun existsByFollowerIdAndFollowingId(followerId: UUID, followingId: UUID): Mono<Boolean> {
        return Mono.from(
            dsl.selectCount()
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWER_ID.eq(followerId.toString()))
                .and(FOLLOWS.FOLLOWING_ID.eq(followingId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
        ).map { record -> record.value1() > 0 }
            .defaultIfEmpty(false)
    }

    /**
     * 팔로잉 수 조회
     *
     * @param followerId 팔로워 ID
     * @return 팔로잉 수
     */
    fun countByFollowerId(followerId: UUID): Mono<Int> {
        return Mono.from(
            dsl.selectCount()
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWER_ID.eq(followerId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
        ).map { record -> record.value1() }
            .defaultIfEmpty(0)
    }

    /**
     * 팔로워 수 조회
     *
     * @param followingId 팔로잉 ID
     * @return 팔로워 수
     */
    fun countByFollowingId(followingId: UUID): Mono<Int> {
        return Mono.from(
            dsl.selectCount()
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWING_ID.eq(followingId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
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
        val nowUnix = now.epochSecond

        // 사용자가 팔로우한 관계 삭제
        val deleteFollowing = Mono.from(
            dsl.update(FOLLOWS)
                .set(FOLLOWS.DELETED_AT, now)
                .set(FOLLOWS.DELETED_AT_UNIX, nowUnix)
                .set(FOLLOWS.UPDATED_AT, now)
                .set(FOLLOWS.UPDATED_BY, deletedBy.toString())
                .where(FOLLOWS.FOLLOWER_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
        )

        // 사용자를 팔로우한 관계 삭제
        val deleteFollowers = Mono.from(
            dsl.update(FOLLOWS)
                .set(FOLLOWS.DELETED_AT, now)
                .set(FOLLOWS.DELETED_AT_UNIX, nowUnix)
                .set(FOLLOWS.UPDATED_AT, now)
                .set(FOLLOWS.UPDATED_BY, deletedBy.toString())
                .where(FOLLOWS.FOLLOWING_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
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
     * @return 팔로워 사용자 ID 목록
     */
    fun findFollowerUserIds(userId: UUID): Flux<UUID> {
        return Flux.from(
            dsl.select(FOLLOWS.FOLLOWER_ID)
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWING_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
        ).map { record -> UUID.fromString(record.getValue(FOLLOWS.FOLLOWER_ID)) }
    }

    /**
     * 사용자의 팔로잉 사용자 ID 목록 조회
     *
     * 해당 사용자가 팔로우하는 사용자들의 ID 목록을 반환합니다.
     * Soft Delete된 팔로우 관계는 제외됩니다.
     *
     * @param userId 사용자 ID
     * @return 팔로잉 사용자 ID 목록
     */
    fun findFollowingUserIds(userId: UUID): Flux<UUID> {
        return Flux.from(
            dsl.select(FOLLOWS.FOLLOWING_ID)
                .from(FOLLOWS)
                .where(FOLLOWS.FOLLOWER_ID.eq(userId.toString()))
                .and(FOLLOWS.DELETED_AT_UNIX.eq(0L))
        ).map { record -> UUID.fromString(record.getValue(FOLLOWS.FOLLOWING_ID)) }
    }
}
