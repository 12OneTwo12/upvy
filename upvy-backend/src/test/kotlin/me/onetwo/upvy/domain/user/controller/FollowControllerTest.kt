package me.onetwo.upvy.domain.user.controller

import java.time.Instant
import java.util.UUID

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import me.onetwo.upvy.config.TestSecurityConfig
import me.onetwo.upvy.domain.user.dto.UserProfileResponse
import me.onetwo.upvy.domain.user.exception.AlreadyFollowingException
import me.onetwo.upvy.domain.user.exception.CannotFollowSelfException
import me.onetwo.upvy.domain.user.exception.NotFollowingException
import me.onetwo.upvy.domain.user.model.Follow
import me.onetwo.upvy.domain.user.service.FollowService
import me.onetwo.upvy.util.mockUser
import me.onetwo.upvy.infrastructure.common.ApiPaths
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.restdocs.operation.preprocess.Preprocessors.*
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.request.RequestDocumentation.*
import org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import reactor.core.publisher.Flux

/**
 * FollowController 단위 테스트 + Spring Rest Docs
 */
@WebFluxTest(controllers = [FollowController::class])
@Import(TestSecurityConfig::class)
@AutoConfigureRestDocs
@ActiveProfiles("test")
@DisplayName("팔로우 Controller 테스트")
class FollowControllerTest : BaseReactiveTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @MockkBean
    private lateinit var followService: FollowService

    private val testUserId = UUID.randomUUID()
    private val testFollowingId = UUID.randomUUID()

    @Test
    @DisplayName("팔로우 성공")
    fun follow_Success() {
        // Given
        val follow = Follow(
            id = 1L,
            followerId = testUserId,
            followingId = testFollowingId
        )

        every { followService.follow(testUserId, testFollowingId) } returns Mono.just(follow)
        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", testFollowingId)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.followerId").isEqualTo(testUserId.toString())
            .jsonPath("$.followingId").isEqualTo(testFollowingId.toString())
            .consumeWith(
                document(
                    "follow-create",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("followingId").description("팔로우할 사용자 ID (UUID)")
                    ),
                    responseFields(
                        fieldWithPath("id").description("팔로우 관계 ID"),
                        fieldWithPath("followerId").description("팔로우하는 사용자 ID"),
                        fieldWithPath("followingId").description("팔로우받는 사용자 ID"),
                        fieldWithPath("createdAt").description("팔로우 생성일시")
                    )
                )
            )

        verify(exactly = 1) { followService.follow(testUserId, testFollowingId) }
    }

    @Test
    @DisplayName("팔로우 실패 - 자기 자신 팔로우")
    fun follow_SelfFollow_ThrowsException() {
        // Given
        every { followService.follow(testUserId, testUserId) } throws CannotFollowSelfException()

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", testUserId)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("팔로우 실패 - 이미 팔로우 중")
    fun follow_AlreadyFollowing_ThrowsException() {
        // Given
        every {
            followService.follow(testUserId, testFollowingId)
        } throws AlreadyFollowingException(testFollowingId)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .post()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", testFollowingId)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("언팔로우 성공")
    fun unfollow_Success() {
        // Given
        every { followService.unfollow(testUserId, testFollowingId) } returns Mono.empty()

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .delete()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", testFollowingId)
            .exchange()
            .expectStatus().isNoContent
            .expectBody()
            .consumeWith(
                document(
                    "follow-delete",
                    pathParameters(
                        parameterWithName("followingId").description("언팔로우할 사용자 ID (UUID)")
                    )
                )
            )

        verify(exactly = 1) { followService.unfollow(testUserId, testFollowingId) }
    }

    @Test
    @DisplayName("언팔로우 실패 - 팔로우하지 않음")
    fun unfollow_NotFollowing_ThrowsException() {
        // Given
        every {
            followService.unfollow(testUserId, testFollowingId)
        } throws NotFollowingException(testFollowingId)

        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .delete()
            .uri("${ApiPaths.API_V1_FOLLOWS}/{followingId}", testFollowingId)
            .exchange()
            .expectStatus().is4xxClientError
    }

    @Test
    @DisplayName("팔로우 관계 확인 - 팔로우 중")
    fun checkFollowing_Following_ReturnsTrue() {
        // Given
        every { followService.isFollowing(testUserId, testFollowingId) } returns Mono.just(true)
        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/check/{followingId}", testFollowingId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.followerId").isEqualTo(testUserId.toString())
            .jsonPath("$.followingId").isEqualTo(testFollowingId.toString())
            .jsonPath("$.isFollowing").isEqualTo(true)
            .consumeWith(
                document(
                    "follow-check",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("followingId").description("확인할 사용자 ID (UUID)")
                    ),
                    responseFields(
                        fieldWithPath("followerId").description("팔로우하는 사용자 ID"),
                        fieldWithPath("followingId").description("팔로우 대상 사용자 ID"),
                        fieldWithPath("isFollowing").description("팔로우 여부 (true: 팔로우 중, false: 팔로우 안함)")
                    )
                )
            )
    }

    @Test
    @DisplayName("팔로우 관계 확인 - 팔로우하지 않음")
    fun checkFollowing_NotFollowing_ReturnsFalse() {
        // Given
        every { followService.isFollowing(testUserId, testFollowingId) } returns Mono.just(false)
        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/check/{followingId}", testFollowingId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.isFollowing").isEqualTo(false)
    }

    @Test
    @DisplayName("팔로우 통계 조회 - 특정 사용자")
    fun getFollowStats_Success() {
        // Given
        val targetUserId = UUID.randomUUID()
        every { followService.getFollowerCount(targetUserId) } returns Mono.just(100)
        every { followService.getFollowingCount(targetUserId) } returns Mono.just(50)
        // When & Then
        webTestClient.get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/stats/{targetUserId}", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo(targetUserId.toString())
            .jsonPath("$.followerCount").isEqualTo(100)
            .jsonPath("$.followingCount").isEqualTo(50)
            .consumeWith(
                document(
                    "follow-stats-get",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("targetUserId").description("조회할 사용자 ID (UUID)")
                    ),
                    responseFields(
                        fieldWithPath("userId").description("사용자 ID"),
                        fieldWithPath("followerCount").description("팔로워 수"),
                        fieldWithPath("followingCount").description("팔로잉 수")
                    )
                )
            )
    }

    @Test
    @DisplayName("내 팔로우 통계 조회")
    fun getMyFollowStats_Success() {
        // Given
        every { followService.getFollowerCount(testUserId) } returns Mono.just(25)
        every { followService.getFollowingCount(testUserId) } returns Mono.just(30)
        // When & Then
        webTestClient
            .mutateWith(mockUser(testUserId))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/stats/me")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.userId").isEqualTo(testUserId.toString())
            .jsonPath("$.followerCount").isEqualTo(25)
            .jsonPath("$.followingCount").isEqualTo(30)
            .consumeWith(
                document(
                    "follow-stats-get-me",
                    preprocessResponse(prettyPrint()),
                    responseFields(
                        fieldWithPath("userId").description("사용자 ID"),
                        fieldWithPath("followerCount").description("팔로워 수"),
                        fieldWithPath("followingCount").description("팔로잉 수")
                    )
                )
            )
    }

    @Test
    @DisplayName("팔로워 목록 조회")
    fun getFollowers_Success() {
        // Given: 팔로워 목록
        val targetUserId = UUID.randomUUID()
        val follower1 = UserProfileResponse(
            id = 1L,
            userId = UUID.randomUUID(),
            nickname = "follower1",
            profileImageUrl = "https://example.com/profile1.jpg",
            bio = "follower1 bio",
            followerCount = 10,
            followingCount = 5,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val follower2 = UserProfileResponse(
            id = 2L,
            userId = UUID.randomUUID(),
            nickname = "follower2",
            profileImageUrl = null,
            bio = null,
            followerCount = 20,
            followingCount = 15,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every { followService.getFollowers(targetUserId) } returns Flux.fromIterable(listOf(follower1, follower2))
        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(UUID.randomUUID()))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/followers/{userId}", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].userId").isEqualTo(follower1.userId.toString())
            .jsonPath("$[0].nickname").isEqualTo("follower1")
            .jsonPath("$[0].profileImageUrl").isEqualTo("https://example.com/profile1.jpg")
            .jsonPath("$[0].followerCount").isEqualTo(10)
            .jsonPath("$[1].userId").isEqualTo(follower2.userId.toString())
            .jsonPath("$[1].nickname").isEqualTo("follower2")
            .consumeWith(
                document(
                    "follow-followers-list",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("userId").description("조회할 사용자 ID (UUID)")
                    ),
                    responseFields(
                        fieldWithPath("[].id").description("프로필 ID"),
                        fieldWithPath("[].userId").description("사용자 ID"),
                        fieldWithPath("[].nickname").description("닉네임"),
                        fieldWithPath("[].profileImageUrl").description("프로필 이미지 URL").optional(),
                        fieldWithPath("[].bio").description("자기소개").optional(),
                        fieldWithPath("[].followerCount").description("팔로워 수"),
                        fieldWithPath("[].followingCount").description("팔로잉 수"),
                        fieldWithPath("[].createdAt").description("프로필 생성일시"),
                        fieldWithPath("[].updatedAt").description("프로필 수정일시")
                    )
                )
            )

        verify(exactly = 1) { followService.getFollowers(targetUserId) }
    }

    @Test
    @DisplayName("팔로워 목록 조회 - 팔로워가 없는 경우")
    fun getFollowers_NoFollowers_ReturnsEmptyList() {
        // Given: 팔로워가 없음
        val targetUserId = UUID.randomUUID()
        every { followService.getFollowers(targetUserId) } returns Flux.empty()
        // When & Then: 빈 배열 반환
        webTestClient
            .mutateWith(mockUser(UUID.randomUUID()))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/followers/{userId}", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("[]")

        verify(exactly = 1) { followService.getFollowers(targetUserId) }
    }

    @Test
    @DisplayName("팔로잉 목록 조회")
    fun getFollowing_Success() {
        // Given: 팔로잉 목록
        val targetUserId = UUID.randomUUID()
        val following1 = UserProfileResponse(
            id = 3L,
            userId = UUID.randomUUID(),
            nickname = "following1",
            profileImageUrl = "https://example.com/profile3.jpg",
            bio = "following1 bio",
            followerCount = 100,
            followingCount = 50,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val following2 = UserProfileResponse(
            id = 4L,
            userId = UUID.randomUUID(),
            nickname = "following2",
            profileImageUrl = null,
            bio = "Hello",
            followerCount = 200,
            followingCount = 150,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        every { followService.getFollowing(targetUserId) } returns Flux.fromIterable(listOf(following1, following2))
        // When & Then: API 호출 및 검증
        webTestClient
            .mutateWith(mockUser(UUID.randomUUID()))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/following/{userId}", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].userId").isEqualTo(following1.userId.toString())
            .jsonPath("$[0].nickname").isEqualTo("following1")
            .jsonPath("$[0].profileImageUrl").isEqualTo("https://example.com/profile3.jpg")
            .jsonPath("$[0].followerCount").isEqualTo(100)
            .jsonPath("$[1].userId").isEqualTo(following2.userId.toString())
            .jsonPath("$[1].nickname").isEqualTo("following2")
            .consumeWith(
                document(
                    "follow-following-list",
                    preprocessResponse(prettyPrint()),
                    pathParameters(
                        parameterWithName("userId").description("조회할 사용자 ID (UUID)")
                    ),
                    responseFields(
                        fieldWithPath("[].id").description("프로필 ID"),
                        fieldWithPath("[].userId").description("사용자 ID"),
                        fieldWithPath("[].nickname").description("닉네임"),
                        fieldWithPath("[].profileImageUrl").description("프로필 이미지 URL").optional(),
                        fieldWithPath("[].bio").description("자기소개").optional(),
                        fieldWithPath("[].followerCount").description("팔로워 수"),
                        fieldWithPath("[].followingCount").description("팔로잉 수"),
                        fieldWithPath("[].createdAt").description("프로필 생성일시"),
                        fieldWithPath("[].updatedAt").description("프로필 수정일시")
                    )
                )
            )

        verify(exactly = 1) { followService.getFollowing(targetUserId) }
    }

    @Test
    @DisplayName("팔로잉 목록 조회 - 팔로잉이 없는 경우")
    fun getFollowing_NoFollowing_ReturnsEmptyList() {
        // Given: 팔로잉이 없음
        val targetUserId = UUID.randomUUID()
        every { followService.getFollowing(targetUserId) } returns Flux.empty()
        // When & Then: 빈 배열 반환
        webTestClient
            .mutateWith(mockUser(UUID.randomUUID()))
            .get()
            .uri("${ApiPaths.API_V1_FOLLOWS}/following/{userId}", targetUserId)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("[]")

        verify(exactly = 1) { followService.getFollowing(targetUserId) }
    }
}
