package me.onetwo.upvy.domain.auth.terms.controller

import jakarta.validation.Valid
import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementRequest
import me.onetwo.upvy.domain.auth.terms.dto.TermsAgreementResponse
import me.onetwo.upvy.domain.auth.terms.service.TermsAgreementService
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal

/**
 * 약관 동의 컨트롤러
 *
 * 사용자의 약관 동의 상태를 관리하는 API를 제공합니다.
 * 서비스 이용약관, 개인정보 처리방침, 커뮤니티 가이드라인 동의와
 * 마케팅 정보 수신 동의(선택) 상태를 추적합니다.
 *
 * ### 법적 준수 (Legal Compliance)
 * 약관 동의 이력은 법적 증빙을 위해 다음 정보를 기록합니다:
 * - 약관 버전
 * - 동의 시각
 * - IP 주소
 * - User Agent
 *
 * @property termsAgreementService 약관 동의 Service
 */
@RestController
@RequestMapping(ApiPaths.API_V1_AUTH)
class TermsAgreementController(
    private val termsAgreementService: TermsAgreementService
) {

    /**
     * 약관 동의 처리
     *
     * 사용자가 약관에 동의하면 현재 상태를 저장하고 이력을 기록합니다.
     * 모든 필수 약관(서비스 이용약관, 개인정보 처리방침, 커뮤니티 가이드라인)에 동의해야 합니다.
     *
     * ### 처리 흐름
     * 1. 기존 약관 동의 조회
     * 2. 약관 동의 정보 저장/업데이트
     * 3. 약관 동의 이력 저장 (법적 증빙용)
     *
     * ### 법적 증빙 정보
     * - IP 주소: 요청자의 원격 IP 주소
     * - User Agent: 요청 헤더의 User-Agent 정보
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @param request 약관 동의 요청
     * @param serverHttpRequest ServerHttpRequest (IP 주소, User Agent 추출용)
     * @return TermsAgreementResponse 약관 동의 응답
     */
    @PostMapping("/terms-agreement")
    fun agreeToTerms(
        principal: Mono<Principal>,
        @Valid @RequestBody request: TermsAgreementRequest,
        serverHttpRequest: ServerHttpRequest
    ): Mono<ResponseEntity<TermsAgreementResponse>> {
        // IP 주소 추출 (Reverse Proxy 환경 고려)
        val ipAddress = extractClientIpAddress(serverHttpRequest)

        // User Agent 추출
        val userAgent = serverHttpRequest.headers.getFirst("User-Agent")

        return principal
            .toUserId()
            .flatMap { userId ->
                termsAgreementService.agreeToTerms(userId, request, ipAddress, userAgent)
            }
            .map { response -> ResponseEntity.status(HttpStatus.CREATED).body(response) }
    }

    /**
     * 약관 동의 상태 조회
     *
     * 현재 사용자의 약관 동의 상태를 조회합니다.
     * 약관 동의 정보가 없으면 모든 필드가 false인 응답을 반환합니다.
     *
     * ### 응답 필드
     * - serviceTermsAgreed: 서비스 이용약관 동의 여부
     * - privacyPolicyAgreed: 개인정보 처리방침 동의 여부
     * - communityGuidelinesAgreed: 커뮤니티 가이드라인 동의 여부
     * - marketingAgreed: 마케팅 정보 수신 동의 여부 (선택)
     * - isAllRequiredAgreed: 모든 필수 약관 동의 여부
     *
     * @param principal 인증된 사용자 Principal (Spring Security에서 자동 주입)
     * @return TermsAgreementResponse 약관 동의 응답
     */
    @GetMapping("/terms-agreement")
    fun getTermsAgreement(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<TermsAgreementResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                termsAgreementService.getTermsAgreement(userId)
            }
            .map { response -> ResponseEntity.ok(response) }
    }

    /**
     * 클라이언트 IP 주소 추출
     *
     * Reverse Proxy 환경을 고려하여 IP 주소를 추출합니다.
     * 우선순위:
     * 1. X-Forwarded-For 헤더 (첫 번째 IP 사용)
     * 2. X-Real-IP 헤더
     * 3. RemoteAddress (직접 연결)
     *
     * @param serverHttpRequest ServerHttpRequest
     * @return IP 주소 (nullable)
     */
    private fun extractClientIpAddress(serverHttpRequest: ServerHttpRequest): String? {
        // X-Forwarded-For 헤더 확인 (Reverse Proxy가 설정한 원본 IP)
        val xForwardedFor = serverHttpRequest.headers.getFirst("X-Forwarded-For")
        if (!xForwardedFor.isNullOrBlank()) {
            // X-Forwarded-For는 "client, proxy1, proxy2" 형식이므로 첫 번째 IP 사용
            return xForwardedFor.split(",").firstOrNull()?.trim()
        }

        // X-Real-IP 헤더 확인
        val xRealIp = serverHttpRequest.headers.getFirst("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) {
            return xRealIp.trim()
        }

        // 직접 연결된 경우 RemoteAddress 사용
        return serverHttpRequest.remoteAddress?.address?.hostAddress
    }
}
