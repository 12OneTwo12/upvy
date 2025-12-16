package me.onetwo.upvy.domain.user.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import me.onetwo.upvy.domain.user.event.UserCreatedEvent
import me.onetwo.upvy.domain.user.exception.UserNotFoundException
import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserAuthenticationMethod
import me.onetwo.upvy.domain.user.model.UserProfile
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.model.UserStatus
import me.onetwo.upvy.domain.user.model.UserStatusHistory
import me.onetwo.upvy.domain.user.repository.FollowRepository
import me.onetwo.upvy.domain.user.repository.UserAuthenticationMethodRepository
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.domain.user.repository.UserStatusHistoryRepository
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.event.ReactiveEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * UserService 단위 테스트
 *
 * 계정 통합 아키텍처 (Account Linking)에서 OAuth 로그인 및 사용자 관리 비즈니스 로직을 검증합니다.
 * Repository는 MockK로 모킹하여 Service 계층만 테스트합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("사용자 Service 단위 테스트")
class UserServiceTest : BaseReactiveTest {

    private lateinit var userRepository: UserRepository
    private lateinit var authMethodRepository: UserAuthenticationMethodRepository
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var followRepository: FollowRepository
    private lateinit var userStatusHistoryRepository: UserStatusHistoryRepository
    private lateinit var eventPublisher: ReactiveEventPublisher
    private lateinit var userService: UserService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        authMethodRepository = mockk()
        userProfileRepository = mockk()
        followRepository = mockk()
        userStatusHistoryRepository = mockk()
        eventPublisher = mockk()
        userService = UserServiceImpl(
            userRepository,
            authMethodRepository,
            userProfileRepository,
            followRepository,
            userStatusHistoryRepository,
            eventPublisher
        )
    }

    @Nested
    @DisplayName("findOrCreateOAuthUser - OAuth 로그인")
    inner class FindOrCreateOAuthUser {

        @Test
        @DisplayName("완전히 새로운 사용자 OAuth 로그인 시, User + 인증 수단을 생성한다")
        fun findOrCreateOAuthUser_NewUser_CreatesUserAndAuthMethodAndProfile() {
            // Given: 완전히 새로운 사용자
            val email = "newuser@example.com"
            val provider = OAuthProvider.GOOGLE
            val providerId = "google-123"
            val name = "New User"
            val profileImageUrl = "https://example.com/profile.jpg"

            val userId = UUID.randomUUID()
            val createdUser = User(
                id = userId,
                email = email,
                role = UserRole.USER,
                status = UserStatus.ACTIVE
            )

            val authMethod = UserAuthenticationMethod(
                id = 1L,
                userId = userId,
                provider = provider,
                providerId = providerId,
                emailVerified = true,
                isPrimary = true
            )

            val statusHistory = UserStatusHistory(
                id = 1L,
                userId = userId,
                previousStatus = null,
                newStatus = UserStatus.ACTIVE,
                reason = "Initial signup via $provider OAuth",
                changedBy = userId.toString()
            )

            every { userRepository.findByEmailIncludingDeleted(email) } returns Mono.empty()
            every { userRepository.save(any()) } returns Mono.just(createdUser)
            every { authMethodRepository.save(any()) } returns Mono.just(authMethod)
            every { userStatusHistoryRepository.save(any()) } returns Mono.just(statusHistory)
            every { eventPublisher.publish(any<UserCreatedEvent>()) } just Runs

            // When: OAuth 로그인
            val result = userService.findOrCreateOAuthUser(email, provider, providerId, name, profileImageUrl)

            // Then: User + 인증 수단 생성됨 (프로필은 사용자가 직접 생성)
            StepVerifier.create(result)
                .assertNext { user ->
                    assertThat(user.id).isEqualTo(userId)
                    assertThat(user.email).isEqualTo(email)
                    assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
                }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmailIncludingDeleted(email) }
            verify(exactly = 1) { userRepository.save(any()) }
            verify(exactly = 1) { authMethodRepository.save(any()) }
            verify(exactly = 1) { userStatusHistoryRepository.save(any()) }
            verify(exactly = 1) { eventPublisher.publish(any<UserCreatedEvent>()) }
        }

        @Test
        @DisplayName("같은 이메일 + 같은 provider로 로그인 시, 기존 사용자를 반환한다")
        fun findOrCreateOAuthUser_SameEmailAndProvider_ReturnsExistingUser() {
            // Given: Google로 이미 가입한 사용자
            val email = "existing@example.com"
            val provider = OAuthProvider.GOOGLE
            val providerId = "google-456"
            val name = "Existing User"
            val profileImageUrl = "https://example.com/profile.jpg"

            val userId = UUID.randomUUID()
            val existingUser = User(
                id = userId,
                email = email,
                role = UserRole.USER,
                status = UserStatus.ACTIVE
            )

            val existingAuthMethod = UserAuthenticationMethod(
                id = 1L,
                userId = userId,
                provider = provider,
                providerId = providerId,
                emailVerified = true,
                isPrimary = true
            )

            every { userRepository.findByEmailIncludingDeleted(email) } returns Mono.just(existingUser)
            every { authMethodRepository.findByUserIdAndProvider(userId, provider) } returns Mono.just(existingAuthMethod)

            // When: 같은 provider로 로그인
            val result = userService.findOrCreateOAuthUser(email, provider, providerId, name, profileImageUrl)

            // Then: 기존 사용자 반환 (아무것도 생성 안함)
            StepVerifier.create(result)
                .assertNext { user ->
                    assertThat(user.id).isEqualTo(userId)
                    assertThat(user.email).isEqualTo(email)
                }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmailIncludingDeleted(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(userId, provider) }
            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { authMethodRepository.save(any()) }
            verify(exactly = 0) { userProfileRepository.save(any()) }
        }

        @Test
        @DisplayName("같은 이메일 + 다른 provider로 로그인 시, 기존 사용자에 새 인증 수단을 추가한다 (계정 자동 연결)")
        fun findOrCreateOAuthUser_SameEmailDifferentProvider_LinksNewAuthMethod() {
            // Given: Google로 가입한 사용자가 Naver로 로그인
            val email = "user@example.com"
            val existingProvider = OAuthProvider.GOOGLE
            val newProvider = OAuthProvider.NAVER
            val newProviderId = "naver-789"
            val name = "User"
            val profileImageUrl = "https://example.com/profile.jpg"

            val userId = UUID.randomUUID()
            val existingUser = User(
                id = userId,
                email = email,
                role = UserRole.USER,
                status = UserStatus.ACTIVE
            )

            val newAuthMethod = UserAuthenticationMethod(
                id = 2L,
                userId = userId,
                provider = newProvider,
                providerId = newProviderId,
                emailVerified = true,
                isPrimary = false  // 기존 인증 수단이 있으므로 primary 아님
            )

            every { userRepository.findByEmailIncludingDeleted(email) } returns Mono.just(existingUser)
            every { authMethodRepository.findByUserIdAndProvider(userId, newProvider) } returns Mono.empty()
            every { authMethodRepository.save(any()) } returns Mono.just(newAuthMethod)

            // When: 다른 provider로 로그인 (계정 자동 연결)
            val result = userService.findOrCreateOAuthUser(email, newProvider, newProviderId, name, profileImageUrl)

            // Then: 새 인증 수단 추가됨
            StepVerifier.create(result)
                .assertNext { user ->
                    assertThat(user.id).isEqualTo(userId)
                    assertThat(user.email).isEqualTo(email)
                }
                .verifyComplete()

            val authMethodSlot = slot<UserAuthenticationMethod>()
            verify(exactly = 1) { userRepository.findByEmailIncludingDeleted(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(userId, newProvider) }
            verify(exactly = 1) { authMethodRepository.save(capture(authMethodSlot)) }

            val capturedAuthMethod = authMethodSlot.captured
            assertThat(capturedAuthMethod.userId).isEqualTo(userId)
            assertThat(capturedAuthMethod.provider).isEqualTo(newProvider)
            assertThat(capturedAuthMethod.providerId).isEqualTo(newProviderId)
            assertThat(capturedAuthMethod.isPrimary).isFalse()  // 기존 인증 수단이 있으므로 false
            assertThat(capturedAuthMethod.emailVerified).isTrue()  // OAuth는 자동 검증

            verify(exactly = 0) { userRepository.save(any()) }
            verify(exactly = 0) { userProfileRepository.save(any()) }
        }

        @Test
        @DisplayName("탈퇴한 계정 재가입 시, 상태를 ACTIVE로 복원한다")
        fun findOrCreateOAuthUser_DeletedUser_RestoresAndCreatesNewProfile() {
            // Given: 탈퇴한 사용자
            val email = "deleted@example.com"
            val provider = OAuthProvider.GOOGLE
            val providerId = "google-999"
            val name = "Restored User"
            val profileImageUrl = "https://example.com/new-profile.jpg"

            val userId = UUID.randomUUID()
            val deletedUser = User(
                id = userId,
                email = email,
                role = UserRole.USER,
                status = UserStatus.DELETED,
                deletedAt = Instant.now()
            )

            val restoredUser = User(
                id = userId,
                email = email,
                role = UserRole.USER,
                status = UserStatus.ACTIVE,
                deletedAt = null
            )

            val authMethod = UserAuthenticationMethod(
                id = 3L,
                userId = userId,
                provider = provider,
                providerId = providerId,
                emailVerified = true,
                isPrimary = true  // 재가입 시 첫 인증 수단은 primary
            )

            val statusHistory = UserStatusHistory(
                id = 2L,
                userId = userId,
                previousStatus = UserStatus.DELETED,
                newStatus = UserStatus.ACTIVE,
                reason = "User re-registration via $provider OAuth",
                changedBy = userId.toString()
            )

            every { userRepository.findByEmailIncludingDeleted(email) } returns Mono.just(deletedUser)
            every { userRepository.findByIdIncludingDeleted(userId) } returns Mono.just(deletedUser)
            every { userRepository.updateStatus(userId, UserStatus.ACTIVE, userId) } returns Mono.just(restoredUser)
            every { authMethodRepository.save(any()) } returns Mono.just(authMethod)
            every { userStatusHistoryRepository.save(any()) } returns Mono.just(statusHistory)

            // When: 탈퇴한 계정 재가입
            val result = userService.findOrCreateOAuthUser(email, provider, providerId, name, profileImageUrl)

            // Then: 상태 복원 (프로필은 사용자가 직접 생성)
            StepVerifier.create(result)
                .assertNext { user ->
                    assertThat(user.id).isEqualTo(userId)
                    assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
                    assertThat(user.deletedAt).isNull()
                }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmailIncludingDeleted(email) }
            verify(exactly = 1) { userRepository.updateStatus(userId, UserStatus.ACTIVE, userId) }
            verify(exactly = 1) { authMethodRepository.save(any()) }
            verify(exactly = 1) { userStatusHistoryRepository.save(any()) }
            verify(exactly = 0) { userRepository.save(any()) }  // User 생성 안함
        }

    }

    @Nested
    @DisplayName("getUserById / getUserByEmail")
    inner class GetUser {

        @Test
        @DisplayName("사용자 ID로 조회 성공")
        fun getUserById_ExistingUser_ReturnsUser() {
            // Given
            val userId = UUID.randomUUID()
            val user = User(
                id = userId,
                email = "test@example.com",
                role = UserRole.USER
            )

            every { userRepository.findById(userId) } returns Mono.just(user)

            // When
            val result = userService.getUserById(userId)

            // Then
            StepVerifier.create(result)
                .assertNext { assertThat(it).isEqualTo(user) }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findById(userId) }
        }

        @Test
        @DisplayName("사용자 ID로 조회 실패 - UserNotFoundException 발생")
        fun getUserById_NonExistingUser_ThrowsException() {
            // Given
            val userId = UUID.randomUUID()

            every { userRepository.findById(userId) } returns Mono.empty()

            // When
            val result = userService.getUserById(userId)

            // Then
            StepVerifier.create(result)
                .expectError(UserNotFoundException::class.java)
                .verify()

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
                role = UserRole.USER
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)

            // When
            val result = userService.getUserByEmail(email)

            // Then
            StepVerifier.create(result)
                .assertNext { assertThat(it).isEqualTo(user) }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmail(email) }
        }

        @Test
        @DisplayName("이메일로 조회 실패 - UserNotFoundException 발생")
        fun getUserByEmail_NonExistingUser_ThrowsException() {
            // Given
            val email = "nonexistent@example.com"

            every { userRepository.findByEmail(email) } returns Mono.empty()

            // When
            val result = userService.getUserByEmail(email)

            // Then
            StepVerifier.create(result)
                .expectError(UserNotFoundException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
        }
    }

    @Nested
    @DisplayName("isEmailDuplicated - 이메일 중복 확인")
    inner class IsEmailDuplicated {

        @Test
        @DisplayName("이메일 중복 확인 - 중복됨")
        fun isEmailDuplicated_ExistingEmail_ReturnsTrue() {
            // Given
            val email = "existing@example.com"
            val user = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)

            // When
            val result = userService.isEmailDuplicated(email)

            // Then
            StepVerifier.create(result)
                .assertNext { assertThat(it).isTrue() }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmail(email) }
        }

        @Test
        @DisplayName("이메일 중복 확인 - 중복되지 않음")
        fun isEmailDuplicated_NonExistingEmail_ReturnsFalse() {
            // Given
            val email = "new@example.com"

            every { userRepository.findByEmail(email) } returns Mono.empty()

            // When
            val result = userService.isEmailDuplicated(email)

            // Then
            StepVerifier.create(result)
                .assertNext { assertThat(it).isFalse() }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmail(email) }
        }
    }

    @Nested
    @DisplayName("withdrawUser - 회원 탈퇴")
    inner class WithdrawUser {

        @Test
        @DisplayName("회원 탈퇴 시, 사용자 상태를 DELETED로 변경하고 관련 데이터를 soft delete한다")
        fun withdrawUser_ActiveUser_SoftDeletesUserAndRelatedData() {
            // Given: 활성 사용자
            val userId = UUID.randomUUID()
            val user = User(
                id = userId,
                email = "user@example.com",
                role = UserRole.USER,
                status = UserStatus.ACTIVE
            )

            val deletedUser = user.copy(status = UserStatus.DELETED)

            val authMethod1 = UserAuthenticationMethod(
                id = 1L,
                userId = userId,
                provider = OAuthProvider.GOOGLE,
                providerId = "google-123",
                emailVerified = true,
                isPrimary = true
            )

            val authMethod2 = UserAuthenticationMethod(
                id = 2L,
                userId = userId,
                provider = OAuthProvider.NAVER,
                providerId = "naver-456",
                emailVerified = true,
                isPrimary = false
            )

            val statusHistory = UserStatusHistory(
                id = 1L,
                userId = userId,
                previousStatus = UserStatus.ACTIVE,
                newStatus = UserStatus.DELETED,
                reason = "User requested account deletion",
                changedBy = userId.toString()
            )

            every { userRepository.findById(userId) } returns Mono.just(user)
            every { userRepository.findByIdIncludingDeleted(userId) } returns Mono.just(user)
            every { userRepository.updateStatus(userId, UserStatus.DELETED, userId) } returns Mono.just(deletedUser)
            every { userStatusHistoryRepository.save(any()) } returns Mono.just(statusHistory)
            every { authMethodRepository.findAllByUserId(userId) } returns Flux.just(authMethod1, authMethod2)
            every { authMethodRepository.softDelete(1L) } returns Mono.empty()
            every { authMethodRepository.softDelete(2L) } returns Mono.empty()
            every { userProfileRepository.softDelete(userId, userId) } returns Mono.empty()
            every { followRepository.softDeleteAllByUserId(userId, userId) } returns Mono.empty()

            // When: 회원 탈퇴
            val result = userService.withdrawUser(userId)

            // Then: 상태 변경 + soft delete
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { userRepository.findById(userId) }
            verify(exactly = 1) { userRepository.updateStatus(userId, UserStatus.DELETED, userId) }
            verify(exactly = 1) { userStatusHistoryRepository.save(any()) }
            verify(exactly = 1) { authMethodRepository.findAllByUserId(userId) }
            verify(exactly = 1) { authMethodRepository.softDelete(1L) }
            verify(exactly = 1) { authMethodRepository.softDelete(2L) }
            verify(exactly = 1) { userProfileRepository.softDelete(userId, userId) }
            verify(exactly = 1) { followRepository.softDeleteAllByUserId(userId, userId) }
        }

        @Test
        @DisplayName("존재하지 않는 사용자 탈퇴 시, UserNotFoundException 발생")
        fun withdrawUser_NonExistingUser_ThrowsException() {
            // Given: 존재하지 않는 사용자
            val userId = UUID.randomUUID()

            every { userRepository.findById(userId) } returns Mono.empty()

            // When: 회원 탈퇴 시도
            val result = userService.withdrawUser(userId)

            // Then: UserNotFoundException 발생
            StepVerifier.create(result)
                .expectError(UserNotFoundException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findById(userId) }
            verify(exactly = 0) { userRepository.updateStatus(any(), any(), any()) }
        }
    }
}
