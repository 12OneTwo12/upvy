package me.onetwo.upvy.infrastructure.redis

import me.onetwo.upvy.infrastructure.security.jwt.JwtProperties
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.UUID

/**
 * Refresh Token Redis Repository
 *
 * Refresh Token을 Redis에 저장하고 관리합니다.
 * Key 형식: "refresh_token:{userId}"
 *
 * Reactive Redis 연산을 사용하여 비동기 처리를 지원합니다.
 *
 * @property reactiveRedisTemplate Reactive Redis 템플릿
 * @property jwtProperties JWT 설정
 */
@Repository
class RefreshTokenRepository(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val jwtProperties: JwtProperties
) {
    companion object {
        private const val KEY_PREFIX = "refresh_token:"
    }

    /**
     * Refresh Token 저장
     *
     * @param userId 사용자 ID
     * @param refreshToken Refresh Token
     * @return 저장 성공 여부
     */
    fun save(userId: UUID, refreshToken: String): Mono<Boolean> {
        val key = getKey(userId)
        val ttl = Duration.ofMillis(jwtProperties.refreshTokenExpiration)

        return reactiveRedisTemplate.opsForValue()
            .set(key, refreshToken, ttl)
    }

    /**
     * Refresh Token 조회
     *
     * @param userId 사용자 ID
     * @return Refresh Token (존재하지 않으면 empty Mono)
     */
    fun findByUserId(userId: UUID): Mono<String> {
        val key = getKey(userId)
        return reactiveRedisTemplate.opsForValue()
            .get(key)
    }

    /**
     * Refresh Token 삭제
     *
     * @param userId 사용자 ID
     * @return 삭제 성공 여부
     */
    fun deleteByUserId(userId: UUID): Mono<Boolean> {
        val key = getKey(userId)
        return reactiveRedisTemplate.delete(key)
            .map { it > 0 }
    }

    /**
     * Refresh Token 존재 여부 확인
     *
     * @param userId 사용자 ID
     * @return 존재하면 true, 그렇지 않으면 false
     */
    fun existsByUserId(userId: UUID): Mono<Boolean> {
        val key = getKey(userId)
        return reactiveRedisTemplate.hasKey(key)
    }

    /**
     * Redis Key 생성
     *
     * @param userId 사용자 ID
     * @return Redis Key
     */
    private fun getKey(userId: UUID): String {
        return "$KEY_PREFIX$userId"
    }
}
