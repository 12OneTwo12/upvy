package me.onetwo.upvy.domain.auth.terms.dto

import me.onetwo.upvy.domain.auth.terms.model.UserTermsAgreement
import java.time.Instant

/**
 * 약관 동의 응답
 *
 * 사용자의 약관 동의 상태를 반환합니다.
 *
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
 * @property marketingAgreed 마케팅 정보 수신 동의 여부
 * @property marketingVersion 동의한 마케팅 약관 버전
 * @property marketingAgreedAt 마케팅 정보 수신 동의 시각
 * @property isAllRequiredAgreed 모든 필수 약관 동의 여부
 */
data class TermsAgreementResponse(
    val userId: String,
    val serviceTermsAgreed: Boolean,
    val serviceTermsVersion: String?,
    val serviceTermsAgreedAt: Instant?,
    val privacyPolicyAgreed: Boolean,
    val privacyPolicyVersion: String?,
    val privacyPolicyAgreedAt: Instant?,
    val communityGuidelinesAgreed: Boolean,
    val communityGuidelinesVersion: String?,
    val communityGuidelinesAgreedAt: Instant?,
    val marketingAgreed: Boolean,
    val marketingVersion: String?,
    val marketingAgreedAt: Instant?,
    val isAllRequiredAgreed: Boolean
) {
    companion object {
        /**
         * UserTermsAgreement 엔티티를 TermsAgreementResponse로 변환
         *
         * @param agreement 약관 동의 엔티티
         * @return 약관 동의 응답 DTO
         */
        fun from(agreement: UserTermsAgreement): TermsAgreementResponse {
            return TermsAgreementResponse(
                userId = agreement.userId.toString(),
                serviceTermsAgreed = agreement.serviceTermsAgreed,
                serviceTermsVersion = agreement.serviceTermsVersion,
                serviceTermsAgreedAt = agreement.serviceTermsAgreedAt,
                privacyPolicyAgreed = agreement.privacyPolicyAgreed,
                privacyPolicyVersion = agreement.privacyPolicyVersion,
                privacyPolicyAgreedAt = agreement.privacyPolicyAgreedAt,
                communityGuidelinesAgreed = agreement.communityGuidelinesAgreed,
                communityGuidelinesVersion = agreement.communityGuidelinesVersion,
                communityGuidelinesAgreedAt = agreement.communityGuidelinesAgreedAt,
                marketingAgreed = agreement.marketingAgreed,
                marketingVersion = agreement.marketingVersion,
                marketingAgreedAt = agreement.marketingAgreedAt,
                isAllRequiredAgreed = agreement.isAllRequiredAgreed()
            )
        }
    }
}
