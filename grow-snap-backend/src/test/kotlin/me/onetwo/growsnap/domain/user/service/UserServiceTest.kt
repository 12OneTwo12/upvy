package me.onetwo.growsnap.domain.user.service

import java.util.UUID

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import me.onetwo.growsnap.domain.user.exception.UserNotFoundException
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserProfile
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.model.UserStatus
import me.onetwo.growsnap.domain.user.model.UserStatusHistory
import java.time.LocalDateTime
import me.onetwo.growsnap.domain.user.repository.FollowRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserStatusHistoryRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono

/**
 * UserService 단위 테스트
 */
@ExtendWith(MockKExtension::class)
@DisplayName("사용자 Service 테스트")
class UserServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var followRepository: FollowRepository
    private lateinit var userStatusHistoryRepository: UserStatusHistoryRepository
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        userProfileRepository = mockk()
        followRepository = mockk()
        userStatusHistoryRepository = mockk()
        userService = UserServiceImpl(userRepository, userProfileRepository, followRepository, userStatusHistoryRepository)
    }

    @Test
    @DisplayName("OAuth 사용자 조회 또는 생성 - 기존 활성 사용자 반환")
    fun findOrCreateOAuthUser_ExistingActiveUser_ReturnsUser() {
        // Given
        val email = "test@example.com"
        val provider = OAuthProvider.GOOGLE
        val providerId = "google-123"
        val name = "Test User"
        val profileImageUrl = "https://example.com/profile.jpg"

        val existingUser = User(
            id = UUID.randomUUID(),
            email = email,
            provider = provider,
            providerId = providerId,
            role = UserRole.USER,
            status = UserStatus.ACTIVE
        )

        every {
            userRepository.findByEmailIncludingDeleted(email)
        } returns Mono.just(existingUser)

        // When
        val result = userService.findOrCreateOAuthUser(email, provider, providerId, name, profileImageUrl).block()!!

        // Then
        assertEquals(existingUser, result)
        verify(exactly = 1) {
            userRepository.findByEmailIncludingDeleted(email)
        }
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { userProfileRepository.save(any()) }
        verify(exactly = 0) { userStatusHistoryRepository.save(any()) }
    }

    @Test
    @DisplayName("OAuth 사용자 조회 또는 생성 - 신규 사용자 및 프로필 생성")
    fun findOrCreateOAuthUser_NewUser_CreatesUserAndProfile() {
        // Given
        val email = "new@example.com"
        val provider = OAuthProvider.GOOGLE
        val providerId = "google-456"
        val name = "New User"
        val profileImageUrl = "https://example.com/new-profile.jpg"

        val userId = UUID.randomUUID()
        val newUser = User(
            id = userId,
            email = email,
            provider = provider,
            providerId = providerId,
            role = UserRole.USER,
            status = UserStatus.ACTIVE
        )

        val mockProfile = UserProfile(
            id = 1L,
            userId = userId,
            nickname = name,
            profileImageUrl = profileImageUrl,
            bio = null
        )

        val mockHistory = UserStatusHistory(
            id = 1L,
            userId = userId,
            previousStatus = null,
            newStatus = UserStatus.ACTIVE,
            reason = "Initial signup via $provider OAuth",
            changedBy = userId.toString()
        )

        every {
            userRepository.findByEmailIncludingDeleted(email)
        } returns Mono.empty()
        every { userRepository.save(any()) } returns Mono.just(newUser)
        every { userProfileRepository.existsByNickname(any()) } returns Mono.just(false)
        every { userProfileRepository.save(any()) } returns Mono.just(mockProfile)
        every { userStatusHistoryRepository.save(any()) } returns Mono.just(mockHistory)

        // When
        val result = userService.findOrCreateOAuthUser(email, provider, providerId, name, profileImageUrl).block()!!

        // Then
        assertEquals(newUser, result)
        verify(exactly = 1) {
            userRepository.findByEmailIncludingDeleted(email)
        }
        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { userProfileRepository.save(any()) }
        verify(exactly = 1) { userStatusHistoryRepository.save(any()) }
    }

    @Test
    @DisplayName("OAuth 사용자 조회 또는 생성 - 탈퇴한 사용자 재가입 (새 프로필 생성)")
    fun findOrCreateOAuthUser_DeletedUser_CreatesNewProfile() {
        // Given
        val email = "deleted_no_profile@example.com"
        val provider = OAuthProvider.GOOGLE
        val providerId = "google-790"
        val name = "Restored User No Profile"
        val profileImageUrl = "https://example.com/new-profile.jpg"

        val userId = UUID.randomUUID()
        val deletedUser = User(
            id = userId,
            email = email,
            provider = provider,
            providerId = providerId,
            role = UserRole.USER,
            status = UserStatus.DELETED,
            deletedAt = LocalDateTime.now().minusDays(1)
        )

        val restoredUser = deletedUser.copy(
            status = UserStatus.ACTIVE,
            deletedAt = null
        )

        val newProfile = UserProfile(
            id = 1L,
            userId = userId,
            nickname = name,
            profileImageUrl = profileImageUrl,
            bio = null
        )

        val mockHistory = UserStatusHistory(
            id = 1L,
            userId = userId,
            previousStatus = UserStatus.DELETED,
            newStatus = UserStatus.ACTIVE,
            reason = "User re-registration via OAuth",
            changedBy = userId.toString()
        )

        every {
            userRepository.findByEmailIncludingDeleted(email)
        } returns Mono.just(deletedUser)
        every {
            userRepository.findByIdIncludingDeleted(userId)
        } returns Mono.just(deletedUser)
        every {
            userRepository.updateStatus(userId, UserStatus.ACTIVE, userId)
        } returns Mono.just(restoredUser)
        every {
            userStatusHistoryRepository.save(any())
        } returns Mono.just(mockHistory)
        every {
            userProfileRepository.existsByNickname(any())
        } returns Mono.just(false)
        every {
            userProfileRepository.save(any())
        } returns Mono.just(newProfile)

        // When
        val result = userService.findOrCreateOAuthUser(email, provider, providerId, name, profileImageUrl).block()!!

        // Then
        assertEquals(UserStatus.ACTIVE, result.status)
        assertNull(result.deletedAt)
        verify(exactly = 1) {
            userRepository.findByEmailIncludingDeleted(email)
        }
        verify(exactly = 1) {
            userRepository.updateStatus(userId, UserStatus.ACTIVE, userId)
        }
        verify(exactly = 1) {
            userStatusHistoryRepository.save(any())
        }
        verify(exactly = 1) {
            userProfileRepository.save(any())  // 새로운 프로필 생성됨
        }
        verify(exactly = 0) {
            userRepository.save(any())
        }
    }

    @Test
    @DisplayName("OAuth 사용자 조회 또는 생성 - OAuth 제공자 변경 시 업데이트")
    fun findOrCreateOAuthUser_ProviderChanged_UpdatesProvider() {
        // Given
        val email = "user@example.com"
        val oldProvider = OAuthProvider.GOOGLE
        val newProvider = OAuthProvider.KAKAO
        val oldProviderId = "google-111"
        val newProviderId = "kakao-222"
        val name = "User"
        val profileImageUrl = "https://example.com/profile.jpg"

        val userId = UUID.randomUUID()
        val existingUser = User(
            id = userId,
            email = email,
            provider = oldProvider,
            providerId = oldProviderId,
            role = UserRole.USER,
            status = UserStatus.ACTIVE
        )

        val updatedUser = existingUser.copy(
            provider = newProvider,
            providerId = newProviderId
        )

        every {
            userRepository.findByEmailIncludingDeleted(email)
        } returns Mono.just(existingUser)
        every {
            userRepository.updateProvider(userId, newProvider, newProviderId)
        } returns Mono.just(updatedUser)

        // When
        val result = userService.findOrCreateOAuthUser(email, newProvider, newProviderId, name, profileImageUrl).block()!!

        // Then
        assertEquals(newProvider, result.provider)
        assertEquals(newProviderId, result.providerId)
        verify(exactly = 1) {
            userRepository.findByEmailIncludingDeleted(email)
        }
        verify(exactly = 1) {
            userRepository.updateProvider(userId, newProvider, newProviderId)
        }
        verify(exactly = 0) {
            userStatusHistoryRepository.save(any())
        }
    }

    @Test
    @DisplayName("사용자 ID로 조회 성공")
    fun getUserById_ExistingUser_ReturnsUser() {
        // Given
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            email = "test@example.com",
            provider = OAuthProvider.GOOGLE,
            providerId = "google-123",
            role = UserRole.USER
        )

        every { userRepository.findById(userId) } returns Mono.just(user)
        // When
        val result = userService.getUserById(userId).block()!!

        // Then
        assertEquals(user, result)
        verify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("사용자 ID로 조회 실패 - UserNotFoundException 발생")
    fun getUserById_NonExistingUser_ThrowsException() {
        // Given
        val userId = UUID.randomUUID()

        every { userRepository.findById(userId) } returns Mono.empty()
        // When & Then
        val exception = assertThrows<UserNotFoundException> {
            userService.getUserById(userId).block()!!
        }

        assertTrue(exception.message!!.contains("$userId"))
        verify(exactly = 1) { userRepository.findById(userId) }
    }

    @Test
    @DisplayName("이메일로 조회 성공")
    fun getUserByEmail_ExistingUser_ReturnsUser() {
        // Given
        val email = "test@example.com"
        val user = User(
            id = UUID.randomUUID(),
            email = email,
            provider = OAuthProvider.GOOGLE,
            providerId = "google-123",
            role = UserRole.USER
        )

        every { userRepository.findByEmail(email) } returns Mono.just(user)
        // When
        val result = userService.getUserByEmail(email).block()!!

        // Then
        assertEquals(user, result)
        verify(exactly = 1) { userRepository.findByEmail(email) }
    }

    @Test
    @DisplayName("이메일로 조회 실패 - UserNotFoundException 발생")
    fun getUserByEmail_NonExistingUser_ThrowsException() {
        // Given
        val email = "nonexistent@example.com"

        every { userRepository.findByEmail(email) } returns Mono.empty()
        // When & Then
        val exception = assertThrows<UserNotFoundException> {
            userService.getUserByEmail(email).block()!!
        }

        assertTrue(exception.message!!.contains(email))
        verify(exactly = 1) { userRepository.findByEmail(email) }
    }

    @Test
    @DisplayName("이메일 중복 확인 - 중복됨")
    fun isEmailDuplicated_ExistingEmail_ReturnsTrue() {
        // Given
        val email = "existing@example.com"
        val user = User(
            id = UUID.randomUUID(),
            email = email,
            provider = OAuthProvider.GOOGLE,
            providerId = "google-123",
            role = UserRole.USER
        )

        every { userRepository.findByEmail(email) } returns Mono.just(user)
        // When
        val result = userService.isEmailDuplicated(email).block()!!

        // Then
        assertTrue(result)
        verify(exactly = 1) { userRepository.findByEmail(email) }
    }

    @Test
    @DisplayName("이메일 중복 확인 - 중복되지 않음")
    fun isEmailDuplicated_NonExistingEmail_ReturnsFalse() {
        // Given
        val email = "new@example.com"

        every { userRepository.findByEmail(email) } returns Mono.empty()
        // When
        val result = userService.isEmailDuplicated(email).block()!!

        // Then
        assertFalse(result)
        verify(exactly = 1) { userRepository.findByEmail(email) }
    }
}
