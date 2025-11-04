package me.onetwo.growsnap.domain.user.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.infrastructure.config.TestRedisConfig
import me.onetwo.growsnap.domain.user.dto.UpdateProfileRequest
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class, TestRedisConfig::class)
@ActiveProfiles("test")
@DisplayName("사용자 프로필 Controller 통합 테스트")
class UserProfileControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Test
    @DisplayName("내 프로필 조회 성공")
    fun getMyProfile_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com",
            providerId = "google-123"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/me")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo(user.id!!.toString())
            .jsonPath("$.nickname").exists()
            .jsonPath("$.followerCount").isNumber
            .jsonPath("$.followingCount").isNumber
    }

    @Test
    @DisplayName("사용자 ID로 프로필 조회 성공")
    fun getProfileByUserId_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com",
            providerId = "google-123"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/{targetUserId}", user.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo(user.id!!.toString())
            .jsonPath("$.nickname").exists()
    }

    @Test
    @DisplayName("닉네임으로 프로필 조회 성공")
    fun getProfileByNickname_Success() {
        // Given: 사용자 생성
        val (user, profile) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com",
            providerId = "google-123"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/nickname/{nickname}", profile.nickname)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo(profile.nickname)
    }

    @Test
    @DisplayName("존재하지 않는 프로필 조회 시, 404 Not Found를 반환한다")
    fun getProfile_NotFound() {
        // Given: 존재하지 않는 사용자 ID
        val nonExistentUserId = UUID.randomUUID()

        // When & Then: 존재하지 않는 프로필 조회 시도
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/{targetUserId}", nonExistentUserId.toString())
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    @DisplayName("프로필 수정 성공")
    fun updateProfile_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com",
            providerId = "google-123"
        )

        val request = UpdateProfileRequest(
            nickname = "updatednick",
            bio = "수정된 자기소개"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .patch()
            .uri(ApiPaths.API_V1_PROFILES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo("updatednick")
            .jsonPath("$.bio").isEqualTo("수정된 자기소개")

        // Then: DB에 반영되었는지 확인
        val updatedProfile = userProfileRepository.findByUserId(user.id!!).block()!!
        assertThat(updatedProfile?.nickname).isEqualTo("updatednick")
        assertThat(updatedProfile?.bio).isEqualTo("수정된 자기소개")
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 사용 가능")
    fun checkNickname_Available() {
        // Given: 사용되지 않은 닉네임
        val nickname = "availablenick"

        // When & Then: API 호출 및 검증
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/check/nickname/{nickname}", nickname)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo(nickname)
            .jsonPath("$.isDuplicated").isEqualTo(false)
    }

    @Test
    @DisplayName("닉네임 중복 확인 - 중복됨")
    fun checkNickname_Duplicated() {
        // Given: 사용자 생성
        val (user, profile) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com",
            providerId = "google-123"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_PROFILES}/check/nickname/{nickname}", profile.nickname)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.nickname").isEqualTo(profile.nickname)
            .jsonPath("$.isDuplicated").isEqualTo(true)
    }
}
