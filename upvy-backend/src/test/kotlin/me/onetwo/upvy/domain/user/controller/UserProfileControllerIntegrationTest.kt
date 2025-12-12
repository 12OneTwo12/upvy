package me.onetwo.upvy.domain.user.controller

import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.domain.user.dto.CreateProfileRequest
import me.onetwo.upvy.domain.user.dto.UpdateProfileRequest
import me.onetwo.upvy.domain.user.model.User
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.util.createUserWithProfile
import me.onetwo.upvy.util.mockUser
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
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("사용자 프로필 Controller 통합 테스트")
class UserProfileControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Test
    @DisplayName("프로필 생성 성공 - 최초 프로필 생성")
    fun createProfile_Success() {
        // Given: 프로필이 없는 사용자 생성
        val user = User(email = "newuser@example.com")
        val savedUser = userRepository.save(user).block()!!

        val request = CreateProfileRequest(
            nickname = "newuser",
            profileImageUrl = null,
            bio = "안녕하세요!"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(savedUser.id!!))
            .post()
            .uri(ApiPaths.API_V1_PROFILES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.userId").isEqualTo(savedUser.id!!.toString())
            .jsonPath("$.nickname").isEqualTo("newuser")
            .jsonPath("$.bio").isEqualTo("안녕하세요!")
            .jsonPath("$.followerCount").isEqualTo(0)
            .jsonPath("$.followingCount").isEqualTo(0)

        // Then: DB에 생성되었는지 확인
        val createdProfile = userProfileRepository.findByUserId(savedUser.id!!).block()!!
        assertThat(createdProfile.nickname).isEqualTo("newuser")
        assertThat(createdProfile.bio).isEqualTo("안녕하세요!")
    }

    @Test
    @DisplayName("프로필 생성 실패 - 이미 프로필이 존재하는 경우")
    fun createProfile_Fail_AlreadyExists() {
        // Given: 이미 프로필이 있는 사용자
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        val request = CreateProfileRequest(
            nickname = "newuser",
            bio = null
        )

        // When & Then: API 호출 시 409 Conflict
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .post()
            .uri(ApiPaths.API_V1_PROFILES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("프로필 생성 실패 - 닉네임 중복")
    fun createProfile_Fail_DuplicateNickname() {
        // Given: 기존 사용자와 새로운 사용자
        val (existingUser, existingProfile) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "existing@example.com"
        )

        val newUser = User(email = "newuser@example.com")
        val savedNewUser = userRepository.save(newUser).block()!!

        val request = CreateProfileRequest(
            nickname = existingProfile.nickname,  // 기존 닉네임 사용
            bio = null
        )

        // When & Then: API 호출 시 409 Conflict
        webTestClient
            .mutateWith(mockUser(savedNewUser.id!!))
            .post()
            .uri(ApiPaths.API_V1_PROFILES)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("내 프로필 조회 성공")
    fun getMyProfile_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
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
            email = "test@example.com"
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
            email = "test@example.com"
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
            email = "test@example.com"
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
            email = "test@example.com"
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
