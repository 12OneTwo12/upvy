package me.onetwo.growsnap.infrastructure.security.config

import org.springframework.http.HttpMethod

/**
 * 공개 API 경로 관리
 *
 * Spring Security에서 인증 없이 접근 가능한 API 경로를 정의합니다.
 * SecurityConfig와 TestSecurityConfig에서 공통으로 사용합니다.
 */
object PublicApiPaths {

    /**
     * 인증이 필요 없는 공개 엔드포인트
     *
     * OAuth2 로그인, 인증 API 등 모든 HTTP 메서드 허용
     */
    val AUTH_ENDPOINTS = arrayOf(
        "/api/v1/auth/**",
        "/oauth2/**",
        "/login/**",
        "/error"
    )

    /**
     * GET 메서드만 허용하는 조회 전용 공개 API
     *
     * 사용자, 프로필, 팔로우 통계 등 읽기 전용 API
     */
    object GetOnly {
        /**
         * HTTP 메서드
         */
        val METHOD = HttpMethod.GET

        /**
         * 공개 경로 목록
         */
        val PATHS = arrayOf(
            // 사용자 조회
            "/api/v1/users/*",

            // 프로필 조회
            "/api/v1/profiles/*",                   // UUID로 조회
            "/api/v1/profiles/nickname/*",          // 닉네임으로 조회
            "/api/v1/profiles/check/nickname/*",    // 닉네임 중복 확인

            // 팔로우 통계
            "/api/v1/follows/stats/*",

            // 콘텐츠 개별 조회
            "/api/v1/contents/*"
        )
    }
}
