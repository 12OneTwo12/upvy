package me.onetwo.growsnap.domain.user.service

import me.onetwo.growsnap.domain.user.dto.UserProfileResponse
import me.onetwo.growsnap.domain.user.model.Follow
import java.util.UUID

/**
 * 팔로우 관리 서비스 인터페이스
 *
 * 사용자 간 팔로우/언팔로우 관계를 관리하고, 팔로워/팔로잉 수를 업데이트합니다.
 */
interface FollowService {

    /**
     * 사용자 팔로우
     *
     * follower가 following을 팔로우합니다.
     * 팔로우 관계 생성 후 양쪽 사용자의 팔로워/팔로잉 수를 업데이트합니다.
     *
     * @param followerId 팔로우하는 사용자 ID
     * @param followingId 팔로우받는 사용자 ID
     * @return 생성된 팔로우 관계
     * @throws me.onetwo.growsnap.domain.user.exception.CannotFollowSelfException 자기 자신을 팔로우하려는 경우
     * @throws me.onetwo.growsnap.domain.user.exception.AlreadyFollowingException 이미 팔로우 중인 경우
     */
    fun follow(followerId: UUID, followingId: UUID): Follow

    /**
     * 사용자 언팔로우
     *
     * follower가 following을 언팔로우합니다.
     * 팔로우 관계 삭제 후 양쪽 사용자의 팔로워/팔로잉 수를 업데이트합니다.
     *
     * @param followerId 언팔로우하는 사용자 ID
     * @param followingId 언팔로우받는 사용자 ID
     * @throws me.onetwo.growsnap.domain.user.exception.NotFollowingException 팔로우하지 않은 사용자를 언팔로우하려는 경우
     */
    fun unfollow(followerId: UUID, followingId: UUID)

    /**
     * 팔로우 관계 확인
     *
     * @param followerId 팔로우하는 사용자 ID
     * @param followingId 팔로우받는 사용자 ID
     * @return 팔로우 여부 (true: 팔로우 중, false: 팔로우하지 않음)
     */
    fun isFollowing(followerId: UUID, followingId: UUID): Boolean

    /**
     * 사용자의 팔로잉 수 조회
     *
     * @param userId 사용자 ID
     * @return 팔로잉 수
     */
    fun getFollowingCount(userId: UUID): Int

    /**
     * 사용자의 팔로워 수 조회
     *
     * @param userId 사용자 ID
     * @return 팔로워 수
     */
    fun getFollowerCount(userId: UUID): Int

    /**
     * 사용자의 팔로워 목록 조회
     *
     * 해당 사용자를 팔로우하는 사용자들의 프로필 목록을 반환합니다.
     *
     * @param userId 사용자 ID
     * @return 팔로워 프로필 목록
     */
    fun getFollowers(userId: UUID): List<UserProfileResponse>

    /**
     * 사용자의 팔로잉 목록 조회
     *
     * 해당 사용자가 팔로우하는 사용자들의 프로필 목록을 반환합니다.
     *
     * @param userId 사용자 ID
     * @return 팔로잉 프로필 목록
     */
    fun getFollowing(userId: UUID): List<UserProfileResponse>
}
