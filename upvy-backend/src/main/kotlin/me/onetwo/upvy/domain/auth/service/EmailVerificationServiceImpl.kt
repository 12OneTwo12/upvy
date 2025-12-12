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
     * 6자리 인증 코드를 이메일로 전송하며, 사용자는 앱에서 해당 코드를 입력하여 인증을 완료합니다.
     *
     * @param to 수신자 이메일
     * @param verificationToken 인증 코드 (6자리 숫자)
     * @param language 이메일 언어 (ko: 한국어, en: 영어, ja: 일본어)
     * @return Mono<Void>
     */
    override fun sendVerificationEmail(to: String, verificationToken: String, language: String): Mono<Void> {
        val subject = getSubject(language)
        val htmlContent = buildVerificationEmailHtml(verificationToken, language)

        return emailClient.sendHtmlEmail(to, subject, htmlContent)
    }

    /**
     * 언어별 제목 반환
     *
     * @param language 이메일 언어
     * @return 제목
     */
    private fun getSubject(language: String): String {
        return when (language.lowercase()) {
            "ko" -> "[Upvy] 이메일 인증 코드"
            "ja" -> "[Upvy] メール認証コード"
            else -> "[Upvy] Email Verification Code" // 기본값: 영어
        }
    }

    /**
     * 이메일 인증 HTML 템플릿 생성
     *
     * @param verificationCode 6자리 인증 코드
     * @param language 이메일 언어 (ko: 한국어, en: 영어, ja: 일본어)
     * @return HTML 콘텐츠
     */
    private fun buildVerificationEmailHtml(verificationCode: String, language: String): String {
        val content = getEmailContent(language)

        return """
            <!DOCTYPE html>
            <html lang="${content.langCode}">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${content.title}</title>
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
                                        <h2 style="margin: 0 0 20px 0; color: #333333; font-size: 24px; font-weight: 600;">${content.heading}</h2>
                                        <p style="margin: 0 0 20px 0; color: #666666; font-size: 16px; line-height: 1.6;">
                                            ${content.greeting}<br>
                                            ${content.instruction}
                                        </p>
                                    </td>
                                </tr>

                                <!-- Verification Code -->
                                <tr>
                                    <td style="padding: 0 40px 40px 40px; text-align: center;">
                                        <div style="display: inline-block; padding: 24px 48px; background-color: #f9f9f9; border-radius: 8px; border: 2px solid #e0e0e0;">
                                            <p style="margin: 0 0 8px 0; color: #999999; font-size: 14px; font-weight: 500;">${content.codeLabel}</p>
                                            <p style="margin: 0; color: #333333; font-size: 48px; font-weight: 700; letter-spacing: 8px; font-family: 'Courier New', monospace;">$verificationCode</p>
                                        </div>
                                    </td>
                                </tr>

                                <!-- Notice -->
                                <tr>
                                    <td style="padding: 0 40px 40px 40px;">
                                        <div style="padding: 16px; background-color: #fff3cd; border-left: 4px solid #ffc107; border-radius: 4px;">
                                            <p style="margin: 0; color: #856404; font-size: 14px; line-height: 1.6;">
                                                <strong>${content.securityTitle}</strong><br>
                                                ${content.securityNotice1}<br>
                                                ${content.securityNotice2}<br>
                                                ${content.securityNotice3}
                                            </p>
                                        </div>
                                    </td>
                                </tr>

                                <!-- Footer -->
                                <tr>
                                    <td style="padding: 20px 40px; background-color: #f9f9f9; border-top: 1px solid #eeeeee; border-radius: 0 0 8px 8px;">
                                        <p style="margin: 0; color: #999999; font-size: 12px; line-height: 1.6; text-align: center;">
                                            ${content.footer1}<br>
                                            ${content.footer2}
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

    /**
     * 언어별 이메일 내용 반환
     *
     * @param language 이메일 언어
     * @return 이메일 내용
     */
    private fun getEmailContent(language: String): EmailContent {
        return when (language.lowercase()) {
            "ko" -> EmailContent(
                langCode = "ko",
                title = "Upvy 이메일 인증 코드",
                heading = "이메일 인증 코드",
                greeting = "Upvy에 가입해주셔서 감사합니다!",
                instruction = "아래 인증 코드를 앱에 입력하여 이메일 인증을 완료해주세요.",
                codeLabel = "인증 코드",
                securityTitle = "보안 안내",
                securityNotice1 = "• 이 코드는 <strong>5분간 유효</strong>합니다.",
                securityNotice2 = "• 본인이 요청하지 않았다면 이 이메일을 무시하세요.",
                securityNotice3 = "• 코드를 타인과 공유하지 마세요.",
                footer1 = "이 이메일은 발신 전용입니다. 문의사항이 있으시면 앱 내 고객지원을 이용해주세요.",
                footer2 = "인증 코드가 만료되었다면 앱에서 \"코드 재전송\"을 눌러주세요."
            )
            "ja" -> EmailContent(
                langCode = "ja",
                title = "Upvy メール認証コード",
                heading = "メール認証コード",
                greeting = "Upvyにご登録いただきありがとうございます！",
                instruction = "以下の認証コードをアプリに入力して、メール認証を完了してください。",
                codeLabel = "認証コード",
                securityTitle = "セキュリティに関するご案内",
                securityNotice1 = "• このコードは<strong>5分間有効</strong>です。",
                securityNotice2 = "• ご本人が要求されていない場合は、このメールを無視してください。",
                securityNotice3 = "• コードを他人と共有しないでください。",
                footer1 = "このメールは送信専用です。お問い合わせはアプリ内のカスタマーサポートをご利用ください。",
                footer2 = "認証コードが期限切れの場合は、アプリで「コード再送信」をタップしてください。"
            )
            else -> EmailContent(
                langCode = "en",
                title = "Upvy Email Verification Code",
                heading = "Email Verification Code",
                greeting = "Thank you for signing up for Upvy!",
                instruction = "Please enter the verification code below in the app to complete email verification.",
                codeLabel = "Verification Code",
                securityTitle = "Security Notice",
                securityNotice1 = "• This code is valid for <strong>5 minutes</strong>.",
                securityNotice2 = "• If you didn't request this, please ignore this email.",
                securityNotice3 = "• Do not share this code with others.",
                footer1 = "This is an automated email. For inquiries, please use customer support in the app.",
                footer2 = "If the verification code has expired, please tap \"Resend Code\" in the app."
            )
        }
    }

    /**
     * 이메일 내용 데이터 클래스
     */
    private data class EmailContent(
        val langCode: String,
        val title: String,
        val heading: String,
        val greeting: String,
        val instruction: String,
        val codeLabel: String,
        val securityTitle: String,
        val securityNotice1: String,
        val securityNotice2: String,
        val securityNotice3: String,
        val footer1: String,
        val footer2: String
    )
}
