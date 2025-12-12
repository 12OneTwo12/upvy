package me.onetwo.upvy.domain.auth.repository

import me.onetwo.upvy.domain.auth.model.EmailVerificationToken
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 이메일 인증 토큰 Repository 인터페이스
 */
interface EmailVerificationTokenRepository {

    /**
     * 인증 토큰 저장
     *
     * @param token 인증 토큰
     * @return 저장된 인증 토큰 (ID 포함)
     */
    fun save(token: EmailVerificationToken): Mono<EmailVerificationToken>

    /**
     * 토큰 문자열로 인증 토큰 조회
     *
     * @param token 토큰 문자열
     * @return 인증 토큰 (존재하지 않으면 empty)
     */
    fun findByToken(token: String): Mono<EmailVerificationToken>

    /**
     * 사용자 ID로 인증 토큰 조회 (가장 최근 토큰)
     *
     * @param userId 사용자 ID
     * @return 인증 토큰 (존재하지 않으면 empty)
     */
    fun findLatestByUserId(userId: UUID): Mono<EmailVerificationToken>

    /**
     * 인증 토큰 삭제 (Soft Delete)
     *
     * @param id 토큰 ID
     * @return Mono<Void>
     */
    fun softDelete(id: Long): Mono<Void>

    /**
     * 사용자의 모든 인증 토큰 삭제 (Soft Delete)
     *
     * @param userId 사용자 ID
     * @return Mono<Void>
     */
    fun softDeleteAllByUserId(userId: UUID): Mono<Void>
}
