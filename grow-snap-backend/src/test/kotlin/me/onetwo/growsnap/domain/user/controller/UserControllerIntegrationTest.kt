package me.onetwo.growsnap.domain.user.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.infrastructure.config.TestRedisConfig
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class, TestRedisConfig::class)
@ActiveProfiles("test")
@DisplayName("사용자 Controller 통합 테스트")
class UserControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Test
    @DisplayName("내 정보 조회 성공")
    fun getMe_Success() {
        // Given: 사용자와 프로필 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_USERS}/me")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(user.id!!.toString())
            .jsonPath("$.email").isEqualTo("test@example.com")
            .jsonPath("$.provider").isEqualTo("GOOGLE")
            .jsonPath("$.role").isEqualTo("USER")
    }

    @Test
    @DisplayName("사용자 ID로 조회 성공")
    fun getUserById_Success() {
        // Given: 사용자와 프로필 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_USERS}/{targetUserId}", user.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(user.id!!.toString())
            .jsonPath("$.email").isEqualTo("test@example.com")
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시, 404 Not Found를 반환한다")
    fun getUserById_NotFound() {
        // Given: 존재하지 않는 사용자 ID
        val nonExistentUserId = UUID.randomUUID()

        // When & Then: 존재하지 않는 사용자 조회 시도
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_USERS}/{targetUserId}", nonExistentUserId.toString())
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    @DisplayName("회원 탈퇴 성공")
    fun withdrawMe_Success() {
        // Given: 사용자와 프로필 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .delete()
            .uri("${ApiPaths.API_V1_USERS}/me")
            .exchange()
            .expectStatus().isNoContent

        // Then: 소프트 삭제 확인 (트랜잭션 완료 대기)
        // Note: 이 테스트는 API 응답(204)만 검증하고, 실제 soft delete는
        // 비동기 처리 및 트랜잭션 타이밍 이슈로 인해 검증하지 않음
        // 실제 soft delete 로직은 UserServiceImplTest에서 검증됨
    }
}
