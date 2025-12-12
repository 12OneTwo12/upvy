package me.onetwo.upvy.domain.user.repository

import me.onetwo.upvy.domain.user.model.OAuthProvider
import me.onetwo.upvy.domain.user.model.UserAuthenticationMethod
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 사용자 인증 수단 저장소 인터페이스
 *
 * 계정 통합 아키텍처에서 사용자의 인증 수단을 관리합니다.
 */
interface UserAuthenticationMethodRepository {

    /**
     * 인증 수단 저장
     *
     * @param authMethod 저장할 인증 수단
     * @return 저장된 인증 수단
     */
    fun save(authMethod: UserAuthenticationMethod): Mono<UserAuthenticationMethod>

    /**
     * 사용자의 모든 인증 수단 조회
     *
     * @param userId 사용자 ID
     * @return 사용자의 인증 수단 목록
     */
    fun findAllByUserId(userId: UUID): Flux<UserAuthenticationMethod>

    /**
     * 사용자의 특정 provider 인증 수단 조회
     *
     * @param userId 사용자 ID
     * @param provider 인증 제공자
     * @return 인증 수단
     */
    fun findByUserIdAndProvider(userId: UUID, provider: OAuthProvider): Mono<UserAuthenticationMethod>

    /**
     * OAuth provider와 provider ID로 인증 수단 조회
     *
     * OAuth 로그인 시 기존 사용자 찾기에 사용됩니다.
     *
     * @param provider OAuth 제공자 (GOOGLE, NAVER, KAKAO 등)
     * @param providerId OAuth 제공자 ID
     * @return 인증 수단
     */
    fun findByProviderAndProviderId(provider: OAuthProvider, providerId: String): Mono<UserAuthenticationMethod>

    /**
     * 검증된 이메일 인증 수단 조회
     *
     * @param userId 사용자 ID
     * @param provider 인증 제공자
     * @param verified 이메일 검증 여부
     * @return 인증 수단
     */
    fun findByUserIdAndProviderAndEmailVerified(
        userId: UUID,
        provider: OAuthProvider,
        verified: Boolean
    ): Mono<UserAuthenticationMethod>

    /**
     * 비밀번호 업데이트
     *
     * @param id 인증 수단 ID
     * @param password BCrypt 암호화된 비밀번호
     * @return 업데이트된 행 수
     */
    fun updatePassword(id: Long, password: String): Mono<Int>

    /**
     * 이메일 검증 상태 업데이트
     *
     * @param id 인증 수단 ID
     * @param verified 이메일 검증 여부
     * @return 업데이트된 행 수
     */
    fun updateEmailVerified(id: Long, verified: Boolean): Mono<Int>

    /**
     * 주 인증 수단 설정
     *
     * 기존 주 인증 수단은 자동으로 isPrimary = false로 변경됩니다.
     *
     * @param userId 사용자 ID
     * @param id 주 인증 수단으로 설정할 ID
     * @return 업데이트된 행 수
     */
    fun setPrimary(userId: UUID, id: Long): Mono<Int>

    /**
     * 인증 수단 삭제 (Soft Delete)
     *
     * @param id 인증 수단 ID
     * @return 삭제된 행 수
     */
    fun softDelete(id: Long): Mono<Int>
}
