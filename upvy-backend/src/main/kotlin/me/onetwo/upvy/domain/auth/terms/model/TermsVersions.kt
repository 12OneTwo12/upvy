package me.onetwo.upvy.domain.auth.terms.model

/**
 * 약관 버전 상수
 *
 * 약관이 변경될 때마다 버전을 업데이트합니다.
 * 사용자가 최신 버전에 동의했는지 확인하는 데 사용됩니다.
 */
object TermsVersions {
    /**
     * 서비스 이용약관 현재 버전
     */
    const val CURRENT_SERVICE_TERMS_VERSION = "v1.0"

    /**
     * 개인정보 처리방침 현재 버전
     */
    const val CURRENT_PRIVACY_POLICY_VERSION = "v1.0"

    /**
     * 커뮤니티 가이드라인 현재 버전
     */
    const val CURRENT_COMMUNITY_GUIDELINES_VERSION = "v1.0"

    /**
     * 마케팅 정보 수신 동의 현재 버전
     */
    const val CURRENT_MARKETING_VERSION = "v1.0"
}
