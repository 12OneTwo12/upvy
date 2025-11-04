package me.onetwo.growsnap.domain.auth.service

import me.onetwo.growsnap.domain.auth.dto.RefreshTokenResponse
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 인증 서비스 인터페이스
 *
 * JWT 토큰 갱신, 로그아웃 등의 인증 관련 비즈니스 로직을 정의합니다.
 */
interface AuthService {

    /**
     * Access Token 갱신
     *
     * Refresh Token을 검증하고 새로운 Access Token을 발급합니다.
     *
     * @param refreshToken Refresh Token
     * @return RefreshTokenResponse 새로운 Access Token
     * @throws IllegalArgumentException Refresh Token이 유효하지 않은 경우
     */
    fun refreshAccessToken(refreshToken: String): Mono<RefreshTokenResponse>

    /**
     * 로그아웃
     *
     * Redis에 저장된 Refresh Token을 삭제하여 로그아웃 처리합니다.
     *
     * @param refreshToken Refresh Token
     * @throws IllegalArgumentException Refresh Token이 유효하지 않은 경우
     */
    fun logout(refreshToken: String)

    /**
     * 사용자 ID로 Refresh Token 조회
     *
     * @param userId 사용자 ID
     * @return Refresh Token (존재하지 않으면 null)
     */
    fun getRefreshTokenByUserId(userId: UUID): String?
}
