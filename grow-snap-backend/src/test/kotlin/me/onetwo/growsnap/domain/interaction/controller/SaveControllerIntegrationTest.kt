package me.onetwo.growsnap.domain.interaction.controller

import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.infrastructure.config.TestRedisConfig
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.interaction.repository.UserSaveRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
@Import(TestSecurityConfig::class, TestRedisConfig::class)
@ActiveProfiles("test")
@DisplayName("저장 Controller 통합 테스트")
class SaveControllerIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userProfileRepository: UserProfileRepository

    @Autowired
    private lateinit var contentRepository: ContentRepository

    @Autowired
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @Autowired
    private lateinit var userSaveRepository: UserSaveRepository

    @Nested
    @DisplayName("POST /api/v1/contents/{contentId}/save - 콘텐츠 저장")
    inner class SaveContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 저장 시, 200 OK와 저장 응답을 반환한다")
        fun saveContent_WithValidRequest_ReturnsSaveResponse() {
            // Given: 사용자와 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: API 호출 및 검증 (saveCount는 비동기 이벤트 처리 후 업데이트되므로 즉시 확인하지 않음)
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.isSaved").isEqualTo(true)

            // Then: 이벤트가 처리되어 DB에 저장이 기록되고 카운트가 증가했는지 확인
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val exists = userSaveRepository.exists(user.id!!, content.id!!).block()!!
                assertThat(exists).isTrue

                val saveCount = contentInteractionRepository.getSaveCount(content.id!!).block()
                assertThat(saveCount).isEqualTo(1)
            }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId}/save - 콘텐츠 저장 취소")
    inner class UnsaveContent {

        @Test
        @DisplayName("유효한 요청으로 저장 취소 시, 200 OK와 저장 취소 응답을 반환한다")
        fun unsaveContent_WithValidRequest_ReturnsSaveResponse() {
            // Given: 사용자, 콘텐츠 생성 및 저장
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // 저장 추가
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", content.id!!.toString())
                .exchange()
                .expectStatus().isOk

            // When & Then: 저장 취소
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.isSaved").isEqualTo(false)

            // Then: 이벤트가 처리되어 DB에서 저장이 삭제되고 카운트가 감소했는지 확인
            await.atMost(2, TimeUnit.SECONDS).untilAsserted {
                val exists = userSaveRepository.exists(user.id!!, content.id!!).block()!!
                assertThat(exists).isFalse

                val saveCount = contentInteractionRepository.getSaveCount(content.id!!).block()
                assertThat(saveCount).isEqualTo(0)
            }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/saved-contents - 저장한 콘텐츠 목록 조회")
    inner class GetSavedContents {

        @Test
        @DisplayName("저장한 콘텐츠 목록 조회 시, 200 OK와 콘텐츠 목록을 반환한다")
        fun getSavedContents_WithValidRequest_ReturnsSavedContentList() {
            // Given: 사용자와 여러 콘텐츠 생성 및 저장
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content1 = createContent(
                contentRepository,
                creatorId = user.id!!,
                url = "https://example.com/video1.mp4",
                thumbnailUrl = "https://example.com/thumb1.jpg",
                contentInteractionRepository = contentInteractionRepository
            )

            val content2 = createContent(
                contentRepository,
                creatorId = user.id!!,
                url = "https://example.com/video2.mp4",
                thumbnailUrl = "https://example.com/thumb2.jpg",
                duration = 120,
                contentInteractionRepository = contentInteractionRepository
            )

            // 두 콘텐츠 저장
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", content1.id!!.toString())
                .exchange()
                .expectStatus().isOk

            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", content2.id!!.toString())
                .exchange()
                .expectStatus().isOk

            // When & Then: 저장한 콘텐츠 목록 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_USERS}/me/saved-contents")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$").isArray
                .jsonPath("$.length()").isEqualTo(2)
        }

        @Test
        @DisplayName("저장한 콘텐츠가 없으면, 빈 배열을 반환한다")
        fun getSavedContents_WithNoSavedContents_ReturnsEmptyArray() {
            // Given: 사용자만 생성 (저장한 콘텐츠 없음)
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            // When & Then: 빈 배열 반환 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1_USERS}/me/saved-contents")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .json("[]")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId}/save/status - 저장 상태 조회")
    inner class GetSaveStatus {

        @Test
        @DisplayName("저장 상태 조회 시, 사용자가 저장한 경우 true를 반환한다")
        fun getSaveStatus_WhenUserSaved_ReturnsTrue() {
            // Given: 사용자, 콘텐츠 생성 및 저장
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // 저장 추가
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save", content.id!!.toString())
                .exchange()
                .expectStatus().isOk

            // When & Then: 저장 상태 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save/status", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.isSaved").isEqualTo(true)
        }

        @Test
        @DisplayName("저장 상태 조회 시, 사용자가 저장하지 않은 경우 false를 반환한다")
        fun getSaveStatus_WhenUserNotSaved_ReturnsFalse() {
            // Given: 사용자와 콘텐츠 생성 (저장 없음)
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val content = createContent(
                contentRepository,
                creatorId = user.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: 저장 상태 조회
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .get()
                .uri("${ApiPaths.API_V1}/contents/{contentId}/save/status", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.contentId").isEqualTo(content.id!!.toString())
                .jsonPath("$.isSaved").isEqualTo(false)
        }
    }
}
