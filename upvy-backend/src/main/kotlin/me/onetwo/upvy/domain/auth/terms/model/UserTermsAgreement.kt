package me.onetwo.upvy.domain.auth.terms.model

import java.time.Instant
import java.util.UUID

/**
 * 사용자 약관 동의 현재 상태
 *
 * 사용자가 동의한 약관의 현재 상태를 저장합니다.
 * 약관이 변경되면 버전을 통해 재동의 여부를 확인할 수 있습니다.
 *
 * @property id 약관 동의 ID
 * @property userId 사용자 ID
 * @property serviceTermsAgreed 서비스 이용약관 동의 여부
 * @property serviceTermsVersion 동의한 서비스 이용약관 버전
 * @property serviceTermsAgreedAt 서비스 이용약관 동의 시각
 * @property privacyPolicyAgreed 개인정보 처리방침 동의 여부
 * @property privacyPolicyVersion 동의한 개인정보 처리방침 버전
 * @property privacyPolicyAgreedAt 개인정보 처리방침 동의 시각
 * @property communityGuidelinesAgreed 커뮤니티 가이드라인 동의 여부
 * @property communityGuidelinesVersion 동의한 커뮤니티 가이드라인 버전
 * @property communityGuidelinesAgreedAt 커뮤니티 가이드라인 동의 시각
 * @property marketingAgreed 마케팅 정보 수신 동의 여부 (선택)
 * @property marketingVersion 동의한 마케팅 약관 버전
 * @property marketingAgreedAt 마케팅 정보 수신 동의 시각
 * @property createdAt 생성 시각
 * @property createdBy 생성자 ID
 * @property updatedAt 수정 시각
 * @property updatedBy 수정자 ID
 * @property deletedAt 삭제 시각 (Soft Delete)
 */
data class UserTermsAgreement(
    val id: UUID? = null,
    val userId: UUID,

    // 서비스 이용약관 (필수)
    val serviceTermsAgreed: Boolean = false,
    val serviceTermsVersion: String? = null,
    val serviceTermsAgreedAt: Instant? = null,

    // 개인정보 처리방침 (필수)
    val privacyPolicyAgreed: Boolean = false,
    val privacyPolicyVersion: String? = null,
    val privacyPolicyAgreedAt: Instant? = null,

    // 커뮤니티 가이드라인 (필수)
    val communityGuidelinesAgreed: Boolean = false,
    val communityGuidelinesVersion: String? = null,
    val communityGuidelinesAgreedAt: Instant? = null,

    // 마케팅 정보 수신 동의 (선택)
    val marketingAgreed: Boolean = false,
    val marketingVersion: String? = null,
    val marketingAgreedAt: Instant? = null,

    // Audit Trail
    val createdAt: Instant = Instant.now(),
    val createdBy: String? = null,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null,
    val deletedAt: Instant? = null
) {
    /**
     * 모든 필수 약관에 동의했는지 확인
     *
     * 서비스 이용약관, 개인정보 처리방침, 커뮤니티 가이드라인 모두 동의해야 true 반환
     *
     * @return 모든 필수 약관 동의 여부
     */
    fun isAllRequiredAgreed(): Boolean {
        return serviceTermsAgreed &&
            privacyPolicyAgreed &&
            communityGuidelinesAgreed
    }

    /**
     * 모든 필수 약관이 최신 버전인지 확인
     *
     * 각 약관 타입의 최신 버전과 비교하여 확인합니다.
     * 서비스 이용약관, 개인정보 처리방침, 커뮤니티 가이드라인을 각각 독립적으로 확인합니다.
     *
     * @return 모든 필수 약관이 최신 버전인지 여부
     */
    fun isLatestVersion(): Boolean {
        return serviceTermsVersion == TermsVersions.CURRENT_SERVICE_TERMS_VERSION &&
            privacyPolicyVersion == TermsVersions.CURRENT_PRIVACY_POLICY_VERSION &&
            communityGuidelinesVersion == TermsVersions.CURRENT_COMMUNITY_GUIDELINES_VERSION
    }
}
