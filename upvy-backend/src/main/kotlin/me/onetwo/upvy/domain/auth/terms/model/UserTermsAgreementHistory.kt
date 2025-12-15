package me.onetwo.upvy.domain.auth.terms.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 약관 동의 이력
 *
 * 법적 증빙을 위해 사용자의 약관 동의/철회 이력을 모두 보관합니다.
 * GDPR, CCPA 등 개인정보 보호 법규 준수를 위한 감사 로그입니다.
 *
 * @property id 이력 ID
 * @property userId 사용자 ID
 * @property termsType 약관 타입
 * @property termsVersion 약관 버전
 * @property action 동작 (AGREED, UPDATED, REVOKED)
 * @property agreedAt 동의/철회 시각
 * @property ipAddress IP 주소 (법적 증빙용)
 * @property userAgent User Agent (법적 증빙용)
 * @property createdAt 생성 시각
 */
data class UserTermsAgreementHistory(
    val id: UUID? = null,
    val userId: UUID,
    val termsType: TermsType,
    val termsVersion: String,
    val action: AgreementAction,
    val agreedAt: Instant,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val createdAt: Instant = Instant.now()
)
