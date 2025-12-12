package me.onetwo.upvy.domain.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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

/**
 * 이메일 회원가입 요청
 *
 * @property email 이메일 주소
 * @property password 비밀번호
 * @property language 사용자 언어 설정 (ko: 한국어, en: 영어, ja: 일본어, 기본값: en)
 */
data class EmailSignupRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수입니다")
    val password: String,

    val language: String = "en"
)

/**
 * 이메일 로그인 요청
 *
 * @property email 이메일 주소
 * @property password 비밀번호
 */
data class EmailSigninRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수입니다")
    val password: String
)

/**
 * 이메일 인증 코드 검증 요청
 *
 * 이메일로 발송된 6자리 인증 코드를 검증합니다.
 *
 * @property email 이메일 주소
 * @property code 6자리 인증 코드
 */
data class EmailVerifyCodeRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    val email: String,

    @field:NotBlank(message = "인증 코드는 필수입니다")
    @field:Size(min = 6, max = 6, message = "인증 코드는 6자리여야 합니다")
    val code: String
)

/**
 * 인증 코드 재전송 요청
 *
 * 만료되었거나 받지 못한 인증 코드를 재전송합니다.
 *
 * @property email 이메일 주소
 * @property language 사용자 언어 설정 (ko: 한국어, en: 영어, ja: 일본어, 기본값: en)
 */
data class ResendVerificationCodeRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    val email: String,

    val language: String = "en"
)

/**
 * 이메일 인증 응답
 *
 * 이메일 인증 완료 후 자동 로그인되어 JWT 토큰을 반환합니다.
 *
 * @property accessToken JWT Access Token
 * @property refreshToken JWT Refresh Token
 * @property userId 사용자 ID
 * @property email 사용자 이메일
 */
data class EmailVerifyResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: UUID,
    val email: String
)

/**
 * 비밀번호 변경 요청
 *
 * 현재 비밀번호를 알고 있는 경우 새 비밀번호로 변경합니다.
 * 인증된 사용자만 사용 가능하며, OAuth 전용 사용자는 사용할 수 없습니다.
 *
 * @property currentPassword 현재 비밀번호
 * @property newPassword 새 비밀번호
 */
data class ChangePasswordRequest(
    @field:NotBlank(message = "현재 비밀번호는 필수입니다")
    val currentPassword: String,

    @field:NotBlank(message = "새 비밀번호는 필수입니다")
    @field:Size(min = 8, message = "새 비밀번호는 최소 8자 이상이어야 합니다")
    val newPassword: String
)

/**
 * 비밀번호 재설정 요청
 *
 * 비밀번호를 잊어버린 경우 이메일로 인증 코드를 받아 재설정을 시작합니다.
 *
 * @property email 이메일 주소
 * @property language 이메일 언어 (ko: 한국어, en: 영어, ja: 일본어, 기본값: en)
 */
data class ResetPasswordRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    val email: String,

    val language: String = "en"
)

/**
 * 비밀번호 재설정 코드 검증 요청
 *
 * 이메일로 받은 인증 코드가 유효한지 검증합니다.
 * 프론트엔드에서 코드 입력 후 "다음" 버튼 클릭 시 사용합니다.
 *
 * @property email 이메일 주소
 * @property code 6자리 인증 코드
 */
data class ResetPasswordVerifyCodeRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    val email: String,

    @field:NotBlank(message = "인증 코드는 필수입니다")
    @field:Size(min = 6, max = 6, message = "인증 코드는 6자리여야 합니다")
    val code: String
)

/**
 * 비밀번호 재설정 확정 요청
 *
 * 검증된 인증 코드로 새 비밀번호로 재설정합니다.
 * 프론트엔드에서 비밀번호 입력 후 "완료" 버튼 클릭 시 사용합니다.
 *
 * @property email 이메일 주소
 * @property code 6자리 인증 코드
 * @property newPassword 새 비밀번호
 */
data class ResetPasswordConfirmRequest(
    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    val email: String,

    @field:NotBlank(message = "인증 코드는 필수입니다")
    @field:Size(min = 6, max = 6, message = "인증 코드는 6자리여야 합니다")
    val code: String,

    @field:NotBlank(message = "새 비밀번호는 필수입니다")
    @field:Size(min = 8, message = "새 비밀번호는 최소 8자 이상이어야 합니다")
    val newPassword: String
)
