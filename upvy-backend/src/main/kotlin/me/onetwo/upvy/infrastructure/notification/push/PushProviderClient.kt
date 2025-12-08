package me.onetwo.upvy.infrastructure.notification.push

import me.onetwo.upvy.domain.notification.model.PushProvider as PushProviderType
import reactor.core.publisher.Mono

/**
 * 푸시 알림 제공자 인터페이스
 *
 * 다양한 푸시 알림 서비스(Expo, FCM, APNs)에 대한 공통 인터페이스를 정의합니다.
 */
interface PushProviderClient {

    /**
     * 제공자 유형
     */
    val providerType: PushProviderType

    /**
     * 푸시 알림 발송
     *
     * @param tokens 발송할 토큰 목록
     * @param title 알림 제목
     * @param body 알림 본문
     * @param data 추가 데이터 (JSON 문자열)
     * @return 발송 결과
     */
    fun sendPush(
        tokens: List<String>,
        title: String,
        body: String,
        data: String? = null
    ): Mono<PushSendResult>

    /**
     * 푸시 알림 발송 (상세 결과 포함)
     *
     * @param tokens 발송할 토큰 목록
     * @param title 알림 제목
     * @param body 알림 본문
     * @param data 추가 데이터 (JSON 문자열)
     * @return 상세 발송 결과
     */
    fun sendPushWithResult(
        tokens: List<String>,
        title: String,
        body: String,
        data: String? = null
    ): Mono<PushResult>
}

/**
 * 푸시 발송 결과
 *
 * @property successCount 성공한 토큰 수
 * @property failureCount 실패한 토큰 수
 * @property failedTokens 발송 실패한 토큰 목록 (무효화된 토큰 등)
 */
data class PushSendResult(
    val successCount: Int,
    val failureCount: Int,
    val failedTokens: List<String> = emptyList()
)

/**
 * 푸시 발송 상세 결과
 *
 * @property hasSuccess 하나 이상의 토큰에 성공적으로 발송되었는지 여부
 * @property tokenResults 각 토큰별 발송 결과
 */
data class PushResult(
    val hasSuccess: Boolean,
    val tokenResults: List<TokenResult>
)

/**
 * 개별 토큰 발송 결과
 *
 * @property success 발송 성공 여부
 * @property messageId 프로바이더에서 반환한 메시지 ID
 * @property errorCode 에러 코드 (실패 시)
 * @property errorMessage 에러 메시지 (실패 시)
 */
data class TokenResult(
    val success: Boolean,
    val messageId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null
)
