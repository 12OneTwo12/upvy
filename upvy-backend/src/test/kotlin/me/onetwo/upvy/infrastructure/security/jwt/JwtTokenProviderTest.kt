package me.onetwo.upvy.infrastructure.security.jwt

import io.jsonwebtoken.JwtException
import me.onetwo.upvy.domain.user.model.UserRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

/**
 * JwtTokenProvider 단위 테스트
 *
 * JWT 토큰 생성, 검증, 파싱 기능을 테스트합니다.
 */
@DisplayName("JWT 토큰 Provider 테스트")
class JwtTokenProviderTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var jwtProperties: JwtProperties

    @BeforeEach
    fun setUp() {
        jwtProperties = JwtProperties(
            secret = "test-secret-key-for-jwt-token-generation-minimum-256-bits-required",
            accessTokenExpiration = 3600000, // 1시간
            refreshTokenExpiration = 1209600000 // 14일
        )
        jwtTokenProvider = JwtTokenProvider(jwtProperties)
    }

    @Test
    @DisplayName("Access Token 생성 성공")
    fun generateAccessToken_Success() {
        // Given
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val role = UserRole.USER

        // When
        val token = jwtTokenProvider.generateAccessToken(userId, email, role)

        // Then
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertTrue(jwtTokenProvider.validateToken(token))
    }

    @Test
    @DisplayName("Refresh Token 생성 성공")
    fun generateRefreshToken_Success() {
        // Given
        val userId = UUID.randomUUID()

        // When
        val token = jwtTokenProvider.generateRefreshToken(userId)

        // Then
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertTrue(jwtTokenProvider.validateToken(token))
    }

    @Test
    @DisplayName("토큰에서 사용자 ID 추출 성공")
    fun getUserIdFromToken_Success() {
        // Given
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val role = UserRole.USER
        val token = jwtTokenProvider.generateAccessToken(userId, email, role)

        // When
        val extractedUserId = jwtTokenProvider.getUserIdFromToken(token)

        // Then
        assertEquals(userId, extractedUserId)
    }

    @Test
    @DisplayName("토큰에서 이메일 추출 성공")
    fun getEmailFromToken_Success() {
        // Given
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val role = UserRole.USER
        val token = jwtTokenProvider.generateAccessToken(userId, email, role)

        // When
        val extractedEmail = jwtTokenProvider.getEmailFromToken(token)

        // Then
        assertEquals(email, extractedEmail)
    }

    @Test
    @DisplayName("토큰에서 역할 추출 성공")
    fun getRoleFromToken_Success() {
        // Given
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val role = UserRole.ADMIN
        val token = jwtTokenProvider.generateAccessToken(userId, email, role)

        // When
        val extractedRole = jwtTokenProvider.getRoleFromToken(token)

        // Then
        assertEquals(role, extractedRole)
    }

    @Test
    @DisplayName("유효한 토큰 검증 성공")
    fun validateToken_ValidToken_ReturnsTrue() {
        // Given
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val role = UserRole.USER
        val token = jwtTokenProvider.generateAccessToken(userId, email, role)

        // When
        val isValid = jwtTokenProvider.validateToken(token)

        // Then
        assertTrue(isValid)
    }

    @Test
    @DisplayName("유효하지 않은 토큰 검증 실패")
    fun validateToken_InvalidToken_ReturnsFalse() {
        // Given
        val invalidToken = "invalid.jwt.token"

        // When
        val isValid = jwtTokenProvider.validateToken(invalidToken)

        // Then
        assertFalse(isValid)
    }

    @Test
    @DisplayName("만료 시간 가져오기 성공")
    fun getExpirationFromToken_Success() {
        // Given
        val userId = UUID.randomUUID()
        val email = "test@example.com"
        val role = UserRole.USER
        val token = jwtTokenProvider.generateAccessToken(userId, email, role)

        // When
        val expiration = jwtTokenProvider.getExpirationFromToken(token)

        // Then
        assertNotNull(expiration)
        assertTrue(expiration.after(Date()))
    }

    @Test
    @DisplayName("만료된 토큰으로 사용자 ID 추출 시 예외 발생")
    fun getUserIdFromToken_ExpiredToken_ThrowsException() {
        // Given
        val expiredJwtProperties = JwtProperties(
            secret = "test-secret-key-for-jwt-token-generation-minimum-256-bits-required",
            accessTokenExpiration = -1000, // 이미 만료
            refreshTokenExpiration = 1209600000
        )
        val expiredJwtTokenProvider = JwtTokenProvider(expiredJwtProperties)
        val token = expiredJwtTokenProvider.generateAccessToken(UUID.randomUUID(), "test@example.com", UserRole.USER)

        // When & Then
        assertThrows<JwtException> {
            jwtTokenProvider.getUserIdFromToken(token)
        }
    }

    @Test
    @DisplayName("빈 토큰 검증 실패")
    fun validateToken_EmptyToken_ReturnsFalse() {
        // Given
        val emptyToken = ""

        // When
        val isValid = jwtTokenProvider.validateToken(emptyToken)

        // Then
        assertFalse(isValid)
    }

    @Test
    @DisplayName("다른 사용자 역할로 토큰 생성 및 검증")
    fun generateAccessToken_DifferentRoles_Success() {
        // Given
        val userId = UUID.randomUUID()
        val email = "admin@example.com"

        // When & Then - USER
        val userToken = jwtTokenProvider.generateAccessToken(userId, email, UserRole.USER)
        assertEquals(UserRole.USER, jwtTokenProvider.getRoleFromToken(userToken))

        // When & Then - ADMIN
        val adminToken = jwtTokenProvider.generateAccessToken(userId, email, UserRole.ADMIN)
        assertEquals(UserRole.ADMIN, jwtTokenProvider.getRoleFromToken(adminToken))
    }
}
