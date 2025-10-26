package me.onetwo.growsnap.domain.user.service

import me.onetwo.growsnap.domain.user.dto.UserProfileResponse
import me.onetwo.growsnap.domain.user.event.FollowEvent
import me.onetwo.growsnap.domain.user.exception.AlreadyFollowingException
import me.onetwo.growsnap.domain.user.exception.CannotFollowSelfException
import me.onetwo.growsnap.domain.user.exception.NotFollowingException
import me.onetwo.growsnap.domain.user.model.Follow
import me.onetwo.growsnap.domain.user.repository.FollowRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 팔로우 관리 서비스 구현체
 *
 * 사용자 간 팔로우/언팔로우 관계를 관리하고, 팔로워/팔로잉 수를 업데이트합니다.
 * 팔로우 성공 시 Spring Event를 발행하여 비동기 알림 처리를 수행합니다.
 *
 * @property followRepository 팔로우 Repository
 * @property userService 사용자 서비스 (사용자 존재 여부 확인용)
 * @property userProfileService 사용자 프로필 서비스 (팔로워/팔로잉 수 업데이트용)
 * @property userProfileRepository 사용자 프로필 Repository (프로필 정보 조회용)
 * @property applicationEventPublisher Spring Event 발행자 (비동기 알림 처리용)
 */
@Service
@Transactional(readOnly = true)
class FollowServiceImpl(
    private val followRepository: FollowRepository,
    private val userService: UserService,
    private val userProfileService: UserProfileService,
    private val userProfileRepository: UserProfileRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) : FollowService {

    /**
     * 사용자 팔로우
     *
     * follower가 following을 팔로우합니다.
     * 팔로우 관계 생성 후 양쪽 사용자의 팔로워/팔로잉 수를 업데이트하고,
     * FollowEvent를 발행하여 비동기로 알림을 처리합니다.
     *
     * @param followerId 팔로우하는 사용자 ID
     * @param followingId 팔로우받는 사용자 ID
     * @return 생성된 팔로우 관계
     * @throws CannotFollowSelfException 자기 자신을 팔로우하려는 경우
     * @throws AlreadyFollowingException 이미 팔로우 중인 경우
     */
    @Transactional
    override fun follow(followerId: UUID, followingId: UUID): Follow {
        // 자기 자신 팔로우 방지
        if (followerId == followingId) {
            throw CannotFollowSelfException()
        }

        // 사용자 존재 여부 확인
        userService.getUserById(followerId)
        userService.getUserById(followingId)

        // 이미 팔로우 중인지 확인
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw AlreadyFollowingException(followingId)
        }

        // 팔로우 관계 생성
        val follow = Follow(
            followerId = followerId,
            followingId = followingId
        )
        val savedFollow = followRepository.save(follow)

        // 팔로워/팔로잉 수 업데이트
        userProfileService.incrementFollowingCount(followerId)
        userProfileService.incrementFollowerCount(followingId)

        // 팔로우 이벤트 발행 (트랜잭션 커밋 후 비동기 알림 처리)
        logger.debug(
            "Publishing FollowEvent: follower={}, following={}",
            followerId,
            followingId
        )
        applicationEventPublisher.publishEvent(
            FollowEvent(
                followerId = followerId,
                followingId = followingId
            )
        )

        return savedFollow
    }

    /**
     * 사용자 언팔로우
     *
     * follower가 following을 언팔로우합니다.
     * 팔로우 관계 삭제 후 양쪽 사용자의 팔로워/팔로잉 수를 업데이트합니다.
     *
     * @param followerId 언팔로우하는 사용자 ID
     * @param followingId 언팔로우받는 사용자 ID
     * @throws NotFollowingException 팔로우하지 않은 사용자를 언팔로우하려는 경우
     */
    @Transactional
    override fun unfollow(followerId: UUID, followingId: UUID) {
        // 사용자 존재 여부 확인
        userService.getUserById(followerId)
        userService.getUserById(followingId)

        // 팔로우 관계 확인
        if (!followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw NotFollowingException(followingId)
        }

        // 팔로우 관계 삭제 (Soft Delete)
        followRepository.softDelete(followerId, followingId)

        // 팔로워/팔로잉 수 업데이트
        userProfileService.decrementFollowingCount(followerId)
        userProfileService.decrementFollowerCount(followingId)
    }

    /**
     * 팔로우 관계 확인
     *
     * @param followerId 팔로우하는 사용자 ID
     * @param followingId 팔로우받는 사용자 ID
     * @return 팔로우 여부 (true: 팔로우 중, false: 팔로우하지 않음)
     */
    override fun isFollowing(followerId: UUID, followingId: UUID): Boolean {
        return followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
    }

    /**
     * 사용자의 팔로잉 수 조회
     *
     * @param userId 사용자 ID
     * @return 팔로잉 수
     */
    override fun getFollowingCount(userId: UUID): Int {
        return followRepository.countByFollowerId(userId)
    }

    /**
     * 사용자의 팔로워 수 조회
     *
     * @param userId 사용자 ID
     * @return 팔로워 수
     */
    override fun getFollowerCount(userId: UUID): Int {
        return followRepository.countByFollowingId(userId)
    }

    /**
     * 사용자의 팔로워 목록 조회
     *
     * 해당 사용자를 팔로우하는 사용자들의 프로필 목록을 반환합니다.
     * N+1 쿼리 문제를 방지하기 위해 IN 절을 사용하여 일괄 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 팔로워 프로필 목록
     */
    override fun getFollowers(userId: UUID): List<UserProfileResponse> {
        val followerUserIds = followRepository.findFollowerUserIds(userId)

        if (followerUserIds.isEmpty()) {
            return emptyList()
        }

        val userProfiles = userProfileRepository.findUserInfosByUserIds(followerUserIds)

        return followerUserIds.mapNotNull { followerId ->
            userProfiles[followerId]?.let { _ ->
                val profile = userProfileRepository.findByUserId(followerId)
                profile?.let { UserProfileResponse.from(it) }
            }
        }
    }

    /**
     * 사용자의 팔로잉 목록 조회
     *
     * 해당 사용자가 팔로우하는 사용자들의 프로필 목록을 반환합니다.
     * N+1 쿼리 문제를 방지하기 위해 IN 절을 사용하여 일괄 조회합니다.
     *
     * @param userId 사용자 ID
     * @return 팔로잉 프로필 목록
     */
    override fun getFollowing(userId: UUID): List<UserProfileResponse> {
        val followingUserIds = followRepository.findFollowingUserIds(userId)

        if (followingUserIds.isEmpty()) {
            return emptyList()
        }

        val userProfiles = userProfileRepository.findUserInfosByUserIds(followingUserIds)

        return followingUserIds.mapNotNull { followingId ->
            userProfiles[followingId]?.let { _ ->
                val profile = userProfileRepository.findByUserId(followingId)
                profile?.let { UserProfileResponse.from(it) }
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(FollowServiceImpl::class.java)
    }
}
