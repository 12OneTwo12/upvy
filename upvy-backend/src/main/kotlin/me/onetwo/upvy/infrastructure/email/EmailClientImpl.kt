package me.onetwo.upvy.infrastructure.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * JavaMailSender 기반 이메일 클라이언트 구현체
 *
 * Spring Boot Mail Sender를 사용하여 이메일을 발송합니다.
 * 블로킹 API (JavaMailSender)를 Reactor 스케줄러에서 비동기 실행합니다.
 *
 * @property mailSender Spring Mail Sender
 * @property fromEmail 발신자 이메일 주소
 * @property fromName 발신자 이름
 */
@Component
class EmailClientImpl(
    private val mailSender: JavaMailSender,
    @Value("\${spring.mail.from-email:noreply@upvy.app}") private val fromEmail: String,
    @Value("\${spring.mail.from-name:Upvy}") private val fromName: String
) : EmailClient {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * HTML 이메일 발송
     *
     * ### 비동기 처리
     * - JavaMailSender는 블로킹 API이므로 Schedulers.boundedElastic()에서 실행
     * - WebFlux 스레드 풀이 블로킹되지 않도록 함
     *
     * @param to 수신자 이메일 주소
     * @param subject 이메일 제목
     * @param htmlContent HTML 형식의 이메일 본문
     * @return 발송 완료를 알리는 Mono<Void>
     * @throws Exception 이메일 발송 실패 시
     */
    override fun sendHtmlEmail(to: String, subject: String, htmlContent: String): Mono<Void> {
        return Mono.fromRunnable<Void> {
            try {
                val message = mailSender.createMimeMessage()
                val helper = MimeMessageHelper(message, true, "UTF-8")

                helper.setFrom(fromEmail, fromName)
                helper.setTo(to)
                helper.setSubject(subject)
                helper.setText(htmlContent, true)

                mailSender.send(message)
                logger.info("HTML email sent successfully to: $to")
            } catch (e: Exception) {
                logger.error("Failed to send HTML email to: $to", e)
                throw e
            }
        }.subscribeOn(Schedulers.boundedElastic())
            .then()
    }
}
