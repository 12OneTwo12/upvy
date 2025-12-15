package me.onetwo.upvy.domain.auth.terms.service

import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementRequest
import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 약관 동의 Service 인터페이스
 *
 * 약관 동의 관련 비즈니스 로직을 처리합니다.
 */
interface TermsAgreementService {
    /**
     * 약관 동의 처리
     *
     * 사용자가 약관에 동의하면 현재 상태를 저장하고 이력을 기록합니다.
     * 모든 필수 약관(서비스 이용약관, 개인정보 처리방침, 커뮤니티 가이드라인)에 동의해야 합니다.
     *
     * ### 처리 흐름
     * 1. 기존 약관 동의 조회
     * 2. 약관 동의 정보 저장/업데이트
     * 3. 약관 동의 이력 저장 (법적 증빙)
     *
     * @param userId 사용자 ID
     * @param request 약관 동의 요청
     * @param ipAddress 사용자 IP 주소 (법적 증빙용, optional)
     * @param userAgent 사용자 User Agent (법적 증빙용, optional)
     * @return 약관 동의 응답
     */
    fun agreeToTerms(
        userId: UUID,
        request: TermsAgreementRequest,
        ipAddress: String? = null,
        userAgent: String? = null
    ): Mono<TermsAgreementResponse>

    /**
     * 약관 동의 상태 조회
     *
     * 사용자의 현재 약관 동의 상태를 조회합니다.
     * 약관 동의 정보가 없으면 모든 필드가 false인 응답을 반환합니다.
     *
     * @param userId 사용자 ID
     * @return 약관 동의 응답
     */
    fun getTermsAgreement(userId: UUID): Mono<TermsAgreementResponse>
}
