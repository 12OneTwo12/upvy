package me.onetwo.upvy.domain.user.repository
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest

import me.onetwo.upvy.domain.user.model.Follow
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.jooq.generated.tables.references.FOLLOWS
import me.onetwo.upvy.jooq.generated.tables.references.USER_PROFILES
import me.onetwo.upvy.jooq.generated.tables.references.USERS
import org.jooq.DSLContext
import reactor.core.publisher.Mono
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * FollowRepository 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("팔로우 Repository 테스트")
class FollowRepositoryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var followRepository: FollowRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    

    private lateinit var follower: User
    private lateinit var following: User
    private lateinit var anotherUser: User

    @BeforeEach
    fun setUp() {
        // 테스트 사용자들 생성
        follower = userRepository.save(User(
                email = "follower@example.com",
                role = UserRole.USER
            )).block()!!

        following = userRepository.save(User(
                email = "following@example.com",
                role = UserRole.USER
            )).block()!!

        anotherUser = userRepository.save(User(
                email = "another@example.com",
                role = UserRole.USER
            )).block()!!
    }

    @AfterEach
    fun tearDown() {
        // Delete in correct order to avoid FK constraint violations
        Mono.from(dslContext.deleteFrom(FOLLOWS)).block()
        Mono.from(dslContext.deleteFrom(USER_PROFILES)).block()
        Mono.from(dslContext.deleteFrom(USERS)).block()
    }

    @Test
    @DisplayName("팔로우 저장 성공")
    fun save_Success() {
        // Given
        val follow = Follow(
            followerId = follower.id!!,
            followingId = following.id!!
        )

        // When
        val savedFollow = followRepository.save(follow).block()!!

        // Then
        assertNotNull(savedFollow.id)
        assertEquals(follower.id, savedFollow.followerId)
        assertEquals(following.id, savedFollow.followingId)
    }

    @Test
    @DisplayName("팔로우 삭제 성공")
    fun delete_ExistingFollow_Success() {
        // Given
        val follow = Follow(
            followerId = follower.id!!,
            followingId = following.id!!
        )
        followRepository.save(follow).block()!!

        // When
        followRepository.softDelete(follower.id!!, following.id!!).block()

        // Then
        val exists = followRepository.existsByFollowerIdAndFollowingId(follower.id!!, following.id!!).block()!!
        assertFalse(exists)
    }

    @Test
    @DisplayName("팔로우 관계 존재 확인 - 존재하는 경우")
    fun existsByFollowerIdAndFollowingId_ExistingFollow_ReturnsTrue() {
        // Given
        val follow = Follow(
            followerId = follower.id!!,
            followingId = following.id!!
        )
        followRepository.save(follow).block()!!

        // When
        val exists = followRepository.existsByFollowerIdAndFollowingId(follower.id!!, following.id!!).block()!!

        // Then
        assertTrue(exists)
    }

    @Test
    @DisplayName("팔로우 관계 존재 확인 - 존재하지 않는 경우")
    fun existsByFollowerIdAndFollowingId_NonExistingFollow_ReturnsFalse() {
        // When
        val exists = followRepository.existsByFollowerIdAndFollowingId(follower.id!!, following.id!!).block()!!

        // Then
        assertFalse(exists)
    }

    @Test
    @DisplayName("팔로워 ID로 팔로잉 수 조회 - 여러 명 팔로우")
    fun countByFollowerId_MultipleFollowing_ReturnsCorrectCount() {
        // Given
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = follower.id!!, followingId = anotherUser.id!!)).block()!!

        // When
        val count = followRepository.countByFollowerId(follower.id!!).block()!!

        // Then
        assertEquals(2, count)
    }

    @Test
    @DisplayName("팔로워 ID로 팔로잉 수 조회 - 팔로우 없음")
    fun countByFollowerId_NoFollowing_ReturnsZero() {
        // When
        val count = followRepository.countByFollowerId(follower.id!!).block()!!

        // Then
        assertEquals(0, count)
    }

    @Test
    @DisplayName("팔로잉 ID로 팔로워 수 조회 - 여러 명의 팔로워")
    fun countByFollowingId_MultipleFollowers_ReturnsCorrectCount() {
        // Given
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = anotherUser.id!!, followingId = following.id!!)).block()!!

        // When
        val count = followRepository.countByFollowingId(following.id!!).block()!!

        // Then
        assertEquals(2, count)
    }

    @Test
    @DisplayName("팔로잉 ID로 팔로워 수 조회 - 팔로워 없음")
    fun countByFollowingId_NoFollowers_ReturnsZero() {
        // When
        val count = followRepository.countByFollowingId(following.id!!).block()!!

        // Then
        assertEquals(0, count)
    }

    @Test
    @DisplayName("중복 팔로우 시도 - 예외 발생")
    fun save_DuplicateFollow_ThrowsException() {
        // Given
        val follow = Follow(
            followerId = follower.id!!,
            followingId = following.id!!
        )
        followRepository.save(follow).block()!!

        // When & Then
        val duplicateFollow = Follow(
            followerId = follower.id!!,
            followingId = following.id!!
        )
        assertThrows(Exception::class.java) {
            followRepository.save(duplicateFollow).block()!!
        }
    }

    @Test
    @DisplayName("팔로우 삭제 후 카운트 확인")
    fun delete_ThenCheckCount_ReturnsDecrementedCount() {
        // Given
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = follower.id!!, followingId = anotherUser.id!!)).block()!!

        // When
        followRepository.softDelete(follower.id!!, following.id!!).block()
        val countAfterDelete = followRepository.countByFollowerId(follower.id!!).block()!!

        // Then
        assertEquals(1, countAfterDelete)
    }

    @Test
    @DisplayName("존재하지 않는 팔로우 삭제 시도 - 아무 일도 일어나지 않음")
    fun delete_NonExistingFollow_NoError() {
        // When & Then (예외가 발생하지 않아야 함)
        assertDoesNotThrow {
            followRepository.softDelete(follower.id!!, following.id!!).block()
        }
    }

    @Test
    @DisplayName("양방향 팔로우 확인")
    fun save_BidirectionalFollow_Success() {
        // Given & When
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = following.id!!, followingId = follower.id!!)).block()!!

        // Then
        assertTrue(followRepository.existsByFollowerIdAndFollowingId(follower.id!!, following.id!!).block()!!)
        assertTrue(followRepository.existsByFollowerIdAndFollowingId(following.id!!, follower.id!!).block()!!)
        assertEquals(1, followRepository.countByFollowerId(follower.id!!).block()!!)
        assertEquals(1, followRepository.countByFollowingId(follower.id!!).block()!!)
    }

    @Test
    @DisplayName("팔로워 사용자 ID 목록 조회 - 팔로워가 있는 경우")
    fun findFollowerUserIds_WithFollowers_ReturnsFollowerIds() {
        // Given: following 사용자를 follower와 anotherUser가 팔로우
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = anotherUser.id!!, followingId = following.id!!)).block()!!

        // When: following 사용자의 팔로워 목록 조회
        val followerUserIds = followRepository.findFollowerUserIds(following.id!!).collectList().block()!!

        // Then: 2명의 팔로워 ID가 조회됨
        assertEquals(2, followerUserIds.size)
        assertTrue(followerUserIds.contains(follower.id!!))
        assertTrue(followerUserIds.contains(anotherUser.id!!))
    }

    @Test
    @DisplayName("팔로워 사용자 ID 목록 조회 - 팔로워가 없는 경우")
    fun findFollowerUserIds_NoFollowers_ReturnsEmptySet() {
        // When: 팔로워가 없는 사용자의 팔로워 목록 조회
        val followerUserIds = followRepository.findFollowerUserIds(following.id!!).collectList().block()!!

        // Then: 빈 Set 반환
        assertTrue(followerUserIds.isEmpty())
    }

    @Test
    @DisplayName("팔로워 사용자 ID 목록 조회 - Soft Delete된 팔로우는 제외")
    fun findFollowerUserIds_ExcludesSoftDeleted() {
        // Given: following 사용자를 follower와 anotherUser가 팔로우
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = anotherUser.id!!, followingId = following.id!!)).block()!!

        // follower의 팔로우를 soft delete
        followRepository.softDelete(follower.id!!, following.id!!).block()

        // When: following 사용자의 팔로워 목록 조회
        val followerUserIds = followRepository.findFollowerUserIds(following.id!!).collectList().block()!!

        // Then: anotherUser만 조회됨 (follower는 제외)
        assertEquals(1, followerUserIds.size)
        assertTrue(followerUserIds.contains(anotherUser.id!!))
        assertFalse(followerUserIds.contains(follower.id!!))
    }

    @Test
    @DisplayName("팔로잉 사용자 ID 목록 조회 - 팔로잉이 있는 경우")
    fun findFollowingUserIds_WithFollowing_ReturnsFollowingIds() {
        // Given: follower가 following과 anotherUser를 팔로우
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = follower.id!!, followingId = anotherUser.id!!)).block()!!

        // When: follower 사용자의 팔로잉 목록 조회
        val followingUserIds = followRepository.findFollowingUserIds(follower.id!!).collectList().block()!!

        // Then: 2명의 팔로잉 ID가 조회됨
        assertEquals(2, followingUserIds.size)
        assertTrue(followingUserIds.contains(following.id!!))
        assertTrue(followingUserIds.contains(anotherUser.id!!))
    }

    @Test
    @DisplayName("팔로잉 사용자 ID 목록 조회 - 팔로잉이 없는 경우")
    fun findFollowingUserIds_NoFollowing_ReturnsEmptySet() {
        // When: 팔로잉이 없는 사용자의 팔로잉 목록 조회
        val followingUserIds = followRepository.findFollowingUserIds(follower.id!!).collectList().block()!!

        // Then: 빈 Set 반환
        assertTrue(followingUserIds.isEmpty())
    }

    @Test
    @DisplayName("팔로잉 사용자 ID 목록 조회 - Soft Delete된 팔로우는 제외")
    fun findFollowingUserIds_ExcludesSoftDeleted() {
        // Given: follower가 following과 anotherUser를 팔로우
        followRepository.save(Follow(followerId = follower.id!!, followingId = following.id!!)).block()!!
        followRepository.save(Follow(followerId = follower.id!!, followingId = anotherUser.id!!)).block()!!

        // following에 대한 팔로우를 soft delete
        followRepository.softDelete(follower.id!!, following.id!!).block()

        // When: follower 사용자의 팔로잉 목록 조회
        val followingUserIds = followRepository.findFollowingUserIds(follower.id!!).collectList().block()!!

        // Then: anotherUser만 조회됨 (following은 제외)
        assertEquals(1, followingUserIds.size)
        assertTrue(followingUserIds.contains(anotherUser.id!!))
        assertFalse(followingUserIds.contains(following.id!!))
    }
}
