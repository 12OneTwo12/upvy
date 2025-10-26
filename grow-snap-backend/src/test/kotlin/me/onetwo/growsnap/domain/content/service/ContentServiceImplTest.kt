package me.onetwo.growsnap.domain.content.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import java.util.UUID

@ExtendWith(MockKExtension::class)
@DisplayName("콘텐츠 Service 테스트")
class ContentServiceImplTest {

    @MockK
    private lateinit var contentUploadService: ContentUploadService

    @MockK
    private lateinit var contentRepository: ContentRepository

    @InjectMockKs
    private lateinit var contentService: ContentServiceImpl

    @Nested
    @DisplayName("generateUploadUrl - Presigned URL 생성")
    inner class GenerateUploadUrl {

        @Test
        @DisplayName("유효한 요청으로 Presigned URL 생성 시, URL 정보를 반환한다")
        fun generateUploadUrl_WithValidRequest_ReturnsUploadUrl() {
            // Given: 테스트 데이터
            val userId = UUID.randomUUID()
            val request = ContentUploadUrlRequest(
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            val presignedUrlInfo = PresignedUrlInfo(
                contentId = UUID.randomUUID(),
                uploadUrl = "https://s3.amazonaws.com/presigned-url",
                expiresIn = 900
            )

            every {
                contentUploadService.generateUploadUrl(userId, ContentType.VIDEO, "test.mp4", 1000000L)
            } returns Mono.just(presignedUrlInfo)

            // When: 메서드 실행
            val result = contentService.generateUploadUrl(userId, request)

            // Then: 결과 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.contentId).isEqualTo(presignedUrlInfo.contentId.toString())
                    assertThat(response.uploadUrl).isEqualTo(presignedUrlInfo.uploadUrl)
                    assertThat(response.expiresIn).isEqualTo(900)
                }
                .verifyComplete()

            verify(exactly = 1) {
                contentUploadService.generateUploadUrl(userId, ContentType.VIDEO, "test.mp4", 1000000L)
            }
        }
    }

    @Nested
    @DisplayName("createContent - 콘텐츠 생성")
    inner class CreateContent {

        @Test
        @DisplayName("유효한 요청으로 콘텐츠 생성 시, 콘텐츠를 저장하고 응답을 반환한다")
        fun createContent_WithValidRequest_SavesAndReturnsContent() {
            // Given: 테스트 데이터
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val request = ContentCreateRequest(
                contentId = contentId.toString(),
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test", "video"),
                language = "ko",
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080
            )

            val savedContent = Content(
                id = contentId,
                creatorId = userId,
                contentType = ContentType.VIDEO,
                url = "https://s3.amazonaws.com/video.mp4",
                thumbnailUrl = request.thumbnailUrl,
                duration = request.duration,
                width = request.width,
                height = request.height,
                status = ContentStatus.PUBLISHED,
                createdAt = LocalDateTime.now(),
                createdBy = userId,
                updatedAt = LocalDateTime.now(),
                updatedBy = userId
            )

            val savedMetadata = ContentMetadata(
                id = 1L,
                contentId = contentId,
                title = request.title,
                description = request.description,
                category = request.category,
                tags = request.tags,
                language = request.language,
                createdAt = LocalDateTime.now(),
                createdBy = userId,
                updatedAt = LocalDateTime.now(),
                updatedBy = userId
            )

            every { contentRepository.save(any()) } returns savedContent
            every { contentRepository.saveMetadata(any()) } returns savedMetadata

            // When: 메서드 실행
            val result = contentService.createContent(userId, request)

            // Then: 결과 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(contentId.toString())
                    assertThat(response.title).isEqualTo(request.title)
                    assertThat(response.category).isEqualTo(request.category)
                }
                .verifyComplete()

            verify(exactly = 1) { contentRepository.save(any()) }
            verify(exactly = 1) { contentRepository.saveMetadata(any()) }
        }
    }

    @Nested
    @DisplayName("getContent - 콘텐츠 조회")
    inner class GetContent {

        @Test
        @DisplayName("존재하는 콘텐츠 조회 시, 콘텐츠 정보를 반환한다")
        fun getContent_WhenContentExists_ReturnsContent() {
            // Given: 테스트 데이터
            val contentId = UUID.randomUUID()
            val userId = UUID.randomUUID()

            val content = Content(
                id = contentId,
                creatorId = userId,
                contentType = ContentType.VIDEO,
                url = "https://s3.amazonaws.com/video.mp4",
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail.jpg",
                duration = 60,
                width = 1920,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                createdAt = LocalDateTime.now(),
                createdBy = userId,
                updatedAt = LocalDateTime.now(),
                updatedBy = userId
            )

            val metadata = ContentMetadata(
                id = 1L,
                contentId = contentId,
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test"),
                language = "ko",
                createdAt = LocalDateTime.now(),
                createdBy = userId,
                updatedAt = LocalDateTime.now(),
                updatedBy = userId
            )

            every { contentRepository.findById(contentId) } returns content
            every { contentRepository.findMetadataByContentId(contentId) } returns metadata

            // When: 메서드 실행
            val result = contentService.getContent(contentId)

            // Then: 결과 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(contentId.toString())
                    assertThat(response.title).isEqualTo(metadata.title)
                }
                .verifyComplete()

            verify(exactly = 1) { contentRepository.findById(contentId) }
            verify(exactly = 1) { contentRepository.findMetadataByContentId(contentId) }
        }
    }
}
