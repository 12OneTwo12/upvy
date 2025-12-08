package me.onetwo.upvy.domain.auth.dto

import jakarta.validation.constraints.NotBlank
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenDto
import java.util.UUID

/**
 * Google OAuth2 로그인 요청
 *
 * 프론트엔드에서 Google OAuth2로부터 받은 인가 코드를 전달받습니다.
 *
 * @property code Google OAuth2 인가 코드
 * @property redirectUri 리다이렉트 URI (프론트엔드 URL)
 */
data class GoogleLoginRequest(
    @field:NotBlank(message = "인가 코드는 필수입니다")
    val code: String,

    @field:NotBlank(message = "리다이렉트 URI는 필수입니다")
    val redirectUri: String
)

/**
 * 로그인 응답
 *
 * 로그인 성공 시 JWT 토큰과 사용자 정보를 반환합니다.
 *
 * @property accessToken JWT Access Token
 * @property refreshToken JWT Refresh Token
 * @property userId 사용자 ID
 * @property email 사용자 이메일
 * @property isNewUser 신규 가입 여부 (true인 경우 프로필 생성 필요)
 */
data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: UUID,
    val email: String,
    val isNewUser: Boolean
) {
    companion object {
        /**
         * JwtTokenDto와 사용자 정보로부터 LoginResponse 생성
         *
         * @param tokens JWT 토큰
         * @param userId 사용자 ID
         * @param email 사용자 이메일
         * @param isNewUser 신규 가입 여부
         * @return LoginResponse 객체
         */
        fun from(
            tokens: JwtTokenDto,
            userId: UUID,
            email: String,
            isNewUser: Boolean
        ): LoginResponse {
            return LoginResponse(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                userId = userId,
                email = email,
                isNewUser = isNewUser
            )
        }
    }
}

/**
 * Token 갱신 요청
 *
 * Refresh Token을 사용하여 새로운 Access Token을 발급받습니다.
 *
 * @property refreshToken JWT Refresh Token
 */
data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh Token은 필수입니다")
    val refreshToken: String
)

/**
 * Token 갱신 응답
 *
 * 새로 발급된 Access Token을 반환합니다.
 *
 * @property accessToken 새로운 JWT Access Token
 */
data class RefreshTokenResponse(
    val accessToken: String
)

/**
 * 로그아웃 요청
 *
 * Refresh Token을 무효화합니다.
 *
 * @property refreshToken JWT Refresh Token
 */
data class LogoutRequest(
    @field:NotBlank(message = "Refresh Token은 필수입니다")
    val refreshToken: String
)
