package me.onetwo.upvy.domain.user.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 인증 수단 도메인 모델
 *
 * 계정 통합 아키텍처 (Account Linking)에서 사용자의 인증 수단을 관리합니다.
 * 하나의 사용자는 여러 인증 수단을 가질 수 있습니다 (예: Google + Email).
 *
 * ### OAuth 인증 수단
 * - provider: GOOGLE, NAVER, KAKAO 등
 * - providerId: OAuth 제공자가 부여한 사용자 ID
 * - password: NULL
 * - emailVerified: TRUE (OAuth 제공자가 이미 검증)
 *
 * ### 이메일 인증 수단
 * - provider: EMAIL
 * - providerId: NULL
 * - password: BCrypt 암호화된 비밀번호
 * - emailVerified: 이메일 인증 링크를 통해 검증 후 TRUE
 *
 * @property id 인증 수단 ID
 * @property userId 사용자 ID (users.id)
 * @property provider 인증 제공자 (GOOGLE, NAVER, KAKAO, EMAIL)
 * @property providerId OAuth 제공자 ID (EMAIL인 경우 NULL)
 * @property password BCrypt 암호화된 비밀번호 (EMAIL인 경우만 사용)
 * @property emailVerified 이메일 인증 여부
 * @property isPrimary 주 인증 수단 여부 (로그인 시 우선순위)
 * @property createdAt 생성 시각
 * @property createdBy 생성자 ID
 * @property updatedAt 수정 시각
 * @property updatedBy 수정자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class UserAuthenticationMethod(
    val id: Long? = null,
    val userId: UUID,
    val provider: OAuthProvider,
    val providerId: String? = null,
    val password: String? = null,
    val emailVerified: Boolean = false,
    val isPrimary: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
) {
    /**
     * OAuth 인증 수단 여부 확인
     */
    fun isOAuth(): Boolean = provider != OAuthProvider.EMAIL

    /**
     * 이메일 인증 수단 여부 확인
     */
    fun isEmail(): Boolean = provider == OAuthProvider.EMAIL

    /**
     * 검증된 인증 수단 여부 확인
     */
    fun isVerified(): Boolean = when (provider) {
        OAuthProvider.EMAIL -> emailVerified
        else -> true // OAuth는 항상 검증된 것으로 간주
    }

    init {
        // 데이터 무결성 검증
        require(userId.toString().isNotBlank()) { "userId는 필수입니다." }

        when (provider) {
            OAuthProvider.EMAIL -> {
                require(providerId == null) { "EMAIL 인증 수단은 providerId가 NULL이어야 합니다." }
                require(!password.isNullOrBlank()) { "EMAIL 인증 수단은 password가 필수입니다." }
            }
            else -> {
                require(!providerId.isNullOrBlank()) { "OAuth 인증 수단은 providerId가 필수입니다." }
                require(password == null) { "OAuth 인증 수단은 password가 NULL이어야 합니다." }
            }
        }
    }
}
