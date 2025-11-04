package me.onetwo.growsnap.domain.user.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDateTime
import java.util.UUID
import me.onetwo.growsnap.domain.user.event.FollowEvent
import me.onetwo.growsnap.domain.user.exception.AlreadyFollowingException
import me.onetwo.growsnap.domain.user.exception.CannotFollowSelfException
import me.onetwo.growsnap.domain.user.exception.NotFollowingException
import me.onetwo.growsnap.domain.user.model.Follow
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.FollowRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux

/**
 * FollowService 단위 테스트
 */
@ExtendWith(MockKExtension::class)
@DisplayName("팔로우 Service 테스트")
class FollowServiceTest {

    private lateinit var followRepository: FollowRepository
    private lateinit var userService: UserService
    private lateinit var userProfileService: UserProfileService
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var eventPublisher: ReactiveEventPublisher
    private lateinit var followService: FollowService

    private lateinit var followerUser: User
    private lateinit var followingUser: User
    private lateinit var followerProfile: UserProfile
    private lateinit var followingProfile: UserProfile

    @BeforeEach
    fun setUp() {
        followRepository = mockk()
        userService = mockk()
        userProfileService = mockk()
        userProfileRepository = mockk()
        eventPublisher = mockk(relaxed = true)
        followService = FollowServiceImpl(
            followRepository,
            userService,
            userProfileService,
            userProfileRepository,
            eventPublisher
        )

        followerUser = User(
            id = UUID.randomUUID(),
            email = "follower@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = "follower-123",
            role = UserRole.USER
        )

        followingUser = User(
            id = UUID.randomUUID(),
            email = "following@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = "following-456",
            role = UserRole.USER
        )

        followerProfile = UserProfile(
            id = null,
            userId = UUID.randomUUID(),
            nickname = "follower",
            followingCount = 0
        )

        followingProfile = UserProfile(
            id = null,
            userId = UUID.randomUUID(),
            nickname = "following",
            followerCount = 0
        )
    }

    @Test
    @DisplayName("팔로우 성공")
    fun follow_Success() {
        // Given
        val followerId = UUID.randomUUID()
        val followingId = UUID.randomUUID()

        val follow = Follow(
            id = null,
            followerId = followerId,
            followingId = followingId
        )

        every { userService.getUserById(followerId) } returns Mono.just(followerUser)
        every { userService.getUserById(followingId) } returns Mono.just(followingUser)
        every {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        } returns Mono.just(false)
        every { followRepository.save(any()) } returns Mono.just(follow)
        every { userProfileService.incrementFollowingCount(followerId) } returns
                Mono.just(followerProfile.copy(followingCount = 1))
        every { userProfileService.incrementFollowerCount(followingId) } returns
                Mono.just(followingProfile.copy(followerCount = 1))

        // When
        val result = followService.follow(followerId, followingId).block()!!

        // Then
        assertEquals(follow, result)
        verify(exactly = 1) { userService.getUserById(followerId) }
        verify(exactly = 1) { userService.getUserById(followingId) }
        verify(exactly = 1) {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        }
        verify(exactly = 1) { followRepository.save(any()) }
        verify(exactly = 1) { userProfileService.incrementFollowingCount(followerId) }
        verify(exactly = 1) { userProfileService.incrementFollowerCount(followingId) }
        verify(exactly = 1) {
            eventPublisher.publish(
                match<FollowEvent> { event ->
                    event.followerId == followerId && event.followingId == followingId
                }
            )
        }
    }

    @Test
    @DisplayName("팔로우 실패 - 자기 자신 팔로우")
    fun follow_SelfFollow_ThrowsException() {
        // Given
        val userId = UUID.randomUUID()

        // When & Then
        assertThrows<CannotFollowSelfException> {
            followService.follow(userId, userId).block()!!
        }

        verify(exactly = 0) { userService.getUserById(any()) }
        verify(exactly = 0) { followRepository.save(any()) }
    }

    @Test
    @DisplayName("팔로우 실패 - 이미 팔로우 중")
    fun follow_AlreadyFollowing_ThrowsException() {
        // Given
        val followerId = UUID.randomUUID()
        val followingId = UUID.randomUUID()

        every { userService.getUserById(followerId) } returns Mono.just(followerUser)
        every { userService.getUserById(followingId) } returns Mono.just(followingUser)
        every {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        } returns Mono.just(true)
        // When & Then
        val exception = assertThrows<AlreadyFollowingException> {
            followService.follow(followerId, followingId).block()!!
        }

        assertEquals(followingId, exception.followingId)
        verify(exactly = 1) { userService.getUserById(followerId) }
        verify(exactly = 1) { userService.getUserById(followingId) }
        verify(exactly = 1) {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        }
        verify(exactly = 0) { followRepository.save(any()) }
    }

    @Test
    @DisplayName("언팔로우 성공")
    fun unfollow_Success() {
        // Given
        val followerId = UUID.randomUUID()
        val followingId = UUID.randomUUID()

        every { userService.getUserById(followerId) } returns Mono.just(followerUser)
        every { userService.getUserById(followingId) } returns Mono.just(followingUser)
        every {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        } returns Mono.just(true)
        every { followRepository.softDelete(followerId, followingId) } returns Mono.empty<Void>()
        every { userProfileService.decrementFollowingCount(followerId) } returns
                Mono.just(followerProfile.copy(followingCount = 0))
        every { userProfileService.decrementFollowerCount(followingId) } returns
                Mono.just(followingProfile.copy(followerCount = 0))

        // When
        followService.unfollow(followerId, followingId).block()

        // Then
        verify(exactly = 1) { userService.getUserById(followerId) }
        verify(exactly = 1) { userService.getUserById(followingId) }
        verify(exactly = 1) {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        }
        verify(exactly = 1) { followRepository.softDelete(followerId, followingId) }
        verify(exactly = 1) { userProfileService.decrementFollowingCount(followerId) }
        verify(exactly = 1) { userProfileService.decrementFollowerCount(followingId) }
    }

    @Test
    @DisplayName("언팔로우 실패 - 팔로우하지 않음")
    fun unfollow_NotFollowing_ThrowsException() {
        // Given
        val followerId = UUID.randomUUID()
        val followingId = UUID.randomUUID()

        every { userService.getUserById(followerId) } returns Mono.just(followerUser)
        every { userService.getUserById(followingId) } returns Mono.just(followingUser)
        every {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        } returns Mono.just(false)
        every { userProfileService.decrementFollowingCount(any()) } returns Mono.just(followerProfile)
        every { userProfileService.decrementFollowerCount(any()) } returns Mono.just(followingProfile)
        // When & Then
        val exception = assertThrows<NotFollowingException> {
            followService.unfollow(followerId, followingId).block()!!
        }

        assertEquals(followingId, exception.followingId)
        verify(exactly = 1) { userService.getUserById(followerId) }
        verify(exactly = 1) { userService.getUserById(followingId) }
        verify(exactly = 1) {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        }
        verify(exactly = 0) { followRepository.softDelete(any(), any()) }
    }

    @Test
    @DisplayName("팔로우 관계 확인 - 팔로우 중")
    fun isFollowing_Following_ReturnsTrue() {
        // Given
        val followerId = UUID.randomUUID()
        val followingId = UUID.randomUUID()

        every {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        } returns Mono.just(true)
        // When
        val result = followService.isFollowing(followerId, followingId).block()!!

        // Then
        assertTrue(result)
        verify(exactly = 1) {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        }
    }

    @Test
    @DisplayName("팔로우 관계 확인 - 팔로우하지 않음")
    fun isFollowing_NotFollowing_ReturnsFalse() {
        // Given
        val followerId = UUID.randomUUID()
        val followingId = UUID.randomUUID()

        every {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        } returns Mono.just(false)
        // When
        val result = followService.isFollowing(followerId, followingId).block()!!

        // Then
        assertFalse(result)
        verify(exactly = 1) {
            followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)
        }
    }

    @Test
    @DisplayName("팔로잉 수 조회")
    fun getFollowingCount_ReturnsCount() {
        // Given: 팔로잉 수를 조회할 사용자
        val userId = UUID.randomUUID()
        val count = 5

        every { followRepository.countByFollowerId(userId) } returns Mono.just(count)
        // When: 팔로잉 수 조회
        val result = followService.getFollowingCount(userId).block()!!

        // Then: 팔로잉 수 반환
        assertEquals(count, result)
        verify(exactly = 1) { followRepository.countByFollowerId(userId) }
    }

    @Test
    @DisplayName("팔로워 수 조회")
    fun getFollowerCount_ReturnsCount() {
        // Given: 팔로워 수를 조회할 사용자
        val userId = UUID.randomUUID()
        val count = 10

        every { followRepository.countByFollowingId(userId) } returns Mono.just(count)
        // When: 팔로워 수 조회
        val result = followService.getFollowerCount(userId).block()!!

        // Then: 팔로워 수 반환
        assertEquals(count, result)
        verify(exactly = 1) { followRepository.countByFollowingId(userId) }
    }

    @Test
    @DisplayName("팔로워 목록 조회 - 성공")
    fun getFollowers_Success() {
        // Given: 팔로워 목록
        val userId = UUID.randomUUID()
        val follower1Id = UUID.randomUUID()
        val follower2Id = UUID.randomUUID()

        val follower1Profile = UserProfile(
            id = 1L,
            userId = follower1Id,
            nickname = "follower1",
            profileImageUrl = "https://example.com/profile1.jpg",
            bio = "follower1 bio",
            followerCount = 10,
            followingCount = 5,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val follower2Profile = UserProfile(
            id = 2L,
            userId = follower2Id,
            nickname = "follower2",
            profileImageUrl = null,
            bio = null,
            followerCount = 20,
            followingCount = 15,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { followRepository.findFollowerUserIds(userId) } returns Flux.fromIterable(setOf(follower1Id, follower2Id))
        every { userProfileRepository.findByUserIds(setOf(follower1Id, follower2Id)) } returns
            Flux.fromIterable(listOf(follower1Profile, follower2Profile))

        // When
        val result = followService.getFollowers(userId).collectList().block()!!

        // Then
        assertEquals(2, result.size)
        assertEquals("follower1", result.find { it.userId == follower1Id }?.nickname)
        assertEquals("follower2", result.find { it.userId == follower2Id }?.nickname)
        verify(exactly = 1) { followRepository.findFollowerUserIds(userId) }
        verify(exactly = 1) { userProfileRepository.findByUserIds(setOf(follower1Id, follower2Id)) }
    }

    @Test
    @DisplayName("팔로워 목록 조회 - 팔로워가 없는 경우")
    fun getFollowers_NoFollowers_ReturnsEmptyList() {
        // Given: 팔로워가 없음
        val userId = UUID.randomUUID()

        every { followRepository.findFollowerUserIds(userId) } returns Flux.empty()
        // When
        val result = followService.getFollowers(userId).collectList().block()!!

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { followRepository.findFollowerUserIds(userId) }
        verify(exactly = 0) { userProfileRepository.findByUserIds(any()) }
    }

    @Test
    @DisplayName("팔로잉 목록 조회 - 성공")
    fun getFollowing_Success() {
        // Given: 팔로잉 목록
        val userId = UUID.randomUUID()
        val following1Id = UUID.randomUUID()
        val following2Id = UUID.randomUUID()

        val following1Profile = UserProfile(
            id = 3L,
            userId = following1Id,
            nickname = "following1",
            profileImageUrl = "https://example.com/profile3.jpg",
            bio = "following1 bio",
            followerCount = 100,
            followingCount = 50,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val following2Profile = UserProfile(
            id = 4L,
            userId = following2Id,
            nickname = "following2",
            profileImageUrl = null,
            bio = "Hello",
            followerCount = 200,
            followingCount = 150,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        every { followRepository.findFollowingUserIds(userId) } returns Flux.fromIterable(setOf(following1Id, following2Id))
        every { userProfileRepository.findByUserIds(setOf(following1Id, following2Id)) } returns
            Flux.fromIterable(listOf(following1Profile, following2Profile))

        // When
        val result = followService.getFollowing(userId).collectList().block()!!

        // Then
        assertEquals(2, result.size)
        assertEquals("following1", result.find { it.userId == following1Id }?.nickname)
        assertEquals("following2", result.find { it.userId == following2Id }?.nickname)
        verify(exactly = 1) { followRepository.findFollowingUserIds(userId) }
        verify(exactly = 1) { userProfileRepository.findByUserIds(setOf(following1Id, following2Id)) }
    }

    @Test
    @DisplayName("팔로잉 목록 조회 - 팔로잉이 없는 경우")
    fun getFollowing_NoFollowing_ReturnsEmptyList() {
        // Given: 팔로잉이 없음
        val userId = UUID.randomUUID()

        every { followRepository.findFollowingUserIds(userId) } returns Flux.empty()
        // When
        val result = followService.getFollowing(userId).collectList().block()!!

        // Then
        assertTrue(result.isEmpty())
        verify(exactly = 1) { followRepository.findFollowingUserIds(userId) }
        verify(exactly = 0) { userProfileRepository.findByUserIds(any()) }
    }
}
