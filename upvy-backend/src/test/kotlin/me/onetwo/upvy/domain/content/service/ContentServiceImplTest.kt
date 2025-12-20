package me.onetwo.upvy.domain.content.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.justRun
import io.mockk.verify
import me.onetwo.upvy.domain.content.dto.ContentCreateRequest
import me.onetwo.upvy.domain.content.dto.ContentUpdateRequest
import me.onetwo.upvy.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.upvy.domain.content.exception.FileNotUploadedException
import me.onetwo.upvy.domain.content.exception.UploadSessionNotFoundException
import me.onetwo.upvy.domain.content.exception.UploadSessionUnauthorizedException
import me.onetwo.upvy.domain.content.model.Category
import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentMetadata
import me.onetwo.upvy.domain.content.model.ContentPhoto
import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.model.UploadSession
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.content.repository.ContentPhotoRepository
import me.onetwo.upvy.domain.content.repository.UploadSessionRepository
import me.onetwo.upvy.domain.analytics.service.ContentInteractionService
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.interaction.repository.UserLikeRepository
import me.onetwo.upvy.domain.interaction.repository.UserSaveRepository
import me.onetwo.upvy.domain.tag.service.TagService
import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import me.onetwo.upvy.infrastructure.event.ReactiveEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID
import java.util.function.Consumer

@ExtendWith(MockKExtension::class)
@DisplayName("콘텐츠 Service 테스트")
class ContentServiceImplTest : BaseReactiveTest {

    @MockK
    private lateinit var contentUploadService: ContentUploadService

    @MockK
    private lateinit var contentRepository: ContentRepository

    @MockK
    private lateinit var contentPhotoRepository: ContentPhotoRepository

    @MockK
    private lateinit var uploadSessionRepository: UploadSessionRepository

    @MockK
    private lateinit var s3Client: S3Client

    @MockK
    private lateinit var eventPublisher: ReactiveEventPublisher

    @MockK
    private lateinit var contentInteractionService: ContentInteractionService

    @MockK
    private lateinit var contentInteractionRepository: ContentInteractionRepository

    @MockK
    private lateinit var userLikeRepository: UserLikeRepository

    @MockK
    private lateinit var userSaveRepository: UserSaveRepository

    @MockK
    private lateinit var tagService: TagService

    private lateinit var contentService: ContentServiceImpl

    @BeforeEach
    fun setUp() {
        contentService = ContentServiceImpl(
            contentUploadService = contentUploadService,
            contentRepository = contentRepository,
            contentPhotoRepository = contentPhotoRepository,
            uploadSessionRepository = uploadSessionRepository,
            s3Client = s3Client,
            eventPublisher = eventPublisher,
            contentInteractionService = contentInteractionService,
            contentInteractionRepository = contentInteractionRepository,
            userLikeRepository = userLikeRepository,
            userSaveRepository = userSaveRepository,
            tagService = tagService,
            bucketName = "test-bucket",
            region = "ap-northeast-2"
        )
    }

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
            val s3Key = "contents/VIDEO/$userId/$contentId/test_123456.mp4"

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

            val uploadSession = UploadSession(
                contentId = contentId.toString(),
                userId = userId.toString(),
                s3Key = s3Key,
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            val savedContent = Content(
                id = contentId,
                creatorId = userId,
                contentType = ContentType.VIDEO,
                url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/$s3Key",
                thumbnailUrl = request.thumbnailUrl,
                duration = request.duration,
                width = request.width,
                height = request.height,
                status = ContentStatus.PUBLISHED,
                createdAt = Instant.now(),
                createdBy = userId.toString(),
                updatedAt = Instant.now(),
                updatedBy = userId.toString()
            )

            val savedMetadata = ContentMetadata(
                id = 1L,
                contentId = contentId,
                title = request.title,
                description = request.description,
                category = request.category,
                tags = request.tags,
                language = request.language,
                createdAt = Instant.now(),
                createdBy = userId.toString(),
                updatedAt = Instant.now(),
                updatedBy = userId.toString()
            )

            // Mock Redis: 업로드 세션 조회
            every { uploadSessionRepository.findById(contentId.toString()) } returns Optional.of(uploadSession)

            // Mock S3: 파일 존재 확인
            every { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } returns HeadObjectResponse.builder().build()

            // Mock Repository: 저장
            every { contentRepository.save(any()) } returns Mono.just(savedContent)
            every { contentRepository.saveMetadata(any()) } returns Mono.just(savedMetadata)

            // Mock TagService: 태그 연결
            every { tagService.attachTagsToContent(contentId, request.tags!!, userId.toString()) } returns reactor.core.publisher.Flux.empty()

            // Mock Redis: 세션 삭제
            every { uploadSessionRepository.deleteById(contentId.toString()) } returns Unit

            // Mock ContentInteractionService
            every { contentInteractionService.createContentInteraction(contentId, userId) } returns Mono.empty()

            // Mock Event Publisher
            justRun { eventPublisher.publish(any()) }

            // When: 메서드 실행
            val result = contentService.createContent(userId, request)

            // Then: 결과 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(contentId.toString())
                    assertThat(response.title).isEqualTo(request.title)
                    assertThat(response.category).isEqualTo(request.category)
                    assertThat(response.contentType).isEqualTo(ContentType.VIDEO)
                }
                .verifyComplete()

            verify(exactly = 1) { uploadSessionRepository.findById(contentId.toString()) }
            verify(exactly = 1) { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) }
            verify(exactly = 1) { contentRepository.save(any()) }
            verify(exactly = 1) { contentRepository.saveMetadata(any()) }
            verify(exactly = 1) { uploadSessionRepository.deleteById(contentId.toString()) }
            verify(exactly = 1) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("PHOTO 타입 콘텐츠 생성 시, 사진 목록을 저장하고 응답에 포함한다")
        fun createContent_WithPhotoType_SavesPhotosAndReturnsWithPhotoUrls() {
            // Given: PHOTO 타입 콘텐츠 데이터
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val s3Key = "contents/PHOTO/$userId/$contentId/test_123456.jpg"
            val photoUrls = listOf(
                "https://s3.amazonaws.com/photo1.jpg",
                "https://s3.amazonaws.com/photo2.jpg",
                "https://s3.amazonaws.com/photo3.jpg"
            )

            val request = ContentCreateRequest(
                contentId = contentId.toString(),
                title = "Test Photo Gallery",
                description = "Test Description",
                category = Category.HEALTH,
                tags = listOf("test", "photo"),
                language = "ko",
                thumbnailUrl = "https://s3.amazonaws.com/thumbnail.jpg",
                duration = null,
                width = 1080,
                height = 1080,
                photoUrls = photoUrls
            )

            val uploadSession = UploadSession(
                contentId = contentId.toString(),
                userId = userId.toString(),
                s3Key = s3Key,
                contentType = ContentType.PHOTO,
                fileName = "test.jpg",
                fileSize = 500000L
            )

            val savedContent = Content(
                id = contentId,
                creatorId = userId,
                contentType = ContentType.PHOTO,
                url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/$s3Key",
                thumbnailUrl = request.thumbnailUrl,
                duration = null,
                width = request.width,
                height = request.height,
                status = ContentStatus.PUBLISHED,
                createdAt = Instant.now(),
                createdBy = userId.toString(),
                updatedAt = Instant.now(),
                updatedBy = userId.toString()
            )

            val savedMetadata = ContentMetadata(
                id = 1L,
                contentId = contentId,
                title = request.title,
                description = request.description,
                category = request.category,
                tags = request.tags,
                language = request.language,
                createdAt = Instant.now(),
                createdBy = userId.toString(),
                updatedAt = Instant.now(),
                updatedBy = userId.toString()
            )

            // Mock 설정
            every { uploadSessionRepository.findById(contentId.toString()) } returns Optional.of(uploadSession)
            every { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } returns HeadObjectResponse.builder().build()
            every { contentRepository.save(any()) } returns Mono.just(savedContent)
            every { contentRepository.saveMetadata(any()) } returns Mono.just(savedMetadata)
            every { tagService.attachTagsToContent(contentId, request.tags!!, userId.toString()) } returns reactor.core.publisher.Flux.empty()
            every { contentPhotoRepository.save(any()) } returns Mono.empty()
            every { contentInteractionService.createContentInteraction(contentId, userId) } returns Mono.empty()
            every { uploadSessionRepository.deleteById(contentId.toString()) } returns Unit
            justRun { eventPublisher.publish(any()) }

            // When: 메서드 실행
            val result = contentService.createContent(userId, request)

            // Then: 결과 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(contentId.toString())
                    assertThat(response.title).isEqualTo(request.title)
                    assertThat(response.contentType).isEqualTo(ContentType.PHOTO)
                    assertThat(response.photoUrls).isNotNull
                    assertThat(response.photoUrls).hasSize(3)
                    assertThat(response.photoUrls).containsExactlyElementsOf(photoUrls)
                }
                .verifyComplete()

            // 각 사진이 저장되었는지 확인 (3개의 photoUrls)
            verify(exactly = 3) { contentPhotoRepository.save(any()) }
            verify(exactly = 1) { contentRepository.save(any()) }
            verify(exactly = 1) { contentRepository.saveMetadata(any()) }
            verify(exactly = 1) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("업로드 세션이 존재하지 않으면, UploadSessionNotFoundException이 발생한다")
        fun createContent_WhenUploadSessionNotFound_ThrowsUploadSessionNotFoundException() {
            // Given: 만료되거나 존재하지 않는 업로드 토큰
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

            // Mock Redis: 세션 없음 (만료됨)
            every { uploadSessionRepository.findById(contentId.toString()) } returns Optional.empty()

            // When & Then: 예외 발생
            val result = contentService.createContent(userId, request)

            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is UploadSessionNotFoundException &&
                        error.message!!.contains("Upload session not found or expired")
                }
                .verify()

            verify(exactly = 1) { uploadSessionRepository.findById(contentId.toString()) }
            verify(exactly = 0) { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) }
        }

        @Test
        @DisplayName("업로드 토큰의 소유자와 요청자가 다르면, UploadSessionUnauthorizedException이 발생한다")
        fun createContent_WhenUserIdMismatch_ThrowsUploadSessionUnauthorizedException() {
            // Given: 다른 사용자의 업로드 토큰
            val tokenOwnerId = UUID.randomUUID()
            val requestUserId = UUID.randomUUID()  // 다른 사용자
            val contentId = UUID.randomUUID()
            val s3Key = "contents/VIDEO/$tokenOwnerId/$contentId/test_123456.mp4"

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

            val uploadSession = UploadSession(
                contentId = contentId.toString(),
                userId = tokenOwnerId.toString(),  // 토큰 소유자
                s3Key = s3Key,
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            // Mock Redis: 다른 사용자의 세션
            every { uploadSessionRepository.findById(contentId.toString()) } returns Optional.of(uploadSession)

            // When & Then: 권한 예외 발생
            val result = contentService.createContent(requestUserId, request)  // 다른 사용자가 요청

            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is UploadSessionUnauthorizedException &&
                        error.message!!.contains("not authorized to use this upload token")
                }
                .verify()

            verify(exactly = 1) { uploadSessionRepository.findById(contentId.toString()) }
            verify(exactly = 0) { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) }
        }

        @Test
        @DisplayName("S3에 파일이 존재하지 않으면, FileNotUploadedException이 발생한다")
        fun createContent_WhenS3FileNotExists_ThrowsFileNotUploadedException() {
            // Given: 파일 업로드를 완료하지 않은 경우
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()
            val s3Key = "contents/VIDEO/$userId/$contentId/test_123456.mp4"

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

            val uploadSession = UploadSession(
                contentId = contentId.toString(),
                userId = userId.toString(),
                s3Key = s3Key,
                contentType = ContentType.VIDEO,
                fileName = "test.mp4",
                fileSize = 1000000L
            )

            // Mock Redis: 세션 존재
            every { uploadSessionRepository.findById(contentId.toString()) } returns Optional.of(uploadSession)

            // Mock S3: 파일 없음 (NoSuchKeyException)
            every { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) } throws
                NoSuchKeyException.builder()
                    .message("The specified key does not exist.")
                    .build()

            // When & Then: 예외 발생
            val result = contentService.createContent(userId, request)

            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is FileNotUploadedException &&
                        error.message!!.contains("File not found in S3")
                }
                .verify()

            verify(exactly = 1) { uploadSessionRepository.findById(contentId.toString()) }
            verify(exactly = 1) { s3Client.headObject(any<Consumer<HeadObjectRequest.Builder>>()) }
            verify(exactly = 0) { contentRepository.save(any()) }
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
                createdAt = Instant.now(),
                createdBy = userId.toString(),
                updatedAt = Instant.now(),
                updatedBy = userId.toString()
            )

            val metadata = ContentMetadata(
                id = 1L,
                contentId = contentId,
                title = "Test Video",
                description = "Test Description",
                category = Category.PROGRAMMING,
                tags = listOf("test"),
                language = "ko",
                createdAt = Instant.now(),
                createdBy = userId.toString(),
                updatedAt = Instant.now(),
                updatedBy = userId.toString()
            )

            every { contentRepository.findById(contentId) } returns Mono.just(content)
            every { contentRepository.findMetadataByContentId(contentId) } returns Mono.just(metadata)
            every { contentPhotoRepository.findByContentId(contentId) } returns Mono.just(emptyList())
            every { contentInteractionRepository.getLikeCount(contentId) } returns Mono.just(0)
            every { contentInteractionRepository.getCommentCount(contentId) } returns Mono.just(0)
            every { contentInteractionRepository.getSaveCount(contentId) } returns Mono.just(0)
            every { contentInteractionRepository.getShareCount(contentId) } returns Mono.just(0)
            every { contentInteractionRepository.getViewCount(contentId) } returns Mono.just(0)

            // When: 메서드 실행
            val result = contentService.getContent(contentId, null)

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

    @Nested
    @DisplayName("updateContent - 콘텐츠 수정")
    inner class UpdateContent {

        @Test
        @DisplayName("PHOTO 타입 콘텐츠의 사진 목록 수정 시, 기존 사진을 삭제하고 새 사진을 저장한다")
        fun updateContent_WithPhotoUrls_DeletesOldAndSavesNewPhotos() {
            // Given: PHOTO 타입 콘텐츠 및 수정 요청
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            val existingContent = Content(
                id = contentId,
                creatorId = userId,
                contentType = ContentType.PHOTO,
                url = "https://s3.amazonaws.com/old.jpg",
                thumbnailUrl = "https://s3.amazonaws.com/thumb.jpg",
                duration = null,
                width = 1080,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                createdAt = Instant.now().minus(1, ChronoUnit.DAYS),
                createdBy = userId.toString(),
                updatedAt = Instant.now().minus(1, ChronoUnit.DAYS),
                updatedBy = userId.toString()
            )

            val existingMetadata = ContentMetadata(
                id = 1L,
                contentId = contentId,
                title = "Old Title",
                description = "Old Description",
                category = Category.HEALTH,
                tags = listOf("old"),
                language = "ko",
                createdAt = Instant.now().minus(1, ChronoUnit.DAYS),
                createdBy = userId.toString(),
                updatedAt = Instant.now().minus(1, ChronoUnit.DAYS),
                updatedBy = userId.toString()
            )

            val newPhotoUrls = listOf(
                "https://s3.amazonaws.com/new1.jpg",
                "https://s3.amazonaws.com/new2.jpg"
            )

            val request = ContentUpdateRequest(
                title = "New Title",
                description = "New Description",
                photoUrls = newPhotoUrls
            )

            // Mock 설정
            every { contentRepository.findById(contentId) } returns Mono.just(existingContent)
            every { contentRepository.findMetadataByContentId(contentId) } returns Mono.just(existingMetadata)
            every { contentRepository.updateMetadata(any()) } returns Mono.just(true)
            every { contentPhotoRepository.deleteByContentId(contentId, userId.toString()) } returns Mono.just(3) // 기존 3개 삭제
            every { contentPhotoRepository.save(any()) } returns Mono.empty()

            // When: 메서드 실행
            val result = contentService.updateContent(userId, contentId, request)

            // Then: 결과 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(contentId.toString())
                    assertThat(response.title).isEqualTo("New Title")
                    assertThat(response.description).isEqualTo("New Description")
                    assertThat(response.contentType).isEqualTo(ContentType.PHOTO)
                    assertThat(response.photoUrls).isNotNull
                    assertThat(response.photoUrls).hasSize(2)
                    assertThat(response.photoUrls).containsExactlyElementsOf(newPhotoUrls)
                }
                .verifyComplete()

            // 기존 사진 삭제 확인
            verify(exactly = 1) { contentPhotoRepository.deleteByContentId(contentId, userId.toString()) }
            // 새 사진 저장 확인 (2개)
            verify(exactly = 2) { contentPhotoRepository.save(any()) }
            verify(exactly = 1) { contentRepository.updateMetadata(any()) }
        }

        @Test
        @DisplayName("메타데이터만 수정 시, 사진 목록은 변경하지 않는다")
        fun updateContent_WithoutPhotoUrls_OnlyUpdatesMetadata() {
            // Given: PHOTO 타입 콘텐츠이지만 photoUrls는 null
            val userId = UUID.randomUUID()
            val contentId = UUID.randomUUID()

            val existingContent = Content(
                id = contentId,
                creatorId = userId,
                contentType = ContentType.PHOTO,
                url = "https://s3.amazonaws.com/photo.jpg",
                thumbnailUrl = "https://s3.amazonaws.com/thumb.jpg",
                duration = null,
                width = 1080,
                height = 1080,
                status = ContentStatus.PUBLISHED,
                createdAt = Instant.now().minus(1, ChronoUnit.DAYS),
                createdBy = userId.toString(),
                updatedAt = Instant.now().minus(1, ChronoUnit.DAYS),
                updatedBy = userId.toString()
            )

            val existingMetadata = ContentMetadata(
                id = 1L,
                contentId = contentId,
                title = "Old Title",
                description = "Old Description",
                category = Category.HEALTH,
                tags = listOf("old"),
                language = "ko",
                createdAt = Instant.now().minus(1, ChronoUnit.DAYS),
                createdBy = userId.toString(),
                updatedAt = Instant.now().minus(1, ChronoUnit.DAYS),
                updatedBy = userId.toString()
            )

            val existingPhotoUrls = listOf(
                "https://s3.amazonaws.com/existing1.jpg",
                "https://s3.amazonaws.com/existing2.jpg"
            )

            val request = ContentUpdateRequest(
                title = "New Title",
                photoUrls = null // 사진 목록은 수정하지 않음
            )

            // Mock 설정
            every { contentRepository.findById(contentId) } returns Mono.just(existingContent)
            every { contentRepository.findMetadataByContentId(contentId) } returns Mono.just(existingMetadata)
            every { contentRepository.updateMetadata(any()) } returns Mono.just(true)
            every { contentPhotoRepository.findByContentId(contentId) } returns Mono.just(listOf(
                ContentPhoto(
                    contentId = contentId,
                    photoUrl = existingPhotoUrls[0],
                    displayOrder = 0,
                    width = 1080,
                    height = 1080,
                    createdAt = Instant.now(),
                    createdBy = userId.toString(),
                    updatedAt = Instant.now(),
                    updatedBy = userId.toString()
                ),
                ContentPhoto(
                    contentId = contentId,
                    photoUrl = existingPhotoUrls[1],
                    displayOrder = 1,
                    width = 1080,
                    height = 1080,
                    createdAt = Instant.now(),
                    createdBy = userId.toString(),
                    updatedAt = Instant.now(),
                    updatedBy = userId.toString()
                )
            ))

            // When: 메서드 실행
            val result = contentService.updateContent(userId, contentId, request)

            // Then: 결과 검증
            StepVerifier.create(result)
                .assertNext { response ->
                    assertThat(response.id).isEqualTo(contentId.toString())
                    assertThat(response.title).isEqualTo("New Title")
                    assertThat(response.photoUrls).isNotNull
                    assertThat(response.photoUrls).hasSize(2)
                    assertThat(response.photoUrls).containsExactlyElementsOf(existingPhotoUrls)
                }
                .verifyComplete()

            // 사진 삭제/저장이 호출되지 않았는지 확인
            verify(exactly = 0) { contentPhotoRepository.deleteByContentId(any(), any()) }
            verify(exactly = 0) { contentPhotoRepository.save(any()) }
            // 기존 사진 조회만 수행
            verify(exactly = 1) { contentPhotoRepository.findByContentId(contentId) }
            verify(exactly = 1) { contentRepository.updateMetadata(any()) }
        }
    }
}
