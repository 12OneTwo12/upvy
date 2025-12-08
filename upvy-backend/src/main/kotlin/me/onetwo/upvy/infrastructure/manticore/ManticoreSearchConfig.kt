package me.onetwo.upvy.infrastructure.manticore

import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Manticore Search 설정
 *
 * Manticore Search HTTP API와 통신하기 위한 WebClient를 구성합니다.
 *
 * ## WebClient 설정
 * - Base URL: Manticore Search HTTP API URL (기본값: http://localhost:9308)
 * - Timeout: 연결 타임아웃, 읽기/쓰기 타임아웃 설정
 * - Reactive: Non-blocking Reactor Netty HTTP Client
 *
 * ## Manticore Search HTTP API
 * - POST /search: Full-text search
 * - POST /insert: Document insert
 * - POST /update: Document update
 * - POST /delete: Document delete
 * - POST /replace: Document replace (upsert)
 */
@Configuration
@EnableConfigurationProperties(ManticoreSearchProperties::class)
class ManticoreSearchConfig(
    private val properties: ManticoreSearchProperties
) {

    /**
     * Manticore Search WebClient
     *
     * Manticore Search HTTP API와 통신하기 위한 WebClient를 생성합니다.
     *
     * ## Netty 설정
     * - Connection timeout: 타임아웃 설정
     * - Read/Write timeout: 읽기/쓰기 타임아웃 설정
     * - Response timeout: 응답 타임아웃 설정
     *
     * @return Manticore Search WebClient
     */
    @Bean
    fun manticoreSearchWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.timeout.toInt())
            .responseTimeout(Duration.ofMillis(properties.timeout))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(properties.timeout, TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(properties.timeout, TimeUnit.MILLISECONDS))
            }

        return WebClient.builder()
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }
}
