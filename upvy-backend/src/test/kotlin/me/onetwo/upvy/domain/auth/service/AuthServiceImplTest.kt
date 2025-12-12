package me.onetwo.upvy.domain.auth.service

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import io.mockk.verify
import me.onetwo.upvy.domain.auth.exception.EmailAlreadyExistsException
import me.onetwo.upvy.domain.auth.exception.EmailNotVerifiedException
import me.onetwo.upvy.domain.auth.exception.InvalidCredentialsException
import me.onetwo.upvy.domain.auth.exception.InvalidVerificationTokenException
import me.onetwo.upvy.domain.auth.exception.TokenExpiredException
import me.onetwo.upvy.domain.auth.model.EmailVerificationToken
import me.onetwo.upvy.domain.auth.repository.EmailVerificationTokenRepository
import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserAuthenticationMethod
import me.onetwo.upvy.domain.user.model.UserRole
import me.onetwo.upvy.domain.user.repository.UserAuthenticationMethodRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.domain.user.service.UserService
import me.onetwo.upvy.infrastructure.redis.RefreshTokenRepository
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant
import java.util.UUID

/**
 * AuthServiceImpl 단위 테스트
 *
 * 계정 통합 아키텍처 (Account Linking)에서 이메일 가입/로그인/인증 비즈니스 로직을 검증합니다.
 * Repository는 MockK로 모킹하여 Service 계층만 테스트합니다.
 */
@ExtendWith(MockKExtension::class)
@DisplayName("인증 Service 단위 테스트")
class AuthServiceImplTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var userService: UserService
    private lateinit var userRepository: UserRepository
    private lateinit var authMethodRepository: UserAuthenticationMethodRepository
    private lateinit var emailVerificationTokenRepository: EmailVerificationTokenRepository
    private lateinit var emailService: EmailService
    private lateinit var passwordEncoder: BCryptPasswordEncoder
    private lateinit var authService: AuthServiceImpl

    private lateinit var testUserId: UUID
    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        jwtTokenProvider = mockk()
        refreshTokenRepository = mockk()
        userService = mockk()
        userRepository = mockk()
        authMethodRepository = mockk()
        emailVerificationTokenRepository = mockk()
        emailService = mockk()
        passwordEncoder = BCryptPasswordEncoder()

        authService = AuthServiceImpl(
            jwtTokenProvider,
            refreshTokenRepository,
            userService,
            userRepository,
            authMethodRepository,
            emailVerificationTokenRepository,
            emailService,
            passwordEncoder
        )

        testUserId = UUID.randomUUID()
        testUser = User(
            id = testUserId,
            email = "test@example.com",
            role = UserRole.USER
        )
    }

    @Nested
    @DisplayName("signup - 이메일 가입")
    inner class Signup {

        @Test
        @DisplayName("신규 사용자 이메일 가입 시, 사용자 생성 및 인증 이메일을 발송한다")
        fun signup_NewUser_CreatesUserAndSendsVerificationEmail() {
            // Given: 신규 사용자 (이메일 중복 없음)
            val email = "newuser@example.com"
            val password = "password123"

            val createdUser = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            val savedAuthMethod = UserAuthenticationMethod(
                id = 1L,
                userId = createdUser.id!!,
                provider = OAuthProvider.EMAIL,
                password = passwordEncoder.encode(password),
                emailVerified = false,
                isPrimary = true
            )

            val verificationToken = EmailVerificationToken.create(createdUser.id!!)

            every { userRepository.findByEmail(email) } returns Mono.empty()
            every { userRepository.save(any()) } returns Mono.just(createdUser)
            every { authMethodRepository.save(any()) } returns Mono.just(savedAuthMethod)
            every { emailVerificationTokenRepository.save(any()) } returns Mono.just(verificationToken)
            every { emailService.sendVerificationEmail(email, any()) } returns Mono.empty()

            // When: 이메일 가입
            val result = authService.signup(email, password, null)

            // Then: 성공 (Mono<Void> 완료)
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { userRepository.save(any()) }
            verify(exactly = 1) { authMethodRepository.save(any()) }
            verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
            verify(exactly = 1) { emailService.sendVerificationEmail(email, any()) }
        }

        @Test
        @DisplayName("OAuth 사용자가 이메일 인증 추가 시, 새 인증 수단을 추가하고 이메일을 발송한다")
        fun signup_ExistingOAuthUser_AddsEmailAuthMethod() {
            // Given: Google로 가입한 사용자 (EMAIL 인증 수단 없음)
            val email = "oauth@example.com"
            val password = "password123"

            val existingUser = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            val savedAuthMethod = UserAuthenticationMethod(
                id = 2L,
                userId = existingUser.id!!,
                provider = OAuthProvider.EMAIL,
                password = passwordEncoder.encode(password),
                emailVerified = false,
                isPrimary = false
            )

            val verificationToken = EmailVerificationToken.create(existingUser.id!!)

            every { userRepository.findByEmail(email) } returns Mono.just(existingUser)
            every { authMethodRepository.findByUserIdAndProvider(existingUser.id!!, OAuthProvider.EMAIL) } returns Mono.empty()
            every { authMethodRepository.save(any()) } returns Mono.just(savedAuthMethod)
            every { emailVerificationTokenRepository.save(any()) } returns Mono.just(verificationToken)
            every { emailService.sendVerificationEmail(email, any()) } returns Mono.empty()

            // When: 이메일 가입
            val result = authService.signup(email, password, null)

            // Then: 성공 (기존 사용자에 EMAIL 인증 수단 추가)
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 2) { userRepository.findByEmail(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(existingUser.id!!, OAuthProvider.EMAIL) }
            verify(exactly = 1) { authMethodRepository.save(any()) }
            verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
            verify(exactly = 1) { emailService.sendVerificationEmail(email, any()) }
        }

        @Test
        @DisplayName("이미 이메일로 가입한 경우, EmailAlreadyExistsException을 발생시킨다")
        fun signup_AlreadyExistsEmail_ThrowsException() {
            // Given: 이미 EMAIL 인증 수단이 있는 사용자
            val email = "existing@example.com"
            val password = "password123"

            val existingUser = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            val existingAuthMethod = UserAuthenticationMethod(
                id = 1L,
                userId = existingUser.id!!,
                provider = OAuthProvider.EMAIL,
                password = passwordEncoder.encode("old-password"),
                emailVerified = true,
                isPrimary = true
            )

            every { userRepository.findByEmail(email) } returns Mono.just(existingUser)
            every { authMethodRepository.findByUserIdAndProvider(existingUser.id!!, OAuthProvider.EMAIL) } returns Mono.just(existingAuthMethod)

            // When: 이메일 가입 시도
            val result = authService.signup(email, password, null)

            // Then: EmailAlreadyExistsException 발생
            StepVerifier.create(result)
                .expectError(EmailAlreadyExistsException::class.java)
                .verify()

            verify(exactly = 2) { userRepository.findByEmail(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(existingUser.id!!, OAuthProvider.EMAIL) }
            verify(exactly = 0) { authMethodRepository.save(any()) }
        }

        @Test
        @DisplayName("비밀번호가 BCrypt로 암호화되어 저장된다")
        fun signup_PasswordIsEncrypted() {
            // Given: 신규 사용자
            val email = "newuser@example.com"
            val rawPassword = "password123"

            val createdUser = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            val authMethodSlot = slot<UserAuthenticationMethod>()
            val savedAuthMethod = UserAuthenticationMethod(
                id = 1L,
                userId = createdUser.id!!,
                provider = OAuthProvider.EMAIL,
                password = passwordEncoder.encode(rawPassword),
                emailVerified = false,
                isPrimary = true
            )

            val verificationToken = EmailVerificationToken.create(createdUser.id!!)

            every { userRepository.findByEmail(email) } returns Mono.empty()
            every { userRepository.save(any()) } returns Mono.just(createdUser)
            every { authMethodRepository.save(capture(authMethodSlot)) } returns Mono.just(savedAuthMethod)
            every { emailVerificationTokenRepository.save(any()) } returns Mono.just(verificationToken)
            every { emailService.sendVerificationEmail(email, any()) } returns Mono.empty()

            // When: 이메일 가입
            authService.signup(email, rawPassword, null).block()

            // Then: 비밀번호가 BCrypt로 암호화됨
            val capturedAuthMethod = authMethodSlot.captured
            assertThat(capturedAuthMethod.password).isNotEqualTo(rawPassword)
            assertThat(passwordEncoder.matches(rawPassword, capturedAuthMethod.password)).isTrue()
        }
    }

    @Nested
    @DisplayName("signIn - 이메일 로그인")
    inner class SignIn {

        @Test
        @DisplayName("검증된 이메일로 로그인 시, JWT 토큰을 발급한다")
        fun signIn_WithVerifiedEmail_ReturnsJwtToken() {
            // Given: 검증된 이메일 인증 수단을 가진 사용자
            val email = "user@example.com"
            val rawPassword = "password123"
            val encodedPassword = passwordEncoder.encode(rawPassword)

            val user = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            val authMethod = UserAuthenticationMethod(
                id = 1L,
                userId = user.id!!,
                provider = OAuthProvider.EMAIL,
                password = encodedPassword,
                emailVerified = true,
                isPrimary = true
            )

            val accessToken = "access-token-string"
            val refreshToken = "refresh-token-string"

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) } returns Mono.just(authMethod)
            every { jwtTokenProvider.generateAccessToken(user.id!!, user.email, user.role) } returns accessToken
            every { jwtTokenProvider.generateRefreshToken(user.id!!) } returns refreshToken
            every { refreshTokenRepository.save(user.id!!, refreshToken) } just Runs

            // When: 이메일 로그인
            val result = authService.signIn(email, rawPassword)

            // Then: JWT 토큰 발급됨
            StepVerifier.create(result)
                .assertNext { token ->
                    assertThat(token.accessToken).isEqualTo(accessToken)
                    assertThat(token.refreshToken).isEqualTo(refreshToken)
                    assertThat(token.tokenType).isEqualTo("Bearer")
                }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) }
            verify(exactly = 1) { jwtTokenProvider.generateAccessToken(user.id!!, user.email, user.role) }
            verify(exactly = 1) { jwtTokenProvider.generateRefreshToken(user.id!!) }
            verify(exactly = 1) { refreshTokenRepository.save(user.id!!, refreshToken) }
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 로그인 시, InvalidCredentialsException을 발생시킨다")
        fun signIn_WithNonExistentEmail_ThrowsException() {
            // Given: 존재하지 않는 이메일
            val email = "nonexistent@example.com"
            val password = "password123"

            every { userRepository.findByEmail(email) } returns Mono.empty()

            // When: 로그인 시도
            val result = authService.signIn(email, password)

            // Then: InvalidCredentialsException 발생
            StepVerifier.create(result)
                .expectError(InvalidCredentialsException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 0) { authMethodRepository.findByUserIdAndProvider(any(), any()) }
        }

        @Test
        @DisplayName("이메일 미인증 상태로 로그인 시, EmailNotVerifiedException을 발생시킨다")
        fun signIn_WithUnverifiedEmail_ThrowsException() {
            // Given: 미인증 이메일 인증 수단
            val email = "unverified@example.com"
            val rawPassword = "password123"
            val encodedPassword = passwordEncoder.encode(rawPassword)

            val user = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            val authMethod = UserAuthenticationMethod(
                id = 1L,
                userId = user.id!!,
                provider = OAuthProvider.EMAIL,
                password = encodedPassword,
                emailVerified = false, // 미인증
                isPrimary = true
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) } returns Mono.just(authMethod)

            // When: 로그인 시도
            val result = authService.signIn(email, rawPassword)

            // Then: EmailNotVerifiedException 발생
            StepVerifier.create(result)
                .expectError(EmailNotVerifiedException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) }
            verify(exactly = 0) { jwtTokenProvider.generateAccessToken(any(), any(), any()) }
        }

        @Test
        @DisplayName("잘못된 비밀번호로 로그인 시, InvalidCredentialsException을 발생시킨다")
        fun signIn_WithWrongPassword_ThrowsException() {
            // Given: 검증된 사용자
            val email = "user@example.com"
            val correctPassword = "correct-password"
            val wrongPassword = "wrong-password"
            val encodedPassword = passwordEncoder.encode(correctPassword)

            val user = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            val authMethod = UserAuthenticationMethod(
                id = 1L,
                userId = user.id!!,
                provider = OAuthProvider.EMAIL,
                password = encodedPassword,
                emailVerified = true,
                isPrimary = true
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) } returns Mono.just(authMethod)

            // When: 잘못된 비밀번호로 로그인 시도
            val result = authService.signIn(email, wrongPassword)

            // Then: InvalidCredentialsException 발생
            StepVerifier.create(result)
                .expectError(InvalidCredentialsException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) }
            verify(exactly = 0) { jwtTokenProvider.generateAccessToken(any(), any(), any()) }
        }

        @Test
        @DisplayName("OAuth만 있고 EMAIL 인증 수단이 없는 경우, InvalidCredentialsException을 발생시킨다")
        fun signIn_WithOnlyOAuth_ThrowsException() {
            // Given: OAuth로만 가입한 사용자 (EMAIL 인증 수단 없음)
            val email = "oauth-only@example.com"
            val password = "password123"

            val user = User(
                id = UUID.randomUUID(),
                email = email,
                role = UserRole.USER
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) } returns Mono.empty()

            // When: 이메일 로그인 시도
            val result = authService.signIn(email, password)

            // Then: InvalidCredentialsException 발생
            StepVerifier.create(result)
                .expectError(InvalidCredentialsException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL) }
        }
    }

    @Nested
    @DisplayName("verifyEmail - 이메일 인증")
    inner class VerifyEmail {

        @Test
        @DisplayName("유효한 토큰으로 인증 시, 이메일 검증 및 JWT 토큰을 발급한다")
        fun verifyEmail_WithValidToken_VerifiesEmailAndReturnsJwt() {
            // Given: 유효한 인증 토큰
            val userId = UUID.randomUUID()
            val tokenString = UUID.randomUUID().toString()

            val verificationToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = tokenString,
                expiresAt = Instant.now().plusSeconds(86400), // 24시간 후
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val user = User(
                id = userId,
                email = "user@example.com",
                role = UserRole.USER
            )

            val authMethod = UserAuthenticationMethod(
                id = 1L,
                userId = userId,
                provider = OAuthProvider.EMAIL,
                password = passwordEncoder.encode("password"),
                emailVerified = false,
                isPrimary = true
            )

            val accessToken = "access-token-string"
            val refreshToken = "refresh-token-string"

            every { emailVerificationTokenRepository.findByToken(tokenString) } returns Mono.just(verificationToken)
            every { userRepository.findById(userId) } returns Mono.just(user)
            every { authMethodRepository.findByUserIdAndProvider(userId, OAuthProvider.EMAIL) } returns Mono.just(authMethod)
            every { authMethodRepository.updateEmailVerified(authMethod.id!!, true) } returns Mono.just(1)
            every { emailVerificationTokenRepository.softDeleteAllByUserId(userId) } returns Mono.empty()
            every { jwtTokenProvider.generateAccessToken(userId, user.email, user.role) } returns accessToken
            every { jwtTokenProvider.generateRefreshToken(userId) } returns refreshToken
            every { refreshTokenRepository.save(userId, refreshToken) } just Runs

            // When: 이메일 인증
            val result = authService.verifyEmail(tokenString)

            // Then: JWT 토큰 발급됨
            StepVerifier.create(result)
                .assertNext { token ->
                    assertThat(token.accessToken).isEqualTo(accessToken)
                    assertThat(token.refreshToken).isEqualTo(refreshToken)
                }
                .verifyComplete()

            verify(exactly = 1) { emailVerificationTokenRepository.findByToken(tokenString) }
            verify(exactly = 1) { authMethodRepository.updateEmailVerified(authMethod.id!!, true) }
            verify(exactly = 1) { emailVerificationTokenRepository.softDeleteAllByUserId(userId) }
            verify(exactly = 1) { jwtTokenProvider.generateAccessToken(userId, user.email, user.role) }
            verify(exactly = 1) { jwtTokenProvider.generateRefreshToken(userId) }
        }

        @Test
        @DisplayName("존재하지 않는 토큰으로 인증 시, InvalidVerificationTokenException을 발생시킨다")
        fun verifyEmail_WithNonExistentToken_ThrowsException() {
            // Given: 존재하지 않는 토큰
            val tokenString = "non-existent-token"

            every { emailVerificationTokenRepository.findByToken(tokenString) } returns Mono.empty()

            // When: 인증 시도
            val result = authService.verifyEmail(tokenString)

            // Then: InvalidVerificationTokenException 발생
            StepVerifier.create(result)
                .expectError(InvalidVerificationTokenException::class.java)
                .verify()

            verify(exactly = 1) { emailVerificationTokenRepository.findByToken(tokenString) }
            verify(exactly = 0) { authMethodRepository.updateEmailVerified(any(), any()) }
        }

        @Test
        @DisplayName("만료된 토큰으로 인증 시, TokenExpiredException을 발생시킨다")
        fun verifyEmail_WithExpiredToken_ThrowsException() {
            // Given: 만료된 인증 토큰
            val userId = UUID.randomUUID()
            val tokenString = UUID.randomUUID().toString()

            val expiredToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = tokenString,
                expiresAt = Instant.now().minusSeconds(3600), // 1시간 전 만료
                createdAt = Instant.now().minusSeconds(7200),
                updatedAt = Instant.now().minusSeconds(7200)
            )

            every { emailVerificationTokenRepository.findByToken(tokenString) } returns Mono.just(expiredToken)

            // When: 인증 시도
            val result = authService.verifyEmail(tokenString)

            // Then: TokenExpiredException 발생
            StepVerifier.create(result)
                .expectError(TokenExpiredException::class.java)
                .verify()

            verify(exactly = 1) { emailVerificationTokenRepository.findByToken(tokenString) }
            verify(exactly = 0) { authMethodRepository.updateEmailVerified(any(), any()) }
        }

        @Test
        @DisplayName("이미 사용된 토큰 (deletedAt != null)으로 인증 시, InvalidVerificationTokenException을 발생시킨다")
        fun verifyEmail_WithDeletedToken_ThrowsException() {
            // Given: 삭제된 (사용된) 토큰
            val userId = UUID.randomUUID()
            val tokenString = UUID.randomUUID().toString()

            val deletedToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = tokenString,
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                deletedAt = Instant.now() // 이미 사용됨
            )

            every { emailVerificationTokenRepository.findByToken(tokenString) } returns Mono.just(deletedToken)

            // When: 인증 시도
            val result = authService.verifyEmail(tokenString)

            // Then: InvalidVerificationTokenException 발생
            StepVerifier.create(result)
                .expectError(InvalidVerificationTokenException::class.java)
                .verify()

            verify(exactly = 1) { emailVerificationTokenRepository.findByToken(tokenString) }
            verify(exactly = 0) { authMethodRepository.updateEmailVerified(any(), any()) }
        }

        @Test
        @DisplayName("이메일 인증 완료 후 기존 토큰들이 모두 무효화된다")
        fun verifyEmail_DeletesAllPreviousTokens() {
            // Given: 유효한 인증 토큰
            val userId = UUID.randomUUID()
            val tokenString = UUID.randomUUID().toString()

            val verificationToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = tokenString,
                expiresAt = Instant.now().plusSeconds(86400),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )

            val user = User(
                id = userId,
                email = "user@example.com",
                role = UserRole.USER
            )

            val authMethod = UserAuthenticationMethod(
                id = 1L,
                userId = userId,
                provider = OAuthProvider.EMAIL,
                password = passwordEncoder.encode("password"),
                emailVerified = false,
                isPrimary = true
            )

            val accessToken = "access-token-string"
            val refreshToken = "refresh-token-string"

            every { emailVerificationTokenRepository.findByToken(tokenString) } returns Mono.just(verificationToken)
            every { userRepository.findById(userId) } returns Mono.just(user)
            every { authMethodRepository.findByUserIdAndProvider(userId, OAuthProvider.EMAIL) } returns Mono.just(authMethod)
            every { authMethodRepository.updateEmailVerified(authMethod.id!!, true) } returns Mono.just(1)
            every { emailVerificationTokenRepository.softDeleteAllByUserId(userId) } returns Mono.empty()
            every { jwtTokenProvider.generateAccessToken(userId, user.email, user.role) } returns accessToken
            every { jwtTokenProvider.generateRefreshToken(userId) } returns refreshToken
            every { refreshTokenRepository.save(userId, refreshToken) } just Runs

            // When: 이메일 인증
            authService.verifyEmail(tokenString).block()

            // Then: 사용자의 모든 토큰 무효화됨
            verify(exactly = 1) { emailVerificationTokenRepository.softDeleteAllByUserId(userId) }
        }
    }
}
