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
     * 6자리 인증 코드를 이메일로 전송하며, 사용자는 앱에서 해당 코드를 입력하여 인증을 완료합니다.
     *
     * @param to 수신자 이메일
     * @param verificationToken 인증 코드 (6자리 숫자)
     * @param language 이메일 언어 (ko: 한국어, en: 영어, ja: 일본어)
     * @return Mono<Void>
     */
    fun sendVerificationEmail(to: String, verificationToken: String, language: String): Mono<Void>
}
