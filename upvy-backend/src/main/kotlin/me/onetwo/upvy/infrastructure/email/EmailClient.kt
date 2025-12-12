package me.onetwo.upvy.infrastructure.email

import reactor.core.publisher.Mono

/**
 * 이메일 발송 클라이언트 인터페이스
 *
 * 외부 이메일 서비스(JavaMailSender, SendGrid, AWS SES 등)를 추상화합니다.
 * 실제 이메일 발송 구현체는 Infrastructure 계층에서 제공되며,
 * Domain 계층은 이 인터페이스에만 의존합니다.
 */
interface EmailClient {

    /**
     * HTML 이메일 발송
     *
     * @param to 수신자 이메일 주소
     * @param subject 이메일 제목
     * @param htmlContent HTML 형식의 이메일 본문
     * @return 발송 완료를 알리는 Mono<Void>
     */
    fun sendHtmlEmail(
        to: String,
        subject: String,
        htmlContent: String
    ): Mono<Void>
}
