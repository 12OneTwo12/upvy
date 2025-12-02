package me.onetwo.growsnap.infrastructure.security.util

import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * Principal 확장 함수
 *
 * Principal에서 사용자 ID를 추출하는 유틸리티 함수들을 제공합니다.
 */

private val logger = LoggerFactory.getLogger("PrincipalExtensions")

/**
 * Principal에서 UUID 형식의 사용자 ID를 추출합니다.
 *
 * JWT 토큰의 subject(name)를 UUID로 변환하여 반환합니다.
 *
 * @return 사용자 ID를 담은 Mono<UUID>
 * @throws IllegalArgumentException 토큰의 subject가 UUID 형식이 아닌 경우 (오래된 토큰)
 */
fun Mono<Principal>.toUserId(): Mono<UUID> {
    return this.map { principal ->
        logger.debug("Principal type: ${principal.javaClass.name}, name: ${principal.name}")
        try {
            UUID.fromString(principal.name)
        } catch (e: IllegalArgumentException) {
            logger.error("Invalid token: subject is not a valid UUID. This may be an old token. subject: ${principal.name}")
            throw IllegalArgumentException(
                "Invalid authentication token. Please log in again to get a new token.",
                e
            )
        }
    }
}

/**
 * Principal에서 UUID 형식의 사용자 ID를 추출합니다. (Optional)
 *
 * JWT 토큰이 없거나 subject가 UUID 형식이 아닌 경우 null을 반환합니다.
 * 선택적 인증이 필요한 API에서 사용됩니다.
 *
 * @return 사용자 ID를 담은 Mono<UUID?> (인증되지 않은 경우 null)
 */
fun Mono<Principal>.toUserIdOrNull(): Mono<UUID?> {
    return this
        .map { principal ->
            logger.debug("Principal type: ${principal.javaClass.name}, name: ${principal.name}")
            try {
                UUID.fromString(principal.name)
            } catch (e: IllegalArgumentException) {
                logger.debug("Invalid UUID format in principal: ${principal.name}")
                logger.trace("UUID parsing exception", e) // 예외 스택 트레이스는 trace 레벨로 로깅
                null
            }
        }
        .switchIfEmpty(Mono.fromSupplier { null })
}
