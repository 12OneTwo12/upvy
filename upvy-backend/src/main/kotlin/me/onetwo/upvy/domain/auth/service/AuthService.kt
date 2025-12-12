package me.onetwo.upvy.domain.auth.service

import me.onetwo.upvy.domain.auth.dto.EmailVerifyResponse
import me.onetwo.upvy.domain.auth.dto.RefreshTokenResponse
import me.onetwo.upvy.infrastructure.security.jwt.JwtTokenDto
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 인증 서비스 인터페이스
 *
 * JWT 토큰 갱신, 로그아웃, 이메일 가입/로그인 등의 인증 관련 비즈니스 로직을 정의합니다.
 */
interface AuthService {

    /**
     * Access Token 갱신
     *
     * Refresh Token을 검증하고 새로운 Access Token을 발급합니다.
     *
     * @param refreshToken Refresh Token
     * @return RefreshTokenResponse 새로운 Access Token
     * @throws IllegalArgumentException Refresh Token이 유효하지 않은 경우
     */
    fun refreshAccessToken(refreshToken: String): Mono<RefreshTokenResponse>

    /**
     * 로그아웃
     *
     * Redis에 저장된 Refresh Token을 삭제하여 로그아웃 처리합니다.
     *
     * @param refreshToken Refresh Token
     * @throws IllegalArgumentException Refresh Token이 유효하지 않은 경우
     */
    fun logout(refreshToken: String)

    /**
     * 사용자 ID로 Refresh Token 조회
     *
     * @param userId 사용자 ID
     * @return Refresh Token (존재하지 않으면 null)
     */
    fun getRefreshTokenByUserId(userId: UUID): String?

    /**
     * 이메일 회원가입
     *
     * 이메일 주소와 비밀번호로 새로운 계정을 생성합니다.
     * 회원가입 후 이메일 인증 메일이 발송되며, 인증 완료 전까지는 로그인할 수 없습니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일 중복 확인 (같은 이메일로 가입된 계정이 있는지 확인)
     * 2. 비밀번호 BCrypt 암호화
     * 3. 사용자 계정 생성 (emailVerified = false, provider = EMAIL)
     * 4. 이메일 인증 토큰 생성 (24시간 유효)
     * 5. 인증 이메일 발송
     *
     * @param email 이메일 주소
     * @param password 비밀번호 (평문)
     * @param name 사용자 이름 (선택, 프로필 생성 시 사용)
     * @param language 사용자 언어 설정 (ko: 한국어, en: 영어, ja: 일본어)
     * @return Mono<Void>
     * @throws me.onetwo.upvy.domain.auth.exception.EmailAlreadyExistsException 이메일이 이미 존재하는 경우
     */
    fun signup(email: String, password: String, nickname: String?, language: String): Mono<Void>

    /**
     * 이메일 인증 코드 검증
     *
     * 이메일로 발송된 6자리 인증 코드를 검증하고 사용자의 이메일을 인증 완료 처리합니다.
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
     * @throws me.onetwo.upvy.domain.auth.exception.InvalidVerificationTokenException 코드가 유효하지 않은 경우
     * @throws me.onetwo.upvy.domain.auth.exception.TokenExpiredException 코드가 만료된 경우
     */
    fun verifyEmailCode(email: String, code: String): Mono<EmailVerifyResponse>

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
     * @param language 사용자 언어 설정 (ko: 한국어, en: 영어, ja: 일본어)
     * @return Mono<Void>
     * @throws me.onetwo.upvy.domain.auth.exception.TooManyRequestsException 1분 이내 재전송 시도 시
     * @throws me.onetwo.upvy.domain.auth.exception.UserNotFoundException 사용자를 찾을 수 없는 경우
     */
    fun resendVerificationCode(email: String, language: String): Mono<Void>

    /**
     * 이메일 로그인
     *
     * 이메일 주소와 비밀번호로 로그인합니다.
     *
     * ### 비즈니스 로직
     * 1. 이메일로 사용자 조회
     * 2. 이메일 인증 완료 여부 확인 (emailVerified = true)
     * 3. 비밀번호 검증 (BCrypt)
     * 4. JWT 토큰 발급 및 Redis에 Refresh Token 저장
     * 5. EmailVerifyResponse 생성 (JWT 토큰에서 userId, email 추출)
     *
     * @param email 이메일 주소
     * @param password 비밀번호 (평문)
     * @return EmailVerifyResponse JWT 토큰과 사용자 정보
     * @throws me.onetwo.upvy.domain.auth.exception.InvalidCredentialsException 이메일 또는 비밀번호가 올바르지 않은 경우
     * @throws me.onetwo.upvy.domain.auth.exception.EmailNotVerifiedException 이메일 인증이 완료되지 않은 경우
     */
    fun signIn(email: String, password: String): Mono<EmailVerifyResponse>

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
     * @throws me.onetwo.upvy.domain.auth.exception.OAuthOnlyUserException OAuth 전용 사용자인 경우
     * @throws me.onetwo.upvy.domain.auth.exception.InvalidCredentialsException 현재 비밀번호가 올바르지 않은 경우
     */
    fun changePassword(userId: UUID, currentPassword: String, newPassword: String): Mono<Void>

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
     * @throws me.onetwo.upvy.domain.auth.exception.OAuthOnlyUserException OAuth 전용 사용자인 경우
     * @throws me.onetwo.upvy.domain.auth.exception.TooManyRequestsException 1분 이내 재전송 시도 시
     * @throws me.onetwo.upvy.domain.auth.exception.InvalidCredentialsException 사용자를 찾을 수 없는 경우
     */
    fun resetPasswordRequest(email: String, language: String): Mono<Void>

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
     * @throws me.onetwo.upvy.domain.auth.exception.InvalidVerificationTokenException 코드가 유효하지 않은 경우
     * @throws me.onetwo.upvy.domain.auth.exception.TokenExpiredException 코드가 만료된 경우
     * @throws me.onetwo.upvy.domain.auth.exception.OAuthOnlyUserException OAuth 전용 사용자인 경우
     */
    fun resetPasswordVerifyCode(email: String, code: String): Mono<Void>

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
     * @throws me.onetwo.upvy.domain.auth.exception.InvalidVerificationTokenException 코드가 유효하지 않은 경우
     * @throws me.onetwo.upvy.domain.auth.exception.TokenExpiredException 코드가 만료된 경우
     * @throws me.onetwo.upvy.domain.auth.exception.OAuthOnlyUserException OAuth 전용 사용자인 경우
     */
    fun resetPasswordConfirm(email: String, code: String, newPassword: String): Mono<Void>
}
