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
import me.onetwo.upvy.domain.auth.exception.TooManyRequestsException
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
    private lateinit var emailVerificationService: EmailVerificationService
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
        emailVerificationService = mockk()
        passwordEncoder = BCryptPasswordEncoder()

        authService = AuthServiceImpl(
            jwtTokenProvider,
            refreshTokenRepository,
            userService,
            userRepository,
            authMethodRepository,
            emailVerificationTokenRepository,
            emailVerificationService,
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
            every { emailVerificationService.sendVerificationEmail(email, any(), any()) } returns Mono.empty()

            // When: 이메일 가입
            val result = authService.signup(email, password, null, "en")

            // Then: 성공 (Mono<Void> 완료)
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { userRepository.save(any()) }
            verify(exactly = 1) { authMethodRepository.save(any()) }
            verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
            verify(exactly = 1) { emailVerificationService.sendVerificationEmail(email, any(), any()) }
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
            every { emailVerificationService.sendVerificationEmail(email, any(), any()) } returns Mono.empty()

            // When: 이메일 가입
            val result = authService.signup(email, password, null, "en")

            // Then: 성공 (기존 사용자에 EMAIL 인증 수단 추가)
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 2) { userRepository.findByEmail(email) }
            verify(exactly = 1) { authMethodRepository.findByUserIdAndProvider(existingUser.id!!, OAuthProvider.EMAIL) }
            verify(exactly = 1) { authMethodRepository.save(any()) }
            verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
            verify(exactly = 1) { emailVerificationService.sendVerificationEmail(email, any(), any()) }
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
            val result = authService.signup(email, password, null, "en")

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
            every { emailVerificationService.sendVerificationEmail(email, any(), any()) } returns Mono.empty()

            // When: 이메일 가입
            authService.signup(email, rawPassword, null, "en").block()

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
            every { jwtTokenProvider.getUserIdFromToken(accessToken) } returns user.id!!
            every { jwtTokenProvider.getEmailFromToken(accessToken) } returns user.email
            every { refreshTokenRepository.save(user.id!!, refreshToken) } just Runs

            // When: 이메일 로그인
            val result = authService.signIn(email, rawPassword)

            // Then: EmailVerifyResponse 발급됨
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.accessToken).isEqualTo(accessToken)
                    assertThat(response.refreshToken).isEqualTo(refreshToken)
                    assertThat(response.userId).isEqualTo(user.id)
                    assertThat(response.email).isEqualTo(email)
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
    @DisplayName("verifyEmailCode - 이메일 코드 검증")
    inner class VerifyEmailCode {

        @Test
        @DisplayName("유효한 코드로 인증 시, 이메일 검증 및 JWT 토큰을 발급한다")
        fun verifyEmailCode_WithValidCode_VerifiesEmailAndReturnsJwt() {
            // Given: 유효한 인증 코드
            val userId = UUID.randomUUID()
            val email = "user@example.com"
            val code = "123456"

            val user = User(
                id = userId,
                email = email,
                role = UserRole.USER
            )

            val verificationToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = code,
                expiresAt = Instant.now().plusSeconds(300), // 5분 후
                createdAt = Instant.now(),
                updatedAt = Instant.now()
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

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { emailVerificationTokenRepository.findByUserIdAndToken(userId, code) } returns Mono.just(verificationToken)
            every { authMethodRepository.findByUserIdAndProvider(userId, OAuthProvider.EMAIL) } returns Mono.just(authMethod)
            every { authMethodRepository.updateEmailVerified(authMethod.id!!, true) } returns Mono.just(1)
            every { emailVerificationTokenRepository.softDeleteAllByUserId(userId) } returns Mono.empty()
            every { jwtTokenProvider.generateAccessToken(userId, user.email, user.role) } returns accessToken
            every { jwtTokenProvider.generateRefreshToken(userId) } returns refreshToken
            every { jwtTokenProvider.getUserIdFromToken(accessToken) } returns userId
            every { jwtTokenProvider.getEmailFromToken(accessToken) } returns user.email
            every { refreshTokenRepository.save(userId, refreshToken) } just Runs

            // When: 이메일 코드 인증
            val result = authService.verifyEmailCode(email, code)

            // Then: EmailVerifyResponse 발급됨
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.accessToken).isEqualTo(accessToken)
                    assertThat(response.refreshToken).isEqualTo(refreshToken)
                    assertThat(response.userId).isEqualTo(userId)
                    assertThat(response.email).isEqualTo(email)
                }
                .verifyComplete()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { emailVerificationTokenRepository.findByUserIdAndToken(userId, code) }
            verify(exactly = 1) { authMethodRepository.updateEmailVerified(authMethod.id!!, true) }
            verify(exactly = 1) { emailVerificationTokenRepository.softDeleteAllByUserId(userId) }
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 인증 시, InvalidVerificationTokenException을 발생시킨다")
        fun verifyEmailCode_WithNonExistentEmail_ThrowsException() {
            // Given: 존재하지 않는 이메일
            val email = "nonexistent@example.com"
            val code = "123456"

            every { userRepository.findByEmail(email) } returns Mono.empty()

            // When: 인증 시도
            val result = authService.verifyEmailCode(email, code)

            // Then: InvalidVerificationTokenException 발생
            StepVerifier.create(result)
                .expectError(InvalidVerificationTokenException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 0) { emailVerificationTokenRepository.findByUserIdAndToken(any(), any()) }
        }

        @Test
        @DisplayName("잘못된 코드로 인증 시, InvalidVerificationTokenException을 발생시킨다")
        fun verifyEmailCode_WithInvalidCode_ThrowsException() {
            // Given: 잘못된 코드
            val userId = UUID.randomUUID()
            val email = "user@example.com"
            val wrongCode = "999999"

            val user = User(
                id = userId,
                email = email,
                role = UserRole.USER
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { emailVerificationTokenRepository.findByUserIdAndToken(userId, wrongCode) } returns Mono.empty()

            // When: 인증 시도
            val result = authService.verifyEmailCode(email, wrongCode)

            // Then: InvalidVerificationTokenException 발생
            StepVerifier.create(result)
                .expectError(InvalidVerificationTokenException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { emailVerificationTokenRepository.findByUserIdAndToken(userId, wrongCode) }
            verify(exactly = 0) { authMethodRepository.updateEmailVerified(any(), any()) }
        }

        @Test
        @DisplayName("만료된 코드로 인증 시, TokenExpiredException을 발생시킨다")
        fun verifyEmailCode_WithExpiredCode_ThrowsException() {
            // Given: 만료된 인증 코드
            val userId = UUID.randomUUID()
            val email = "user@example.com"
            val code = "123456"

            val user = User(
                id = userId,
                email = email,
                role = UserRole.USER
            )

            val expiredToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = code,
                expiresAt = Instant.now().minusSeconds(60), // 1분 전 만료
                createdAt = Instant.now().minusSeconds(360),
                updatedAt = Instant.now().minusSeconds(360)
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { emailVerificationTokenRepository.findByUserIdAndToken(userId, code) } returns Mono.just(expiredToken)

            // When: 인증 시도
            val result = authService.verifyEmailCode(email, code)

            // Then: TokenExpiredException 발생
            StepVerifier.create(result)
                .expectError(TokenExpiredException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { emailVerificationTokenRepository.findByUserIdAndToken(userId, code) }
            verify(exactly = 0) { authMethodRepository.updateEmailVerified(any(), any()) }
        }
    }

    @Nested
    @DisplayName("resendVerificationCode - 인증 코드 재전송")
    inner class ResendVerificationCode {

        @Test
        @DisplayName("1분 이후 재전송 시, 성공한다")
        fun resendVerificationCode_AfterOneMinute_Succeeds() {
            // Given: 1분 이상 지난 사용자
            val userId = UUID.randomUUID()
            val email = "user@example.com"

            val user = User(
                id = userId,
                email = email,
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

            val oldToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = "111111",
                expiresAt = Instant.now().plusSeconds(240),
                createdAt = Instant.now().minusSeconds(120), // 2분 전
                updatedAt = Instant.now().minusSeconds(120)
            )

            val newToken = EmailVerificationToken.create(userId)

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { emailVerificationTokenRepository.findLatestByUserId(userId) } returns Mono.just(oldToken)
            every { authMethodRepository.findByUserIdAndProvider(userId, OAuthProvider.EMAIL) } returns Mono.just(authMethod)
            every { emailVerificationTokenRepository.softDeleteAllByUserId(userId) } returns Mono.empty()
            every { emailVerificationTokenRepository.save(any()) } returns Mono.just(newToken)
            every { emailVerificationService.sendVerificationEmail(email, any(), any()) } returns Mono.empty()

            // When: 코드 재전송
            val result = authService.resendVerificationCode(email, "en")

            // Then: 성공
            StepVerifier.create(result)
                .verifyComplete()

            verify(exactly = 1) { emailVerificationTokenRepository.softDeleteAllByUserId(userId) }
            verify(exactly = 1) { emailVerificationTokenRepository.save(any()) }
            verify(exactly = 1) { emailVerificationService.sendVerificationEmail(email, any(), any()) }
        }

        @Test
        @DisplayName("1분 이내 재전송 시, TooManyRequestsException을 발생시킨다")
        fun resendVerificationCode_WithinOneMinute_ThrowsException() {
            // Given: 30초 전에 코드를 보낸 사용자
            val userId = UUID.randomUUID()
            val email = "user@example.com"

            val user = User(
                id = userId,
                email = email,
                role = UserRole.USER
            )

            val recentToken = EmailVerificationToken(
                id = 1L,
                userId = userId,
                token = "111111",
                expiresAt = Instant.now().plusSeconds(270),
                createdAt = Instant.now().minusSeconds(30), // 30초 전
                updatedAt = Instant.now().minusSeconds(30)
            )

            every { userRepository.findByEmail(email) } returns Mono.just(user)
            every { emailVerificationTokenRepository.findLatestByUserId(userId) } returns Mono.just(recentToken)

            // When: 재전송 시도
            val result = authService.resendVerificationCode(email, "en")

            // Then: TooManyRequestsException 발생
            StepVerifier.create(result)
                .expectError(TooManyRequestsException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 1) { emailVerificationTokenRepository.findLatestByUserId(userId) }
            verify(exactly = 0) { emailVerificationService.sendVerificationEmail(any(), any(), any()) }
        }

        @Test
        @DisplayName("존재하지 않는 이메일로 재전송 시, InvalidCredentialsException을 발생시킨다")
        fun resendVerificationCode_WithNonExistentEmail_ThrowsException() {
            // Given: 존재하지 않는 이메일
            val email = "nonexistent@example.com"

            every { userRepository.findByEmail(email) } returns Mono.empty()

            // When: 재전송 시도
            val result = authService.resendVerificationCode(email, "en")

            // Then: InvalidCredentialsException 발생
            StepVerifier.create(result)
                .expectError(InvalidCredentialsException::class.java)
                .verify()

            verify(exactly = 1) { userRepository.findByEmail(email) }
            verify(exactly = 0) { emailVerificationTokenRepository.findLatestByUserId(any()) }
        }
    }
}
