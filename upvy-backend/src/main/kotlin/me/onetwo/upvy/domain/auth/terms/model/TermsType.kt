package me.onetwo.upvy.domain.auth.terms.model

/**
 * 약관 타입
 *
 * @property SERVICE_TERMS 서비스 이용약관
 * @property PRIVACY_POLICY 개인정보 처리방침
 * @property COMMUNITY_GUIDELINES 커뮤니티 가이드라인
 * @property MARKETING_CONSENT 마케팅 정보 수신 동의
 */
enum class TermsType {
    SERVICE_TERMS,
    PRIVACY_POLICY,
    COMMUNITY_GUIDELINES,
    MARKETING_CONSENT
}
