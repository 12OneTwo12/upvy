package me.onetwo.upvy.domain.auth.terms.dto

import jakarta.validation.constraints.AssertTrue

/**
 * 약관 동의 요청
 *
 * 사용자가 약관에 동의할 때 전송하는 요청 데이터입니다.
 * 모든 필수 약관(서비스 이용약관, 개인정보 처리방침, 커뮤니티 가이드라인)에 동의해야 합니다.
 *
 * @property serviceTermsAgreed 서비스 이용약관 동의 여부 (필수)
 * @property privacyPolicyAgreed 개인정보 처리방침 동의 여부 (필수)
 * @property communityGuidelinesAgreed 커뮤니티 가이드라인 동의 여부 (필수)
 * @property marketingAgreed 마케팅 정보 수신 동의 여부 (선택)
 */
data class TermsAgreementRequest(
    @field:AssertTrue(message = "서비스 이용약관 동의가 필요합니다")
    val serviceTermsAgreed: Boolean,

    @field:AssertTrue(message = "개인정보 처리방침 동의가 필요합니다")
    val privacyPolicyAgreed: Boolean,

    @field:AssertTrue(message = "커뮤니티 가이드라인 동의가 필요합니다")
    val communityGuidelinesAgreed: Boolean,

    val marketingAgreed: Boolean = false
)
