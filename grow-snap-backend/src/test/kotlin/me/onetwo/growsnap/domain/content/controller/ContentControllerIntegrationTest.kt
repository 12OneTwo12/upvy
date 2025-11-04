package me.onetwo.growsnap.domain.content.controller

import io.mockk.every
import io.mockk.mockk
import me.onetwo.growsnap.config.TestSecurityConfig
import me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentUpdateRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentInteraction
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentPhoto
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.repository.ContentPhotoRepository
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import me.onetwo.growsnap.domain.user.repository.UserRepository
import me.onetwo.growsnap.domain.user.repository.UserProfileRepository
import me.onetwo.growsnap.infrastructure.common.ApiPaths
import me.onetwo.growsnap.infrastructure.config.AbstractIntegrationTest
import me.onetwo.growsnap.util.createContent
import me.onetwo.growsnap.util.createUserWithProfile
import me.onetwo.growsnap.util.mockUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import java.util.UUID
import java.util.function.Consumer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(TestSecurityConfig::class, ContentControllerIntegrationTest.TestConfig::class)
@ActiveProfiles("test")
@DisplayName("콘텐츠 Controller 통합 테스트")
class ContentControllerIntegrationTest : AbstractIntegrationTest() {

    @TestConfiguration
    class TestConfig {
        /**
         * 테스트용 S3Client Mock
         *
         * S3는 외부 서비스이므로 통합 테스트에서도 mock을 사용합니다.
         */
        @Bean
        @Primary
        fun s3Client(): S3Client {
            val mockClient = mockk<S3Client>(relaxed = true)
            every { mockClient.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } returns HeadObjectResponse.builder().build()
            return mockClient
        }

        /**
         * 테스트용 S3Presigner Mock
         *
         * Presigned URL 생성을 위한 S3Presigner Mock입니다.
         * S3Client와 별도의 Bean이므로 따로 Mock이 필요합니다.
         */
        @Bean
        @Primary
        fun s3Presigner(): software.amazon.awssdk.services.s3.presigner.S3Presigner {
            val mockPresigner = mockk<software.amazon.awssdk.services.s3.presigner.S3Presigner>(relaxed = true)

            // presignPutObject 호출 시 Mock 응답 반환
            every { mockPresigner.presignPutObject(any<software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest>()) } answers {
                val mockPresignedRequest = mockk<software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest>(relaxed = true)
                every { mockPresignedRequest.url() } returns java.net.URL("https://mock-s3-url.example.com/test-upload")
                mockPresignedRequest
            }

            return mockPresigner
        }
    }

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
    private lateinit var contentPhotoRepository: ContentPhotoRepository

    @Nested
    @DisplayName("POST /api/v1/contents/upload-url - Presigned URL 생성")
    inner class GenerateUploadUrl {

        @Test
        @DisplayName("유효한 요청으로 Presigned URL 생성 시, 200과 URL 정보를 반환한다")
        fun generateUploadUrl_WithValidRequest_Returns200AndUrlInfo() {
            // Given: 사용자 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test-presigned-${UUID.randomUUID()}@example.com",
                providerId = "google-presigned-${UUID.randomUUID()}"
            )

            val request = ContentUploadUrlRequest(
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_CONTENTS}/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.contentId").isNotEmpty
                .jsonPath("$.uploadUrl").isNotEmpty
                .jsonPath("$.expiresIn").isNumber
        }
    }

    @Nested
    @DisplayName("POST /api/v1/contents - 콘텐츠 생성")
    inner class CreateContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 생성 시, 201과 콘텐츠 정보를 반환한다")
        fun createContent_WithValidRequest_Returns201AndContentInfo() {
            // Given: 사용자 생성 및 업로드 URL 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test-create-${UUID.randomUUID()}@example.com",
                providerId = "google-create-${UUID.randomUUID()}"
            )

            // Presigned URL 생성 (콘텐츠 ID 얻기)
            val uploadUrlRequest = ContentUploadUrlRequest(
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            val contentId = webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri("${ApiPaths.API_V1_CONTENTS}/upload-url")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(uploadUrlRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.contentId").exists()
                .returnResult()
                .responseBody?.let { String(it) }
                ?.let { it.substringAfter("\"contentId\":\"").substringBefore("\"") }

            // 콘텐츠 생성 요청
            val createRequest = ContentCreateRequest(
                contentId = contentId!!,
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test", "video"),
                language = "ko",
                thumbnailUrl = "https://example.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080
            )

            // When & Then: 콘텐츠 생성 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .post()
                .uri(ApiPaths.API_V1_CONTENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequest)
                .exchange()
                .expectStatus().isCreated
                .expectBody()
                .jsonPath("$.id").isEqualTo(contentId)
                .jsonPath("$.creatorId").isEqualTo(user.id!!.toString())
                .jsonPath("$.title").isEqualTo("Test Video")
                .jsonPath("$.contentType").isEqualTo("VIDEO")
                .jsonPath("$.status").isEqualTo("PUBLISHED")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/contents/{contentId} - 콘텐츠 조회")
    inner class GetContent {

        @Test
        @DisplayName("존재하는 콘텐츠 조회 시, 200과 콘텐츠 정보를 반환한다")
        fun getContent_WhenContentExists_Returns200AndContentInfo() {
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

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(content.id!!.toString())
                .jsonPath("$.creatorId").isEqualTo(user.id!!.toString())
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 조회 시, 404를 반환한다")
        fun getContent_WhenContentNotExists_Returns404() {
            // Given: 존재하지 않는 contentId
            val nonExistentId = UUID.randomUUID()

            // When & Then: API 호출 및 검증
            webTestClient
                .get()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", nonExistentId.toString())
                .exchange()
                .expectStatus().isNotFound
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/contents/{contentId} - 콘텐츠 수정")
    inner class UpdateContent {

        @Test
        @DisplayName("PHOTO 타입 콘텐츠의 사진 목록 수정 시, 200과 수정된 콘텐츠를 반환한다")
        fun updateContent_WithPhotoUrls_Returns200AndUpdatedContent() {
            // Given: 사용자와 PHOTO 타입 콘텐츠 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            // PHOTO 타입 콘텐츠 생성
            val photoContent = Content(
                id = java.util.UUID.randomUUID(),
                creatorId = user.id!!,
                contentType = ContentType.PHOTO,
                url = "https://example.com/photo1.jpg",
                thumbnailUrl = "https://example.com/photo-thumbnail.jpg",
                duration = null,
                width = 1080,
                height = 1080,
                status = ContentStatus.PUBLISHED
            )
            val savedContent = contentRepository.save(photoContent).block()!!

            // ContentMetadata 저장
            contentRepository.saveMetadata(
                ContentMetadata(
                    contentId = savedContent.id!!,
                    title = "Original Title",
                    description = "Original Description",
                    category = Category.ART,
                    tags = listOf("original"),
                    language = "ko"
                )
            ).block()

            // ContentPhoto 저장
            contentPhotoRepository.save(
                ContentPhoto(
                    contentId = savedContent.id!!,
                    photoUrl = "https://example.com/photo1.jpg",
                    displayOrder = 0,
                    width = 1080,
                    height = 1080
                )
            ).then().block()

            // ContentInteraction 초기화
            contentInteractionRepository.create(
                ContentInteraction(
                    contentId = savedContent.id!!,
                    likeCount = 0,
                    commentCount = 0,
                    shareCount = 0,
                    saveCount = 0,
                    viewCount = 0
                )
            ).block()

            // 수정 요청
            val updateRequest = ContentUpdateRequest(
                title = "Updated Title",
                description = "Updated Description",
                category = Category.HEALTH,
                photoUrls = listOf(
                    "https://example.com/new1.jpg",
                    "https://example.com/new2.jpg"
                )
            )

            // When & Then: 콘텐츠 수정 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .patch()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", savedContent.id!!.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateRequest)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo(savedContent.id!!.toString())
                .jsonPath("$.title").isEqualTo("Updated Title")
                .jsonPath("$.description").isEqualTo("Updated Description")
                .jsonPath("$.contentType").isEqualTo("PHOTO")
                .jsonPath("$.photoUrls").isArray
                .jsonPath("$.photoUrls.length()").isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/contents/{contentId} - 콘텐츠 삭제")
    inner class DeleteContent {

        @Test
        @DisplayName("본인이 작성한 콘텐츠 삭제 시, 204를 반환한다")
        fun deleteContent_WhenOwner_Returns204() {
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

            // When & Then: API 호출 및 검증
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isNoContent

            // Then: 소프트 삭제 확인 (findById는 삭제된 콘텐츠를 반환하지 않음)
            val deletedContent = contentRepository.findById(content.id!!).block()
            assertThat(deletedContent).isNull()
        }

        @Test
        @DisplayName("다른 사용자의 콘텐츠 삭제 시, 403을 반환한다")
        fun deleteContent_WhenNotOwner_Returns403() {
            // Given: 두 명의 사용자 생성
            val (contentOwner, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "owner@example.com",
                providerId = "google-owner"
            )

            val (otherUser, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "other@example.com",
                providerId = "google-other"
            )

            // 첫 번째 사용자가 콘텐츠 생성
            val content = createContent(
                contentRepository,
                creatorId = contentOwner.id!!,
                contentInteractionRepository = contentInteractionRepository
            )

            // When & Then: 다른 사용자가 콘텐츠 삭제 시도
            webTestClient
                .mutateWith(mockUser(otherUser.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", content.id!!.toString())
                .exchange()
                .expectStatus().isForbidden
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 삭제 시, 404를 반환한다")
        fun deleteContent_WhenContentNotExists_Returns404() {
            // Given: 사용자만 생성
            val (user, _) = createUserWithProfile(
                userRepository,
                userProfileRepository,
                email = "test@example.com",
                providerId = "google-123"
            )

            val nonExistentContentId = UUID.randomUUID()

            // When & Then: 존재하지 않는 콘텐츠 삭제 시도
            webTestClient
                .mutateWith(mockUser(user.id!!))
                .delete()
                .uri("${ApiPaths.API_V1_CONTENTS}/{contentId}", nonExistentContentId.toString())
                .exchange()
                .expectStatus().isNotFound
        }
    }
}
