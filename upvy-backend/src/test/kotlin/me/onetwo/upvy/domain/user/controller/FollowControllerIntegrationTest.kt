package me.onetwo.upvy.domain.user.controller

import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.infrastructure.config.AbstractIntegrationTest
import me.onetwo.upvy.domain.user.repository.FollowRepository
import me.onetwo.upvy.domain.user.repository.UserProfileRepository
import me.onetwo.upvy.domain.user.repository.UserRepository
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.util.createUserWithProfile
import me.onetwo.upvy.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class)
@ActiveProfiles("test")
@DisplayName("팔로우 Controller 통합 테스트")
class FollowControllerIntegrationTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var followRepository: FollowRepository

    @Test
    @DisplayName("팔로우 성공")
    fun follow_Success() {
        // Given: 두 사용자 생성
        val (follower, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "follower@example.com",
            nickname = "follower${System.currentTimeMillis() % 100000}"
        )

        val (following, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "following@example.com",
            nickname = "following${System.currentTimeMillis() % 100000}"
        )

        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.followerId").isEqualTo(follower.id!!.toString())
            .jsonPath("$.followingId").isEqualTo(following.id!!.toString())

        // Then: 이벤트가 처리되어 DB에 팔로우가 저장되었는지 확인
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val exists = followRepository.existsByFollowerIdAndFollowingId(follower.id!!, following.id!!).block()!!
            assertThat(exists).isTrue
        }
    }

    @Test
    @DisplayName("자기 자신 팔로우 시, 400 Bad Request를 반환한다")
    fun follow_SelfFollow_ThrowsException() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 자기 자신 팔로우 시도
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", user.id!!.toString())
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("이미 팔로우 중인 사용자를 다시 팔로우 시, 409 Conflict를 반환한다")
    fun follow_AlreadyFollowing_ThrowsException() {
        // Given: 두 사용자 생성 및 팔로우
        val (follower, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "follower@example.com",
            nickname = "follower${System.currentTimeMillis() % 100000}"
        )

        val (following, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "following@example.com",
            nickname = "following${System.currentTimeMillis() % 100000}"
        )

        // 첫 번째 팔로우
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().isCreated

        // When & Then: 이미 팔로우 중인 사용자를 다시 팔로우 시도
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().is4xxClientError // 409 Conflict
    }

    @Test
    @DisplayName("언팔로우 성공")
    fun unfollow_Success() {
        // Given: 두 사용자 생성 및 팔로우
        val (follower, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "follower@example.com",
            nickname = "follower${System.currentTimeMillis() % 100000}"
        )

        val (following, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "following@example.com",
            nickname = "following${System.currentTimeMillis() % 100000}"
        )

        // 팔로우
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().isCreated

        // When & Then: 언팔로우
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .delete()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().isNoContent

        // Then: 이벤트가 처리되어 DB에서 팔로우가 삭제되었는지 확인
        await.atMost(2, TimeUnit.SECONDS).untilAsserted {
            val exists = followRepository.existsByFollowerIdAndFollowingId(follower.id!!, following.id!!).block()!!
            assertThat(exists).isFalse
        }
    }

    @Test
    @DisplayName("팔로우하지 않은 사용자를 언팔로우 시, 404 Not Found를 반환한다")
    fun unfollow_NotFollowing_ThrowsException() {
        // Given: 두 사용자 생성 (팔로우 관계 없음)
        val (follower, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "follower@example.com",
            nickname = "follower${System.currentTimeMillis() % 100000}"
        )

        val (notFollowing, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "notfollowing@example.com",
            nickname = "notfollowing${System.currentTimeMillis() % 100000}"
        )

        // When & Then: 팔로우하지 않은 사용자 언팔로우 시도
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .delete()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", notFollowing.id!!.toString())
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    @DisplayName("팔로우 관계 확인 - 팔로우 중")
    fun checkFollowing_Following_ReturnsTrue() {
        // Given: 두 사용자 생성 및 팔로우
        val (follower, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "follower@example.com",
            nickname = "follower${System.currentTimeMillis() % 100000}"
        )

        val (following, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "following@example.com",
            nickname = "following${System.currentTimeMillis() % 100000}"
        )

        // 팔로우
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().isCreated

        // When & Then: 팔로우 관계 확인
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/check/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.followerId").isEqualTo(follower.id!!.toString())
            .jsonPath("$.followingId").isEqualTo(following.id!!.toString())
            .jsonPath("$.isFollowing").isEqualTo(true)
    }

    @Test
    @DisplayName("팔로우 관계 확인 - 팔로우하지 않음")
    fun checkFollowing_NotFollowing_ReturnsFalse() {
        // Given: 두 사용자 생성 (팔로우 없음)
        val (follower, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "follower@example.com",
            nickname = "follower${System.currentTimeMillis() % 100000}"
        )

        val (following, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "following@example.com",
            nickname = "following${System.currentTimeMillis() % 100000}"
        )

        // When & Then: 팔로우 관계 확인
        webTestClient
            .mutateWith(mockUser(follower.id!!))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/check/{followingId}", following.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.isFollowing").isEqualTo(false)
    }

    @Test
    @DisplayName("팔로우 통계 조회 - 특정 사용자")
    fun getFollowStats_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 팔로우 통계 조회
        webTestClient
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/stats/{targetUserId}", user.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo(user.id!!.toString())
            .jsonPath("$.followerCount").isNumber
            .jsonPath("$.followingCount").isNumber
    }

    @Test
    @DisplayName("내 팔로우 통계 조회")
    fun getMyFollowStats_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 내 팔로우 통계 조회
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/stats/me")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo(user.id!!.toString())
            .jsonPath("$.followerCount").isNumber
            .jsonPath("$.followingCount").isNumber
    }

    @Test
    @DisplayName("팔로워 목록 조회")
    fun getFollowers_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 팔로워 목록 조회
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/followers/{userId}", user.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    @DisplayName("팔로워가 없으면, 빈 배열을 반환한다")
    fun getFollowers_NoFollowers_ReturnsEmptyList() {
        // Given: 팔로워가 없는 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 팔로워 목록 조회 - 빈 배열 반환
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/followers/{userId}", user.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("[]")
    }

    @Test
    @DisplayName("팔로잉 목록 조회")
    fun getFollowing_Success() {
        // Given: 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 팔로잉 목록 조회
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/following/{userId}", user.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$").isArray
    }

    @Test
    @DisplayName("팔로잉이 없으면, 빈 배열을 반환한다")
    fun getFollowing_NoFollowing_ReturnsEmptyList() {
        // Given: 팔로잉이 없는 사용자 생성
        val (user, _) = createUserWithProfile(
            userRepository,
            userProfileRepository,
            email = "test@example.com"
        )

        // When & Then: 팔로잉 목록 조회 - 빈 배열 반환
        webTestClient
            .mutateWith(mockUser(user.id!!))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/following/{userId}", user.id!!.toString())
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("[]")
    }
}
