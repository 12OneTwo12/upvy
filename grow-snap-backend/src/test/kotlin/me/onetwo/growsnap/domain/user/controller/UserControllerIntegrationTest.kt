package me.onetwo.growsnap.domain.user.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.user.model.OAuthProvider
import me.onetwo.growsnap.domain.user.model.User
import me.onetwo.growsnap.domain.user.model.UserRole
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
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
@Import(TestSecurityConfig::class)
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

        // Then: 소프트 삭제 확인 (비동기 처리 대기)
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val deletedUser = userRepository.findById(user.id!!)
            assertThat(deletedUser?.deletedAt).isNotNull
        }
    }
}
