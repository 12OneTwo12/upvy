package me.onetwo.upvy.domain.auth.service

import reactor.core.publisher.Mono

/**
 * 이메일 인증 서비스 인터페이스
 *
 * 이메일 인증 메일 발송 비즈니스 로직을 정의합니다.
 * 실제 이메일 발송은 Infrastructure 계층의 EmailClient에 위임합니다.
 */
interface EmailVerificationService {

    /**
     * 이메일 인증 메일 발송
     *
     * 회원가입 시 이메일 인증을 위한 메일을 발송합니다.
     * 인증 링크는 {FRONTEND_URL}/auth/verify?token={token} 형식입니다.
     *
     * @param to 수신자 이메일
     * @param verificationToken 인증 토큰
     * @return Mono<Void>
     */
    fun sendVerificationEmail(to: String, verificationToken: String): Mono<Void>
}
