package me.onetwo.upvy.util

import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.UUID

/**
 * WebTestClient Helper Functions for Testing
 *
 * Spring Security Test의 mockJwt()를 간편하게 사용하기 위한 helper function입니다.
 */

/**
 * Mock JWT 인증을 설정합니다.
 *
 * ### 사용 예시
 * ```kotlin
 * webTestClient
 *     .get()
 *     .uri("/api/v1/users/me")
 *     .with(mockUser(testUserId))  // 간단!
 *     .exchange()
 * ```
 *
 * @param userId 인증된 사용자 ID (UUID)
 * @return Spring Security Test의 mockJwt() configurer
 */
fun mockUser(userId: UUID) = mockJwt().jwt { jwt -> jwt.subject(userId.toString()) }
