package me.onetwo.upvy.domain.auth.service

import me.onetwo.upvy.domain.auth.dto.RefreshTokenResponse
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
 * @property emailService 이메일 발송 서비스
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
    private val emailService: EmailService,
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
     * @return Mono<Void>
     * @throws EmailAlreadyExistsException 이메일 인증 수단이 이미 존재하는 경우
     */
    @Transactional
    override fun signup(email: String, password: String, name: String?): Mono<Void> {
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
                                                sendVerificationEmail(existingUser, savedAuthMethod)
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
                    createNewUserWithEmailAuth(email, password)
                        .doOnSuccess {
                            logger.info("New user created with email auth: $email")
                        }
                }
            }
    }

    /**
     * 새로운 사용자 생성 (EMAIL 인증 수단 포함)
     */
    private fun createNewUserWithEmailAuth(email: String, password: String): Mono<Void> {
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
                        sendVerificationEmail(savedUser, savedAuthMethod)
                    }
            }
    }

    /**
     * 이메일 인증 메일 발송
     */
    private fun sendVerificationEmail(user: User, authMethod: UserAuthenticationMethod): Mono<Void> {
        // 이메일 인증 토큰 생성
        val verificationToken = EmailVerificationToken.create(user.id!!)

        return emailVerificationTokenRepository.save(verificationToken)
            .flatMap { savedToken ->
                // 인증 이메일 발송
                emailService.sendVerificationEmail(user.email, savedToken.token)
                    .doOnSuccess {
                        logger.info("Verification email sent to: ${user.email}")
                    }
            }
    }

    /**
     * 이메일 인증 완료
     *
     * 이메일로 전송된 인증 토큰을 검증하고 사용자의 이메일 인증 수단을 인증 완료 처리합니다.
     * 인증 완료 후 자동으로 로그인 처리되어 JWT 토큰을 반환합니다.
     *
     * ### 비즈니스 로직
     * 1. 토큰으로 인증 정보 조회
     * 2. 토큰 유효성 검증 (만료 여부, Soft Delete 여부)
     * 3. 사용자의 EMAIL 인증 수단 업데이트 (emailVerified = true)
     * 4. 사용된 토큰 무효화 (Soft Delete)
     * 5. JWT 토큰 발급 및 Redis에 Refresh Token 저장
     *
     * @param token 인증 토큰
     * @return JwtTokenDto Access Token과 Refresh Token
     * @throws InvalidVerificationTokenException 토큰이 유효하지 않은 경우
     * @throws TokenExpiredException 토큰이 만료된 경우
     */
    @Transactional
    override fun verifyEmail(token: String): Mono<JwtTokenDto> {
        return emailVerificationTokenRepository.findByToken(token)
            .switchIfEmpty(Mono.error(InvalidVerificationTokenException()))
            .flatMap { verificationToken ->
                // 토큰 만료 여부 확인
                if (verificationToken.isExpired()) {
                    return@flatMap Mono.error<JwtTokenDto>(TokenExpiredException())
                }

                // 토큰 유효성 확인
                if (!verificationToken.isValid()) {
                    return@flatMap Mono.error<JwtTokenDto>(InvalidVerificationTokenException())
                }

                // 사용자 조회
                userRepository.findById(verificationToken.userId)
                    .flatMap { user ->
                        // EMAIL 인증 수단 조회 및 업데이트
                        authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL)
                            .flatMap { authMethod ->
                                authMethodRepository.updateEmailVerified(authMethod.id!!, verified = true)
                                    .then(Mono.defer {
                                        // 사용된 토큰 무효화
                                        emailVerificationTokenRepository.softDeleteAllByUserId(user.id!!)
                                            .then(Mono.defer {
                                                // JWT 토큰 발급
                                                generateTokens(user)
                                                    .doOnSuccess {
                                                        logger.info(
                                                            "Email verification completed for user: ${user.id}, email: ${user.email}"
                                                        )
                                                    }
                                            })
                                    })
                            }
                    }
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
     *
     * @param email 이메일 주소
     * @param password 비밀번호 (평문)
     * @return JwtTokenDto Access Token과 Refresh Token
     * @throws InvalidCredentialsException 이메일 또는 비밀번호가 올바르지 않은 경우
     * @throws EmailNotVerifiedException 이메일 인증이 완료되지 않은 경우
     */
    @Transactional
    override fun signIn(email: String, password: String): Mono<JwtTokenDto> {
        return userRepository.findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidCredentialsException()))
            .flatMap { user ->
                // EMAIL 인증 수단 조회
                authMethodRepository.findByUserIdAndProvider(user.id!!, OAuthProvider.EMAIL)
                    .switchIfEmpty(Mono.error(InvalidCredentialsException()))
                    .flatMap { authMethod ->
                        // 이메일 인증 완료 여부 확인
                        if (!authMethod.emailVerified) {
                            return@flatMap Mono.error<JwtTokenDto>(EmailNotVerifiedException())
                        }

                        // 비밀번호 검증
                        if (!passwordEncoder.matches(password, authMethod.password)) {
                            return@flatMap Mono.error<JwtTokenDto>(InvalidCredentialsException())
                        }

                        // JWT 토큰 발급
                        generateTokens(user)
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
}
