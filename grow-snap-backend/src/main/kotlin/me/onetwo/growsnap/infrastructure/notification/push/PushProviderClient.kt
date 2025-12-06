package me.onetwo.growsnap.infrastructure.notification.push

import me.onetwo.growsnap.domain.notification.model.PushProvider as PushProviderType
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
