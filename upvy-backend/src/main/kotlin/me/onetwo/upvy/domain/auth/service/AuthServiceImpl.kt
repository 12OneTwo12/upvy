package me.onetwo.upvy.domain.auth.service

import me.onetwo.upvy.domain.auth.dto.EmailVerifyResponse
import me.onetwo.upvy.domain.auth.dto.RefreshTokenResponse
import me.onetwo.upvy.domain.auth.exception.EmailAlreadyExistsException
import me.onetwo.upvy.domain.auth.exception.EmailNotVerifiedException
import me.onetwo.upvy.domain.auth.exception.InvalidCredentialsException
import me.onetwo.upvy.domain.auth.exception.InvalidVerificationTokenException
import me.onetwo.upvy.domain.auth.exception.OAuthOnlyUserException
import me.onetwo.upvy.domain.auth.exception.TokenExpiredException
import me.onetwo.upvy.domain.auth.exception.TooManyRequestsException
import me.onetwo.upvy.domain.auth.model.EmailVerificationToken
import me.onetwo.upvy.domain.auth.repository.EmailVerificationTokenRepository
import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.model.UserAuthenticationMethod
import me.onetwo.upvy.domain.user.repository.UserAuthenticationMethodRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.domain.user.service.UserService
import me.onetwo.upvy.infrastructure.redis.RefreshTokenRepository
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenDto
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * 인증 서비스 구현체
 *
 * 계정 통합 아키텍처를 사용하여 JWT 토큰 갱신, 로그아웃, 이메일 가입/로그인 등의 인증 관련 비즈니스 로직을 처리합니다.
 *
 * @property jwtTokenProvider JWT 토큰 Provider
 * @property refreshTokenRepository Refresh Token 저장소
 * @property userService 사용자 서비스
 * @property userRepository 사용자 Repository
 * @property authMethodRepository 사용자 인증 수단 Repository
 * @property emailVerificationTokenRepository 이메일 인증 토큰 Repository
 * @property emailVerificationService 이메일 인증 메일 발송 서비스
 * @property passwordEncoder BCrypt 비밀번호 암호화
 */
@Service
@Transactional(readOnly = true)
class AuthServiceImpl(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val authMethodRepository: UserAuthenticationMethodRepository,
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val emailVerificationService: EmailVerificationService,
    private val passwordEncoder: BCryptPasswordEncoder
) : AuthService {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Access Token 갱신
     *
     * Refresh Token을 검증하고 새로운 Access Token을 발급합니다.
     *
     * @param refreshToken Refresh Token
     * @return RefreshTokenResponse 새로운 Access Token
     * @throws IllegalArgumentException Refresh Token이 유효하지 않은 경우
     */
    @Transactional
    override fun refreshAccessToken(refreshToken: String): Mono<RefreshTokenResponse> {
        // Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return Mono.error(IllegalArgumentException("유효하지 않은 Refresh Token입니다"))
        }

        // Refresh Token에서 사용자 ID 추출
        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)

        // Redis에 저장된 Refresh Token과 비교
        val storedRefreshToken = refreshTokenRepository.findByUserId(userId)
            ?: return Mono.error(IllegalArgumentException("Refresh Token을 찾을 수 없습니다"))

        if (storedRefreshToken != refreshToken) {
            return Mono.error(IllegalArgumentException("Refresh Token이 일치하지 않습니다"))
        }

        // 사용자 정보 조회하고 새로운 Access Token 생성
        return userService.getUserById(userId)
            .map { user ->
                val newAccessToken = jwtTokenProvider.generateAccessToken(
                    userId = user.id!!,
                    email = user.email,
                    role = user.role
                )
                RefreshTokenResponse(accessToken = newAccessToken)
            }
    }

    /**
     * 로그아웃
     *
     * Redis에 저장된 Refresh Token을 삭제하여 로그아웃 처리합니다.
     *
     * @param refreshToken Refresh Token
     * @throws IllegalArgumentException Refresh Token이 유효하지 않은 경우
     */
    @Transactional
    override fun logout(refreshToken: String) {
        // Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("유효하지 않은 Refresh Token입니다")
        }

        // Refresh Token에서 사용자 ID 추출
        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)

        // Redis에서 Refresh Token 삭제
        refreshTokenRepository.deleteByUserId(userId)
    }

    /**
     * 사용자 ID로 Refresh Token 조회
     *
     * @param userId 사용자 ID
     * @return Refresh Token (존재하지 않으면 null)
     */
    override fun getRefreshTokenByUserId(userId: UUID): String? {
        return refreshTokenRepository.findByUserId(userId)
    }

    /**
     * 이메일 회원가입
     *
     * 계정 통합 아키텍처를 사용하여 이메일 주소와 비밀번호로 새로운 계정을 생성하거나 기존 계정에 이메일 인증 수단을 추가합니다.
     *
     * ### 시나리오 1: 완전히 새로운 사용자
     * - User 생성 + EMAIL 인증 수단 생성
     * - 이메일 인증 메일 발송
     *
     * ### 시나리오 2: OAuth로 가입했지만 EMAIL 인증 수단이 없는 경우
     * - 기존 User에 EMAIL 인증 수단 추가
     * - 이메일 인증 메일 발송
     *
     * ### 시나리오 3: 이미 EMAIL 인증 수단이 있는 경우
     * - EmailAlreadyExistsException 발생
     *
     * @param email 이메일 주소
     * @param password 비밀번호 (평문)
     * @param name 사용자 이름 (선택, 프로필 생성 시 사용)
     * @param language 사용자 언어 설정 (ko: 한국어, en: 영어, ja: 일본어)
     * @return Mono<Void>
     * @throws EmailAlreadyExistsException 이메일 인증 수단이 이미 존재하는 경우
     */
    @Transactional
    override fun signup(email: String, password: String, name: String?, language: String): Mono<Void> {
        return userRepository.findByEmail(email)
            .hasElement()
            .flatMap { userExists ->
                if (userExists) {
                    // 같은 이메일의 사용자가 이미 있음
                    userRepository.findByEmail(email)
                        .flatMap { existingUser ->
                            authMethodRepository.findByUserIdAndProvider(existingUser.id!!, OAuthProvider.EMAIL)
                                .flatMap<Void> {
                                    // 이미 EMAIL 인증 수단이 있음
                                    Mono.error(EmailAlreadyExistsException(email))
                                }
                                .switchIfEmpty(
                                    // OAuth로 가입했지만 EMAIL 인증 수단은 없음 - 새 인증 수단 추가
                                    Mono.defer {
                                        val encodedPassword = passwordEncoder.encode(password)
                                        val authMethod = UserAuthenticationMethod(
                                            userId = existingUser.id!!,
                                            provider = OAuthProvider.EMAIL,
                                            password = encodedPassword,
                                            emailVerified = false,
                                            isPrimary = false
                                        )

                                        authMethodRepository.save(authMethod)
                                            .flatMap { savedAuthMethod ->
                                                sendVerificationEmail(existingUser, savedAuthMethod, language)
                                                    .doOnSuccess {
                                                        logger.info(
                                                            "Email auth method added to existing user: ${existingUser.id}, email: $email"
                                                        )
                                                    }
                                            }
                                    }
                                )
                        }
                } else {
                    // 완전히 새로운 사용자 - User + EMAIL 인증 수단 생성
                    createNewUserWithEmailAuth(email, password, language)
                        .doOnSuccess {
                            logger.info("New user created with email auth: $email")
                        }
                }
            }
    }

    /**
     * 새로운 사용자 생성 (EMAIL 인증 수단 포함)
     *
     * @param email 이메일 주소
     * @param password 비밀번호 (평문)
     * @param language 이메일 언어
     */
    private fun createNewUserWithEmailAuth(email: String, password: String, language: String): Mono<Void> {
        val encodedPassword = passwordEncoder.encode(password)

        // 1. User 생성
        val newUser = User(email = email)

        return userRepository.save(newUser)
            .flatMap { savedUser ->
                // 2. EMAIL 인증 수단 생성
                val authMethod = UserAuthenticationMethod(
                    userId = savedUser.id!!,
                    provider = OAuthProvider.EMAIL,
                    password = encodedPassword,
                    emailVerified = false,
                    isPrimary = true  // 첫 인증 수단은 primary
                )

                authMethodRepository.save(authMethod)
                    .flatMap { savedAuthMethod ->
                        // 3. 이메일 인증 메일 발송
                        sendVerificationEmail(savedUser, savedAuthMethod, language)
                    }
            }
    }

    /**
     * 이메일 인증 메일 발송
     *
     * @param user 사용자
     * @param authMethod 인증 수단
     * @param language 이메일 언어 (ko: 한국어, en: 영어, ja: 일본어)
     */
    private fun sendVerificationEmail(user: User, authMethod: UserAuthenticationMethod, language: String): Mono<Void> {
        // 이메일 인증 토큰 생성
        val verificationToken = EmailVerificationToken.create(user.id!!)

        return emailVerificationTokenRepository.save(verificationToken)
            .flatMap { savedToken ->
                // 인증 이메일 발송
                emailVerificationService.sendVerificationEmail(user.email, savedToken.token, language)
                    .doOnSuccess {
                        logger.info("Verification email sent to: ${user.email}, language: $language")
                    }
            }
    }

    /**
     * 이메일 인증 코드 검증
     *
     * 이메일로 발송된 6자리 인증 코드를 검증하고 사용자의 이메일 인증 수단을 인증 완료 처리합니다.
     * 인증 완료 후 자동으로 로그인 처리되어 JWT 토큰과 사용자 정보를 반환합니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일로 사용자 조회
     * 2. 사용자 ID + 코드로 인증 정보 조회
     * 3. 코드 유효성 검증 (만료 여부, Soft Delete 여부)
     * 4. 사용자의 EMAIL 인증 수단 업데이트 (emailVerified = true)
     * 5. 사용된 코드 무효화 (Soft Delete)
     * 6. JWT 토큰 발급 및 Redis에 Refresh Token 저장
     * 7. EmailVerifyResponse 생성
     *
     * @param email 이메일 주소
     * @param code 6자리 인증 코드
     * @return EmailVerifyResponse JWT 토큰과 사용자 정보
     * @throws InvalidVerificationTokenException 코드가 유효하지 않은 경우
     * @throws TokenExpiredException 코드가 만료된 경우
     */
    @Transactional
    override fun verifyEmailCode(email: String, code: String): Mono<EmailVerifyResponse> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
            .flatMap { user ->
                // 사용자 ID + 코드로 인증 토큰 조회
                emailVerificationTokenRepository.findByUserIdAndToken(user.id!!, code)
                    .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
                    .flatMap { verificationToken ->
                        // 코드 만료 여부 확인
                        if (verificationToken.isExpired()) {
                            return@flatMap Mono.error<EmailVerifyResponse>(TokenExpiredException())
                        }

                        // 코드 유효성 확인
                        if (!verificationToken.isValid()) {
                            return@flatMap Mono.error<EmailVerifyResponse>(InvalidVerificationTokenException())
                        }

                        // EMAIL 인증 수단 조회 및 업데이트
                        authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL)
                            .flatMap { authMethod ->
                                authMethodRepository.updateEmailVerified(authMethod.id!!, verified = true)
                                    .then(Mono.defer {
                                        // 사용된 코드 무효화
                                        emailVerificationTokenRepository.softDeleteAllByUserId(user.id)
                                            .then(Mono.defer {
                                                // JWT 토큰 발급 및 EmailVerifyResponse 생성
                                                generateTokensAndResponse(user)
                                                    .doOnSuccess {
                                                        logger.info(
                                                            "Email verification completed via code for user: ${user.id}, email: ${user.email}"
                                                        )
                                                    }
                                            })
                                    })
                            }
                    }
            }
    }

    /**
     * 인증 코드 재전송
     *
     * 만료되었거나 받지 못한 인증 코드를 재전송합니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일로 사용자 조회
     * 2. 마지막 코드 발송 시간 확인 (1분 이내 재전송 방지)
     * 3. 기존 코드 모두 무효화 (Soft Delete)
     * 4. 새로운 코드 생성 및 이메일 발송
     *
     * @param email 이메일 주소
     * @param language 이메일 언어 (ko: 한국어, en: 영어, ja: 일본어)
     * @return Mono<Void>
     * @throws TooManyRequestsException 1분 이내 재전송 시도 시
     */
    @Transactional
    override fun resendVerificationCode(email: String, language: String): Mono<Void> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidCredentialsException()))
            .flatMap { user ->
                // 마지막 코드 발송 시간 확인
                emailVerificationTokenRepository.findLatestByUserId(user.id!!)
                    .map { java.util.Optional.of(it) }
                    .defaultIfEmpty(java.util.Optional.empty())
                    .flatMap { optionalToken ->
                        if (optionalToken.isEmpty) {
                            // 토큰이 없으면 바로 재전송
                            resendCodeInternal(user, language)
                        } else {
                            // 토큰이 있으면 1분 이내 재전송 방지 확인
                            val latestToken = optionalToken.get()
                            val now = Instant.now()
                            val oneMinuteAgo = now.minusSeconds(60)

                            if (latestToken.createdAt.isAfter(oneMinuteAgo)) {
                                Mono.error<Void>(TooManyRequestsException())
                            } else {
                                resendCodeInternal(user, language)
                            }
                        }
                    }
            }
    }

    /**
     * 코드 재전송 내부 로직
     *
     * @param user 사용자
     * @param language 이메일 언어
     */
    private fun resendCodeInternal(user: User, language: String): Mono<Void> {
        // EMAIL 인증 수단 조회
        return authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL)
            .flatMap { authMethod ->
                // 기존 코드 모두 무효화
                emailVerificationTokenRepository.softDeleteAllByUserId(user.id)
                    .then(Mono.defer {
                        // 새로운 코드 생성 및 이메일 발송
                        sendVerificationEmail(user, authMethod, language)
                            .doOnSuccess {
                                logger.info("Verification code resent to: ${user.email}, language: $language")
                            }
                    })
            }
    }

    /**
     * 이메일 로그인
     *
     * 이메일 주소와 비밀번호로 로그인합니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일로 사용자 조회
     * 2. EMAIL 인증 수단 조회
     * 3. 이메일 인증 완료 여부 확인 (emailVerified = true)
     * 4. 비밀번호 검증 (BCrypt)
     * 5. JWT 토큰 발급 및 Redis에 Refresh Token 저장
     * 6. EmailVerifyResponse 생성 (JWT 토큰에서 userId, email 추출)
     *
     * @param email 이메일 주소
     * @param password 비밀번호 (평문)
     * @return EmailVerifyResponse JWT 토큰과 사용자 정보
     * @throws InvalidCredentialsException 이메일 또는 비밀번호가 올바르지 않은 경우
     * @throws EmailNotVerifiedException 이메일 인증이 완료되지 않은 경우
     */
    @Transactional
    override fun signIn(email: String, password: String): Mono<EmailVerifyResponse> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidCredentialsException()))
            .flatMap { user ->
                // EMAIL 인증 수단 조회
                authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL)
                    .switchIfEmpty(Mono.error(InvalidCredentialsException()))
                    .flatMap { authMethod ->
                        // 이메일 인증 완료 여부 확인
                        if (!authMethod.emailVerified) {
                            return@flatMap Mono.error<EmailVerifyResponse>(EmailNotVerifiedException())
                        }

                        // 비밀번호 검증
                        if (!passwordEncoder.matches(password, authMethod.password)) {
                            return@flatMap Mono.error<EmailVerifyResponse>(InvalidCredentialsException())
                        }

                        // JWT 토큰 발급 및 EmailVerifyResponse 생성
                        generateTokensAndResponse(user)
                            .doOnSuccess {
                                logger.info("Email sign-in successful for user: ${user.id}, email: ${user.email}")
                            }
                    }
            }
    }

    /**
     * JWT 토큰 생성 및 Refresh Token 저장
     */
    private fun generateTokens(user: User): Mono<JwtTokenDto> {
        val jwtTokens = JwtTokenDto(
            accessToken = jwtTokenProvider.generateAccessToken(
                userId = user.id!!,
                email = user.email,
                role = user.role
            ),
            refreshToken = jwtTokenProvider.generateRefreshToken(user.id!!)
        )

        // Redis에 Refresh Token 저장
        refreshTokenRepository.save(user.id!!, jwtTokens.refreshToken)

        return Mono.just(jwtTokens)
    }

    /**
     * JWT 토큰 생성 및 EmailVerifyResponse 생성
     *
     * Service 계층에서 JWT 토큰 파싱 및 DTO 생성을 담당합니다.
     * Controller는 HTTP 처리만 담당하도록 책임을 분리합니다.
     */
    private fun generateTokensAndResponse(user: User): Mono<EmailVerifyResponse> {
        return generateTokens(user)
            .map { jwtTokens ->
                // Service 계층에서 JWT 토큰 파싱 (Controller가 아님!)
                val userId = jwtTokenProvider.getUserIdFromToken(jwtTokens.accessToken)
                val email = jwtTokenProvider.getEmailFromToken(jwtTokens.accessToken)

                EmailVerifyResponse(
                    accessToken = jwtTokens.accessToken,
                    refreshToken = jwtTokens.refreshToken,
                    userId = userId,
                    email = email
                )
            }
    }

    /**
     * 비밀번호 변경
     *
     * 현재 비밀번호를 알고 있는 경우 새 비밀번호로 변경합니다.
     * 인증된 사용자만 사용 가능하며, OAuth 전용 사용자는 사용할 수 없습니다.
     *
     * ### 비즈니스 로직
     * 1. 사용자 ID로 EMAIL 인증 수단 조회
     * 2. EMAIL 인증 수단이 없으면 OAuthOnlyUserException 발생
     * 3. 현재 비밀번호 검증 (BCrypt)
     * 4. 새 비밀번호로 업데이트 (BCrypt 암호화)
     *
     * @param userId 사용자 ID (JWT에서 추출)
     * @param currentPassword 현재 비밀번호 (평문)
     * @param newPassword 새 비밀번호 (평문)
     * @return Mono<Void>
     * @throws OAuthOnlyUserException OAuth 전용 사용자인 경우
     * @throws InvalidCredentialsException 현재 비밀번호가 올바르지 않은 경우
     */
    @Transactional
    override fun changePassword(userId: UUID, currentPassword: String, newPassword: String): Mono<Void> {
        return authMethodRepository.findByUserIdAndProvider(userId, OAuthProvider.EMAIL)
            .switchIfEmpty(Mono.error(OAuthOnlyUserException()))
            .flatMap { authMethod ->
                // 현재 비밀번호 검증
                if (!passwordEncoder.matches(currentPassword, authMethod.password)) {
                    return@flatMap Mono.error<Void>(InvalidCredentialsException())
                }

                // 새 비밀번호로 업데이트 (BCrypt 암호화)
                val encodedPassword = passwordEncoder.encode(newPassword)
                authMethodRepository.updatePassword(authMethod.id!!, encodedPassword)
                    .then()
                    .doOnSuccess {
                        logger.info("Password changed successfully for user: $userId")
                    }
            }
    }

    /**
     * 비밀번호 재설정 요청
     *
     * 비밀번호를 잊어버린 경우 이메일로 인증 코드를 발송합니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일로 사용자 조회
     * 2. EMAIL 인증 수단이 없으면 OAuthOnlyUserException 발생
     * 3. 마지막 코드 발송 시간 확인 (1분 이내 재전송 방지)
     * 4. 기존 코드 모두 무효화 (Soft Delete)
     * 5. 새로운 인증 코드 생성 및 이메일 발송
     *
     * @param email 이메일 주소
     * @param language 이메일 언어 (ko: 한국어, en: 영어, ja: 일본어)
     * @return Mono<Void>
     * @throws OAuthOnlyUserException OAuth 전용 사용자인 경우
     * @throws TooManyRequestsException 1분 이내 재전송 시도 시
     * @throws InvalidCredentialsException 사용자를 찾을 수 없는 경우
     */
    @Transactional
    override fun resetPasswordRequest(email: String, language: String): Mono<Void> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidCredentialsException()))
            .flatMap { user ->
                // EMAIL 인증 수단이 있는지 확인
                authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL)
                    .switchIfEmpty(Mono.error(OAuthOnlyUserException()))
                    .flatMap { authMethod ->
                        // 마지막 코드 발송 시간 확인 (resendVerificationCode와 동일한 로직)
                        emailVerificationTokenRepository.findLatestByUserId(user.id)
                            .map { java.util.Optional.of(it) }
                            .defaultIfEmpty(java.util.Optional.empty())
                            .flatMap { optionalToken ->
                                if (optionalToken.isEmpty) {
                                    // 토큰이 없으면 바로 발송
                                    resendCodeInternal(user, language)
                                } else {
                                    // 토큰이 있으면 1분 이내 재전송 방지 확인
                                    val latestToken = optionalToken.get()
                                    val now = Instant.now()
                                    val oneMinuteAgo = now.minusSeconds(60)

                                    if (latestToken.createdAt.isAfter(oneMinuteAgo)) {
                                        Mono.error<Void>(TooManyRequestsException())
                                    } else {
                                        resendCodeInternal(user, language)
                                    }
                                }
                            }
                    }
            }
    }

    /**
     * 비밀번호 재설정 코드 검증
     *
     * 이메일로 받은 인증 코드가 유효한지 검증합니다.
     * 프론트엔드에서 코드 입력 후 "다음" 버튼 클릭 시 호출하여,
     * 코드가 유효하면 비밀번호 입력 화면으로 전환합니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일로 사용자 조회
     * 2. 사용자 ID + 코드로 인증 정보 조회
     * 3. 코드 유효성 검증 (만료 여부, Soft Delete 여부)
     * 4. EMAIL 인증 수단이 있는지 확인
     *
     * @param email 이메일 주소
     * @param code 6자리 인증 코드
     * @return Mono<Void>
     * @throws InvalidVerificationTokenException 코드가 유효하지 않은 경우
     * @throws TokenExpiredException 코드가 만료된 경우
     * @throws OAuthOnlyUserException OAuth 전용 사용자인 경우
     */
    @Transactional(readOnly = true)
    override fun resetPasswordVerifyCode(email: String, code: String): Mono<Void> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
            .flatMap { user ->
                // 사용자 ID + 코드로 인증 토큰 조회
                emailVerificationTokenRepository.findByUserIdAndToken(user.id!!, code)
                    .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
                    .flatMap { verificationToken ->
                        // 코드 만료 여부 확인
                        if (verificationToken.isExpired()) {
                            return@flatMap Mono.error<Void>(TokenExpiredException())
                        }

                        // 코드 유효성 확인
                        if (!verificationToken.isValid()) {
                            return@flatMap Mono.error<Void>(InvalidVerificationTokenException())
                        }

                        // EMAIL 인증 수단이 있는지 확인
                        authMethodRepository.findByUserIdAndProvider(user.id, OAuthProvider.EMAIL)
                            .switchIfEmpty(Mono.error(OAuthOnlyUserException()))
                            .then(Mono.defer {
                                logger.info("Password reset code verified for user: ${user.id}, email: ${user.email}")
                                Mono.empty()
                            })
                    }
            }
    }

    /**
     * 비밀번호 재설정 확정
     *
     * 검증된 인증 코드로 새 비밀번호로 재설정합니다.
     * 프론트엔드에서 비밀번호 입력 후 "완료" 버튼 클릭 시 호출합니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일로 사용자 조회
     * 2. 사용자 ID + 코드로 인증 정보 조회
     * 3. 코드 유효성 재검증 (만료 여부, Soft Delete 여부)
     * 4. EMAIL 인증 수단 조회
     * 5. 새 비밀번호로 업데이트 (BCrypt 암호화)
     * 6. 사용된 코드 무효화 (Soft Delete)
     *
     * @param email 이메일 주소
     * @param code 6자리 인증 코드
     * @param newPassword 새 비밀번호 (평문)
     * @return Mono<Void>
     * @throws InvalidVerificationTokenException 코드가 유효하지 않은 경우
     * @throws TokenExpiredException 코드가 만료된 경우
     * @throws OAuthOnlyUserException OAuth 전용 사용자인 경우
     */
    @Transactional
    override fun resetPasswordConfirm(email: String, code: String, newPassword: String): Mono<Void> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
            .flatMap { user ->
                // 사용자 ID + 코드로 인증 토큰 조회
                emailVerificationTokenRepository.findByUserIdAndToken(user.id!!, code)
                    .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
                    .flatMap { verificationToken ->
                        // 코드 만료 여부 확인
                        if (verificationToken.isExpired()) {
                            return@flatMap Mono.error<Void>(TokenExpiredException())
                        }

                        // 코드 유효성 확인
                        if (!verificationToken.isValid()) {
                            return@flatMap Mono.error<Void>(InvalidVerificationTokenException())
                        }

                        // EMAIL 인증 수단 조회
                        authMethodRepository.findByUserIdAndProvider(user.id, OAuthProvider.EMAIL)
                            .switchIfEmpty(Mono.error(OAuthOnlyUserException()))
                            .flatMap { authMethod ->
                                // 새 비밀번호로 업데이트 (BCrypt 암호화)
                                val encodedPassword = passwordEncoder.encode(newPassword)
                                authMethodRepository.updatePassword(authMethod.id!!, encodedPassword)
                                    .then(
                                        // 사용된 코드 무효화
                                        emailVerificationTokenRepository.softDelete(verificationToken.id!!)
                                    )
                                    .doOnSuccess {
                                        logger.info("Password reset successful for user: ${user.id}, email: ${user.email}")
                                    }
                            }
                    }
            }
    }
}
