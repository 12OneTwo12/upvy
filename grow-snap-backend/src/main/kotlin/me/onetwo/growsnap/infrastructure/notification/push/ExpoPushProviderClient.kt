package me.onetwo.growsnap.infrastructure.notification.push

import com.fasterxml.jackson.databind.ObjectMapper
import me.onetwo.growsnap.domain.notification.model.PushProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Expo Push Notification 제공자 클라이언트
 *
 * Expo Push API를 사용하여 푸시 알림을 발송합니다.
 *
 * @see https://docs.expo.dev/push-notifications/sending-notifications/
 */
@Component
class ExpoPushProviderClient(
    private val objectMapper: ObjectMapper,
    @Value("\${expo.push.api-url:https://exp.host/--/api/v2/push/send}")
    private val expoPushApiUrl: String
) : PushProviderClient {

    private val logger = LoggerFactory.getLogger(ExpoPushProviderClient::class.java)

    private val webClient = WebClient.builder()
        .baseUrl(expoPushApiUrl)
        .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .build()

    override val providerType: PushProvider = PushProvider.EXPO

    /**
     * Expo Push API를 통해 푸시 알림 발송
     */
    override fun sendPush(
        tokens: List<String>,
        title: String,
        body: String,
        data: String?
    ): Mono<PushSendResult> {
        if (tokens.isEmpty()) {
            logger.debug("No tokens to send push notification")
            return Mono.just(PushSendResult(successCount = 0, failureCount = 0))
        }

        val messages = tokens.map { token ->
            mutableMapOf<String, Any?>(
                "to" to token,
                "title" to title,
                "body" to body,
                "sound" to "default"
            ).apply {
                if (data != null) {
                    put("data", objectMapper.readValue(data, Map::class.java))
                }
            }
        }

        logger.debug("Sending Expo push notification: tokens={}, title={}", tokens.size, title)

        return webClient.post()
            .bodyValue(messages)
            .retrieve()
            .bodyToMono(ExpoPushResponse::class.java)
            .map { response ->
                val successCount = response.data?.count { it.status == "ok" } ?: 0
                val failedTokens = response.data
                    ?.filter { it.status == "error" }
                    ?.mapNotNull { it.details?.get("expoPushToken") as? String }
                    ?: emptyList()

                PushSendResult(
                    successCount = successCount,
                    failureCount = tokens.size - successCount,
                    failedTokens = failedTokens
                )
            }
            .doOnSuccess { result ->
                logger.info(
                    "Expo push notification sent: success={}, failure={}",
                    result.successCount,
                    result.failureCount
                )
            }
            .doOnError { error ->
                logger.error("Failed to send Expo push notification", error)
            }
            .onErrorReturn(PushSendResult(successCount = 0, failureCount = tokens.size))
    }
}

/**
 * Expo Push API 응답
 */
data class ExpoPushResponse(
    val data: List<ExpoPushTicket>? = null
)

/**
 * Expo Push 티켓
 */
data class ExpoPushTicket(
    val status: String? = null,
    val id: String? = null,
    val message: String? = null,
    val details: Map<String, Any>? = null
)
