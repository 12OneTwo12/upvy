package me.onetwo.growsnap.domain.user.service

import java.util.UUID

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.user.exception.DuplicateNicknameException
import me.onetwo.growsnap.domain.user.exception.UserProfileNotFoundException
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.storage.ImageUploadService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux

/**
 * UserProfileService 단위 테스트
 */
@ExtendWith(MockKExtension::class)
@DisplayName("사용자 프로필 Service 테스트")
class UserProfileServiceTest {

    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var userService: UserService
    private lateinit var imageUploadService: ImageUploadService
    private lateinit var userProfileService: UserProfileService

    private lateinit var testUser: User
    private lateinit var testProfile: UserProfile

    @BeforeEach
    fun setUp() {
        userProfileRepository = mockk()
        userService = mockk()
        imageUploadService = mockk()
        userProfileService = UserProfileServiceImpl(userProfileRepository, userService, imageUploadService)

        val testUserId = UUID.randomUUID()

        testUser = User(
            id = testUserId,
            email = "test@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = "google-123",
            role = UserRole.USER
        )

        testProfile = UserProfile(
            id = 1L,
            userId = testUserId,
            nickname = "testnick",
            profileImageUrl = "https://example.com/profile.jpg",
            bio = "테스트 자기소개"
        )
    }

    @Test
    @DisplayName("프로필 생성 성공")
    fun createProfile_Success() {
        // Given
        val userId = UUID.randomUUID()
        val nickname = "newnick"

        every { userService.getUserById(userId) } returns Mono.just(testUser)
        every { userProfileRepository.existsByUserId(userId) } returns Mono.just(false)  // 프로필 중복 체크
        every { userProfileRepository.existsByNickname(nickname) } returns Mono.just(false)
        every { userProfileRepository.save(any()) } returns Mono.just(testProfile.copy(nickname = nickname))

        // When
        val result = userProfileService.createProfile(userId, nickname).block()!!

        // Then
        assertEquals(nickname, result.nickname)
        verify(exactly = 1) { userService.getUserById(userId) }
        verify(exactly = 1) { userProfileRepository.existsByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.existsByNickname(nickname) }
        verify(exactly = 1) { userProfileRepository.save(any()) }
    }

    @Test
    @DisplayName("프로필 생성 실패 - 중복 닉네임")
    fun createProfile_DuplicateNickname_ThrowsException() {
        // Given
        val userId = UUID.randomUUID()
        val nickname = "duplicatenick"

        every { userService.getUserById(userId) } returns Mono.just(testUser)
        every { userProfileRepository.existsByUserId(userId) } returns Mono.just(false)  // 프로필 중복 체크
        every { userProfileRepository.existsByNickname(nickname) } returns Mono.just(true)
        // When & Then
        val exception = assertThrows<DuplicateNicknameException> {
            userProfileService.createProfile(userId, nickname).block()!!
        }

        assertEquals(nickname, exception.nickname)
        verify(exactly = 1) { userService.getUserById(userId) }
        verify(exactly = 1) { userProfileRepository.existsByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.existsByNickname(nickname) }
        verify(exactly = 0) { userProfileRepository.save(any()) }
    }

    @Test
    @DisplayName("사용자 ID로 프로필 조회 성공")
    fun getProfileByUserId_ExistingProfile_ReturnsProfile() {
        // Given
        val userId = UUID.randomUUID()

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(testProfile)
        // When
        val result = userProfileService.getProfileByUserId(userId).block()!!

        // Then
        assertEquals(testProfile, result)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
    }

    @Test
    @DisplayName("사용자 ID로 프로필 조회 실패")
    fun getProfileByUserId_NonExistingProfile_ThrowsException() {
        // Given
        val userId = UUID.randomUUID()

        every { userProfileRepository.findByUserId(userId) } returns Mono.empty()
        // When & Then
        val exception = assertThrows<UserProfileNotFoundException> {
            userProfileService.getProfileByUserId(userId).block()!!
        }

        assertTrue(exception.message!!.contains("$userId"))
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
    }

    @Test
    @DisplayName("닉네임으로 프로필 조회 성공")
    fun getProfileByNickname_ExistingProfile_ReturnsProfile() {
        // Given
        val nickname = "testnick"

        every { userProfileRepository.findByNickname(nickname) } returns Mono.just(testProfile)
        // When
        val result = userProfileService.getProfileByNickname(nickname).block()!!

        // Then
        assertEquals(testProfile, result)
        verify(exactly = 1) { userProfileRepository.findByNickname(nickname) }
    }

    @Test
    @DisplayName("닉네임으로 프로필 조회 실패")
    fun getProfileByNickname_NonExistingProfile_ThrowsException() {
        // Given
        val nickname = "nonexistent"

        every { userProfileRepository.findByNickname(nickname) } returns Mono.empty()
        // When & Then
        val exception = assertThrows<UserProfileNotFoundException> {
            userProfileService.getProfileByNickname(nickname).block()!!
        }

        assertTrue(exception.message!!.contains(nickname))
        verify(exactly = 1) { userProfileRepository.findByNickname(nickname) }
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 중복됨")
    fun isNicknameDuplicated_ExistingNickname_ReturnsTrue() {
        // Given
        val nickname = "existing"

        every { userProfileRepository.existsByNickname(nickname) } returns Mono.just(true)
        // When
        val result = userProfileService.isNicknameDuplicated(nickname).block()!!

        // Then
        assertTrue(result)
        verify(exactly = 1) { userProfileRepository.existsByNickname(nickname) }
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 중복되지 않음")
    fun isNicknameDuplicated_NonExistingNickname_ReturnsFalse() {
        // Given
        val nickname = "new"

        every { userProfileRepository.existsByNickname(nickname) } returns Mono.just(false)
        // When
        val result = userProfileService.isNicknameDuplicated(nickname).block()!!

        // Then
        assertFalse(result)
        verify(exactly = 1) { userProfileRepository.existsByNickname(nickname) }
    }

    @Test
    @DisplayName("프로필 업데이트 - 닉네임 변경 성공")
    fun updateProfile_ChangeNickname_Success() {
        // Given
        val userId = UUID.randomUUID()
        val newNickname = "newnick"

        val updatedProfile = testProfile.copy(nickname = newNickname)

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(testProfile)
        every { userProfileRepository.existsByNickname(newNickname) } returns Mono.just(false)
        every { userProfileRepository.update(any()) } returns Mono.just(updatedProfile)
        // When
        val result = userProfileService.updateProfile(userId, nickname = newNickname).block()!!

        // Then
        assertEquals(newNickname, result.nickname)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.existsByNickname(newNickname) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("프로필 업데이트 - 닉네임 중복으로 실패")
    fun updateProfile_DuplicateNickname_ThrowsException() {
        // Given
        val userId = UUID.randomUUID()
        val newNickname = "duplicatenick"

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(testProfile)
        every { userProfileRepository.existsByNickname(newNickname) } returns Mono.just(true)
        // When & Then
        val exception = assertThrows<DuplicateNicknameException> {
            userProfileService.updateProfile(userId, nickname = newNickname).block()!!
        }

        assertEquals(newNickname, exception.nickname)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.existsByNickname(newNickname) }
        verify(exactly = 0) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("프로필 업데이트 - 같은 닉네임으로 변경 시 중복 검사 안 함")
    fun updateProfile_SameNickname_DoesNotCheckDuplicate() {
        // Given
        val userId = UUID.randomUUID()
        val sameNickname = testProfile.nickname

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(testProfile)
        every { userProfileRepository.update(any()) } returns Mono.just(testProfile)
        // When
        val result = userProfileService.updateProfile(userId, nickname = sameNickname).block()!!

        // Then
        assertEquals(sameNickname, result.nickname)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 0) { userProfileRepository.existsByNickname(any()) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("팔로워 수 증가")
    fun incrementFollowerCount_Success() {
        // Given
        val userId = UUID.randomUUID()
        val updatedProfile = testProfile.copy(followerCount = testProfile.followerCount + 1)

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(testProfile)
        every { userProfileRepository.update(any()) } returns Mono.just(updatedProfile)
        // When
        val result = userProfileService.incrementFollowerCount(userId).block()!!

        // Then
        assertEquals(testProfile.followerCount + 1, result.followerCount)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("팔로워 수 감소")
    fun decrementFollowerCount_Success() {
        // Given
        val userId = UUID.randomUUID()
        val profileWithFollowers = testProfile.copy(followerCount = 5)
        val updatedProfile = profileWithFollowers.copy(followerCount = 4)

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(profileWithFollowers)
        every { userProfileRepository.update(any()) } returns Mono.just(updatedProfile)
        // When
        val result = userProfileService.decrementFollowerCount(userId).block()!!

        // Then
        assertEquals(4, result.followerCount)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("팔로워 수 감소 - 0 이하로 내려가지 않음")
    fun decrementFollowerCount_DoesNotGoBelowZero() {
        // Given
        val userId = UUID.randomUUID()
        val profileWithZeroFollowers = testProfile.copy(followerCount = 0)

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(profileWithZeroFollowers)
        every { userProfileRepository.update(any()) } returns Mono.just(profileWithZeroFollowers)
        // When
        val result = userProfileService.decrementFollowerCount(userId).block()!!

        // Then
        assertEquals(0, result.followerCount)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("팔로잉 수 증가")
    fun incrementFollowingCount_Success() {
        // Given
        val userId = UUID.randomUUID()
        val updatedProfile = testProfile.copy(followingCount = testProfile.followingCount + 1)

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(testProfile)
        every { userProfileRepository.update(any()) } returns Mono.just(updatedProfile)
        // When
        val result = userProfileService.incrementFollowingCount(userId).block()!!

        // Then
        assertEquals(testProfile.followingCount + 1, result.followingCount)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("팔로잉 수 감소")
    fun decrementFollowingCount_Success() {
        // Given
        val userId = UUID.randomUUID()
        val profileWithFollowing = testProfile.copy(followingCount = 3)
        val updatedProfile = profileWithFollowing.copy(followingCount = 2)

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(profileWithFollowing)
        every { userProfileRepository.update(any()) } returns Mono.just(updatedProfile)
        // When
        val result = userProfileService.decrementFollowingCount(userId).block()!!

        // Then
        assertEquals(2, result.followingCount)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }

    @Test
    @DisplayName("팔로잉 수 감소 - 0 이하로 내려가지 않음")
    fun decrementFollowingCount_DoesNotGoBelowZero() {
        // Given
        val userId = UUID.randomUUID()
        val profileWithZeroFollowing = testProfile.copy(followingCount = 0)

        every { userProfileRepository.findByUserId(userId) } returns Mono.just(profileWithZeroFollowing)
        every { userProfileRepository.update(any()) } returns Mono.just(profileWithZeroFollowing)
        // When
        val result = userProfileService.decrementFollowingCount(userId).block()!!

        // Then
        assertEquals(0, result.followingCount)
        verify(exactly = 1) { userProfileRepository.findByUserId(userId) }
        verify(exactly = 1) { userProfileRepository.update(any()) }
    }
}
