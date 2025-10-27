package me.onetwo.growsnap.infrastructure.security.util

import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * Principal 확장 함수
 *
 * Principal에서 사용자 ID를 추출하는 유틸리티 함수들을 제공합니다.
 */

/**
 * Principal에서 UUID 형식의 사용자 ID를 추출합니다.
 *
 * JWT 토큰의 subject(name)를 UUID로 변환하여 반환합니다.
 *
 * @return 사용자 ID를 담은 Mono<UUID>
 */
fun Mono<Principal>.toUserId(): Mono<UUID> {
    return this.map { UUID.fromString(it.name) }
}
