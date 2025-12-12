package me.onetwo.upvy.domain.auth.service

import me.onetwo.upvy.infrastructure.email.EmailClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * 이메일 인증 서비스 구현체
 *
 * 이메일 인증 메일 발송 비즈니스 로직을 처리합니다.
 * HTML 템플릿 생성 및 인증 링크 구성은 이 계층에서 담당하며,
 * 실제 이메일 발송은 Infrastructure 계층의 EmailClient에 위임합니다.
 *
 * @property emailClient 이메일 발송 클라이언트 (Infrastructure)
 * @property frontendUrl 프론트엔드 URL
 */
@Service
class EmailVerificationServiceImpl(
    private val emailClient: EmailClient,
    @Value("\${app.frontend-url}") private val frontendUrl: String
) : EmailVerificationService {

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
    override fun sendVerificationEmail(to: String, verificationToken: String): Mono<Void> {
        val verificationLink = "$frontendUrl/auth/verify?token=$verificationToken"
        val subject = "[Upvy] 이메일 인증을 완료해주세요"
        val htmlContent = buildVerificationEmailHtml(verificationLink)

        return emailClient.sendHtmlEmail(to, subject, htmlContent)
    }

    /**
     * 이메일 인증 HTML 템플릿 생성
     *
     * @param verificationLink 인증 링크
     * @return HTML 콘텐츠
     */
    private fun buildVerificationEmailHtml(verificationLink: String): String {
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Upvy 이메일 인증</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif; background-color: #f5f5f5;">
                <table role="presentation" style="width: 100%; border-collapse: collapse;">
                    <tr>
                        <td align="center" style="padding: 40px 0;">
                            <table role="presentation" style="width: 600px; border-collapse: collapse; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);">
                                <!-- Header -->
                                <tr>
                                    <td style="padding: 40px 40px 20px 40px; text-align: center;">
                                        <h1 style="margin: 0; color: #333333; font-size: 28px; font-weight: 700;">Upvy</h1>
                                    </td>
                                </tr>

                                <!-- Content -->
                                <tr>
                                    <td style="padding: 0 40px 20px 40px;">
                                        <h2 style="margin: 0 0 20px 0; color: #333333; font-size: 24px; font-weight: 600;">이메일 인증을 완료해주세요</h2>
                                        <p style="margin: 0 0 20px 0; color: #666666; font-size: 16px; line-height: 1.6;">
                                            Upvy에 가입해주셔서 감사합니다!<br>
                                            아래 버튼을 클릭하여 이메일 인증을 완료해주세요.
                                        </p>
                                    </td>
                                </tr>

                                <!-- Button -->
                                <tr>
                                    <td style="padding: 0 40px 40px 40px; text-align: center;">
                                        <a href="$verificationLink"
                                           style="display: inline-block; padding: 16px 32px; background-color: #007AFF; color: #ffffff; text-decoration: none; border-radius: 8px; font-size: 16px; font-weight: 600;">
                                            이메일 인증하기
                                        </a>
                                    </td>
                                </tr>

                                <!-- Alternative Link -->
                                <tr>
                                    <td style="padding: 0 40px 40px 40px;">
                                        <p style="margin: 0 0 10px 0; color: #999999; font-size: 14px; line-height: 1.6;">
                                            버튼이 작동하지 않으면 아래 링크를 복사하여 브라우저에 붙여넣으세요:
                                        </p>
                                        <p style="margin: 0; color: #007AFF; font-size: 14px; word-break: break-all;">
                                            <a href="$verificationLink" style="color: #007AFF; text-decoration: none;">$verificationLink</a>
                                        </p>
                                    </td>
                                </tr>

                                <!-- Footer -->
                                <tr>
                                    <td style="padding: 20px 40px; background-color: #f9f9f9; border-top: 1px solid #eeeeee; border-radius: 0 0 8px 8px;">
                                        <p style="margin: 0; color: #999999; font-size: 12px; line-height: 1.6; text-align: center;">
                                            이 이메일은 발신 전용입니다. 문의사항이 있으시면 앱 내 고객지원을 이용해주세요.<br>
                                            인증 링크는 24시간 동안 유효합니다.
                                        </p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()
    }
}
