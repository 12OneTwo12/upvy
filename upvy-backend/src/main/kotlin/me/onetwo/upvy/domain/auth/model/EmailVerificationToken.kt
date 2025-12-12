package me.onetwo.upvy.domain.auth.model

import java.time.Instant
import java.util.UUID

/**
 * 이메일 인증 토큰 도메인 모델
 *
 * 이메일 가입 시 발급되는 인증 코드를 나타냅니다.
 * 6자리 숫자 코드가 이메일로 전송되며, 사용자가 앱에서 코드를 입력하면 이메일 인증이 완료됩니다.
 *
 * ### 코드 유효 기간
 * - 기본 5분
 * - expiresAt 이전에만 유효
 *
 * ### 코드 생성
 * - 6자리 랜덤 숫자 (100000-999999)
 * - 충돌 가능성이 있지만 짧은 만료 시간과 userId로 구분
 *
 * @property id 토큰 ID
 * @property userId 사용자 ID
 * @property token 인증 코드 (6자리 숫자)
 * @property expiresAt 만료 시각
 * @property createdAt 생성 시각
 * @property createdBy 생성자 ID
 * @property updatedAt 수정 시각
 * @property updatedBy 수정자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class EmailVerificationToken(
    val id: Long? = null,
    val userId: UUID,
    val token: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
) {
    /**
     * 토큰이 만료되었는지 확인
     *
     * @return 만료 여부
     */
    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    /**
     * 토큰이 유효한지 확인
     *
     * @return 유효 여부 (만료되지 않았고 삭제되지 않음)
     */
    fun isValid(): Boolean = !isExpired() && deletedAt == null

    companion object {
        /**
         * 인증 코드 길이 (6자리)
         */
        const val CODE_LENGTH = 6

        /**
         * 기본 코드 유효 기간 (5분)
         */
        const val DEFAULT_EXPIRY_MINUTES = 5L

        /**
         * 새로운 인증 코드 생성
         *
         * @param userId 사용자 ID
         * @return 새로운 인증 토큰 (6자리 숫자 코드)
         */
        fun create(userId: UUID): EmailVerificationToken {
            val now = Instant.now()
            val expiresAt = now.plusSeconds(60 * DEFAULT_EXPIRY_MINUTES)
            val code = generateCode()

            return EmailVerificationToken(
                userId = userId,
                token = code,
                expiresAt = expiresAt,
                createdAt = now,
                updatedAt = now
            )
        }

        /**
         * 6자리 랜덤 숫자 코드 생성
         *
         * @return 6자리 숫자 문자열 (100000-999999)
         */
        private fun generateCode(): String {
            return (100000..999999).random().toString()
        }
    }
}
