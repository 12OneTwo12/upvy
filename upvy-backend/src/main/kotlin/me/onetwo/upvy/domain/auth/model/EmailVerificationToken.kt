package me.onetwo.upvy.domain.auth.model

import java.time.Instant
import java.util.UUID

/**
 * 이메일 인증 토큰 도메인 모델
 *
 * 이메일 가입 시 발급되는 인증 토큰을 나타냅니다.
 * 토큰은 이메일로 전송되며, 사용자가 링크를 클릭하면 이메일 인증이 완료됩니다.
 *
 * ### 토큰 유효 기간
 * - 기본 24시간
 * - expiresAt 이전에만 유효
 *
 * ### 토큰 생성
 * - UUID.randomUUID()로 생성
 * - 고유성 보장
 *
 * @property id 토큰 ID
 * @property userId 사용자 ID
 * @property token 인증 토큰 (UUID)
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
         * 기본 토큰 유효 기간 (24시간)
         */
        const val DEFAULT_EXPIRY_HOURS = 24L

        /**
         * 새로운 인증 토큰 생성
         *
         * @param userId 사용자 ID
         * @return 새로운 인증 토큰
         */
        fun create(userId: UUID): EmailVerificationToken {
            val now = Instant.now()
            val expiresAt = now.plusSeconds(60 * 60 * DEFAULT_EXPIRY_HOURS)

            return EmailVerificationToken(
                userId = userId,
                token = UUID.randomUUID().toString(),
                expiresAt = expiresAt,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
