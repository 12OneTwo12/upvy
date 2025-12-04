package me.onetwo.growsnap.infrastructure.logging

import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.http.server.reactive.ServerHttpRequestDecorator
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.http.server.reactive.ServerHttpResponseDecorator
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebExchangeDecorator
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Global Request/Response Logging Filter for WebFlux
 *
 * Logs:
 * - Request: Method, URI, Headers, Body
 * - Response: Status, Headers, Body, Execution Time
 *
 * Features:
 * - Request ID tracking (X-Request-ID)
 * - Sensitive data masking
 * - Body content truncation
 */
@Component
class GlobalRequestResponseLoggingFilter : WebFilter {

    @Suppress("ObjectPropertyNaming")
    companion object {
        private val logger = LoggerFactory.getLogger(GlobalRequestResponseLoggingFilter::class.java)
        private const val REQUEST_ID_HEADER = "X-Request-ID"
        private const val MAX_BODY_SIZE = 1024 * 4 // 4KB truncation
        private val EXCLUDED_PATHS = listOf("/actuator", "/favicon.ico")
        private val SENSITIVE_HEADERS = listOf("authorization", "cookie", "x-auth-token")
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.uri.path
        if (EXCLUDED_PATHS.any { path.startsWith(it) }) {
            return chain.filter(exchange)
        }

        val startTime = System.currentTimeMillis()
        val requestId = exchange.request.headers.getFirst(REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()

        // 1. Wrap Request & Response
        val loggingExchange = LoggingWebExchange(exchange, requestId)

        // 2. Log Request (Initial)
        logRequest(loggingExchange.request, requestId)

        // 3. Chain & Log Response (on completion)
        return chain.filter(loggingExchange)
            .doOnSuccess {
                logResponse(loggingExchange.response, requestId, startTime)
            }
            .doOnError { error ->
                logger.error("[{}] Request failed: {}", requestId, error.message)
                logResponse(loggingExchange.response, requestId, startTime) // Log response even on error (status might be set)
            }
    }

    private fun logRequest(request: ServerHttpRequest, requestId: String) {
        val method = request.method
        val uri = request.uri
        val queryParams = request.queryParams

        val headers = formatHeaders(request.headers)

        logger.info(
            "[{}] REQUEST | Method: {} | URI: {} | Params: {} | Headers: {}",
            requestId, method, uri, queryParams, headers
        )
    }

    private fun logResponse(response: ServerHttpResponse, requestId: String, startTime: Long) {
        val duration = System.currentTimeMillis() - startTime
        val status = response.statusCode

        // Note: Response headers might not be fully populated yet in doOnSuccess for WebFlux in some cases,
        // but typically they are available before commit.
        // We rely on the Decorator to capture the body.
        // This log line serves as a summary.

        logger.info(
            "[{}] RESPONSE | Status: {} | Duration: {}ms",
            requestId, status, duration
        )
    }

    private fun formatHeaders(headers: HttpHeaders): String {
        return headers.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            if (SENSITIVE_HEADERS.contains(key.lowercase())) {
                "$key=[***]"
            } else {
                "$key=$value"
            }
        }
    }

    // --- Inner Classes for Decoration ---

    private inner class LoggingWebExchange(
        delegate: ServerWebExchange,
        private val requestId: String
    ) : ServerWebExchangeDecorator(delegate) {

        private val requestDecorator = LoggingRequestDecorator(delegate.request, requestId)
        private val responseDecorator = LoggingResponseDecorator(delegate.response, requestId)

        override fun getRequest(): ServerHttpRequest = requestDecorator
        override fun getResponse(): ServerHttpResponse = responseDecorator
    }

    private inner class LoggingRequestDecorator(
        delegate: ServerHttpRequest,
        private val requestId: String
    ) : ServerHttpRequestDecorator(delegate) {

        private var bytesLogged = 0

        override fun getBody(): Flux<DataBuffer> {
            return super.getBody().map { buffer ->
                logBody(buffer, "REQUEST")
                buffer
            }
        }

        private fun logBody(buffer: DataBuffer, type: String) {
            if (bytesLogged >= MAX_BODY_SIZE) return

            // Only log textual content
            val mediaType = headers.contentType
            if (isTextCompatible(mediaType)) {
                val content = extractContent(buffer, MAX_BODY_SIZE - bytesLogged)
                if (content.isNotEmpty()) {
                    logger.info("[{}] {} BODY (chunk): {}", requestId, type, maskSensitiveData(content))
                    bytesLogged += content.toByteArray().size
                }
            }
        }
    }

    private inner class LoggingResponseDecorator(
        delegate: ServerHttpResponse,
        private val requestId: String
    ) : ServerHttpResponseDecorator(delegate) {

        private var bytesLogged = 0

        override fun writeWith(body: Publisher<out DataBuffer>): Mono<Void> {
            return super.writeWith(
                Flux.from(body).map { buffer ->
                    logBody(buffer, "RESPONSE")
                    buffer
                }
            )
        }

        private fun logBody(buffer: DataBuffer, type: String) {
            if (bytesLogged >= MAX_BODY_SIZE) return

            val mediaType = headers.contentType
            if (isTextCompatible(mediaType)) {
                val content = extractContent(buffer, MAX_BODY_SIZE - bytesLogged)
                if (content.isNotEmpty()) {
                    logger.info("[{}] {} BODY (chunk): {}", requestId, type, content)
                    bytesLogged += content.toByteArray().size
                }
            }
        }
    }

    // --- Utilities ---

    private fun isTextCompatible(mediaType: MediaType?): Boolean {
        if (mediaType == null) return false
        return mediaType.type == "text" ||
                mediaType.subtype.contains("json") ||
                mediaType.subtype.contains("xml") ||
                mediaType.subtype.contains("html")
    }

    @Suppress("TooGenericExceptionCaught")
    private fun extractContent(buffer: DataBuffer, limit: Int): String {
        try {
            val sb = StringBuilder()
            var count = 0

            // Use readableByteBuffers() to access content safely without affecting the buffer's read position.
            // This avoids deprecated slice() methods and is compatible with Spring Framework 6+.
            buffer.readableByteBuffers().forEach { byteBuffer ->
                if (count >= limit) return@forEach

                // Duplicate the ByteBuffer to read safely
                val view = byteBuffer.duplicate()
                val length = Math.min(view.remaining(), limit - count)
                
                if (length > 0) {
                    val bytes = ByteArray(length)
                    view.get(bytes)
                    sb.append(String(bytes, StandardCharsets.UTF_8))
                    count += length
                }
            }
            
            return sb.toString()
        } catch (e: Exception) {
            return "[Error reading body: ${e.message}]"
        }
    }

    private fun maskSensitiveData(content: String): String {
        // Simple regex masking for common sensitive fields in JSON
        var masked = content
        val sensitiveFields = listOf("password", "token", "accessToken", "refreshToken", "secret")
        
        sensitiveFields.forEach { field ->
            val regex = """"$field"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
            masked = regex.replace(masked, """"$field":"***"""")
        }
        return masked
    }
}
