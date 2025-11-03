package me.onetwo.growsnap.domain.auth.service

import me.onetwo.growsnap.domain.auth.dto.RefreshTokenResponse
import me.onetwo.growsnap.domain.user.service.UserService
import me.onetwo.growsnap.infrastructure.redis.RefreshTokenRepository
import me.onetwo.growsnap.infrastructure.security.jwt.JwtTokenProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 인증 서비스 구현체
 *
 * JWT 토큰 갱신, 로그아웃 등의 인증 관련 비즈니스 로직을 처리합니다.
 *
 * @property jwtTokenProvider JWT 토큰 Provider
 * @property refreshTokenRepository Refresh Token 저장소
 * @property userService 사용자 서비스
 */
@Service
@Transactional(readOnly = true)
class AuthServiceImpl(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userService: UserService
) : AuthService {

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
    override fun refreshAccessToken(refreshToken: String): RefreshTokenResponse {
        // Refresh Token 유효성 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw IllegalArgumentException("유효하지 않은 Refresh Token입니다")
        }

        // Refresh Token에서 사용자 ID 추출
        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)

        // Redis에 저장된 Refresh Token과 비교
        val storedRefreshToken = refreshTokenRepository.findByUserId(userId)
            ?: throw IllegalArgumentException("Refresh Token을 찾을 수 없습니다")

        if (storedRefreshToken != refreshToken) {
            throw IllegalArgumentException("Refresh Token이 일치하지 않습니다")
        }

        // 사용자 정보 조회
        val user = userService.getUserById(userId).block()!!

        // 새로운 Access Token 생성
        val newAccessToken = jwtTokenProvider.generateAccessToken(
            userId = user.id!!,
            email = user.email,
            role = user.role
        )

        return RefreshTokenResponse(accessToken = newAccessToken)
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
}
