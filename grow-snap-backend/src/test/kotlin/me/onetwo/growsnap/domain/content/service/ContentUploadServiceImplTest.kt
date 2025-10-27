package me.onetwo.growsnap.domain.content.service

import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import me.onetwo.growsnap.domain.content.model.ContentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import reactor.test.StepVerifier
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URL
import java.time.Duration
import java.util.UUID

@ExtendWith(MockKExtension::class)
@DisplayName("콘텐츠 업로드 Service 테스트")
class ContentUploadServiceImplTest {

    @MockK
    private lateinit var s3Presigner: S3Presigner

    @MockK
    private lateinit var presignedPutObjectRequest: PresignedPutObjectRequest

    private lateinit var contentUploadService: ContentUploadServiceImpl

    @BeforeEach
    fun setUp() {
        contentUploadService = ContentUploadServiceImpl(
            s3Presigner = s3Presigner,
            bucketName = "test-bucket"
        )
    }

    @Nested
    @DisplayName("generateUploadUrl - Presigned URL 생성")
    inner class GenerateUploadUrl {

        @Test
        @DisplayName("유효한 비디오 파일 정보로 Presigned URL 생성 시, URL이 반환된다")
        fun generateUploadUrl_WithValidVideoFile_ReturnsPresignedUrl() {
            // Given: 유효한 비디오 파일 정보
            val userId = UUID.randomUUID()
            val contentType = ContentType.VIDEO
            val fileName = "test.mp4"
            val fileSize = 100_000_000L  // 100MB
            val expectedUrl = "https://s3.amazonaws.com/presigned-url"

            every { presignedPutObjectRequest.url() } returns URL(expectedUrl)
            every { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presignedPutObjectRequest

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: URL 반환
            StepVerifier.create(result)
                .assertNext { presignedUrlInfo ->
                    assertThat(presignedUrlInfo.contentId).isNotNull
                    assertThat(presignedUrlInfo.uploadUrl).isEqualTo(expectedUrl)
                    assertThat(presignedUrlInfo.expiresIn).isEqualTo(900)  // 15분
                }
                .verifyComplete()

            verify(exactly = 1) { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) }
        }

        @Test
        @DisplayName("유효한 사진 파일 정보로 Presigned URL 생성 시, URL이 반환된다")
        fun generateUploadUrl_WithValidPhotoFile_ReturnsPresignedUrl() {
            // Given: 유효한 사진 파일 정보
            val userId = UUID.randomUUID()
            val contentType = ContentType.PHOTO
            val fileName = "test.jpg"
            val fileSize = 10_000_000L  // 10MB
            val expectedUrl = "https://s3.amazonaws.com/presigned-url"

            every { presignedPutObjectRequest.url() } returns URL(expectedUrl)
            every { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presignedPutObjectRequest

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: URL 반환
            StepVerifier.create(result)
                .assertNext { presignedUrlInfo ->
                    assertThat(presignedUrlInfo.uploadUrl).isEqualTo(expectedUrl)
                }
                .verifyComplete()
        }

        @Test
        @DisplayName("PNG 사진 파일도 업로드가 가능하다")
        fun generateUploadUrl_WithPngFile_ReturnsPresignedUrl() {
            // Given: PNG 파일
            val userId = UUID.randomUUID()
            val contentType = ContentType.PHOTO
            val fileName = "test.png"
            val fileSize = 10_000_000L  // 10MB
            val expectedUrl = "https://s3.amazonaws.com/presigned-url"

            every { presignedPutObjectRequest.url() } returns URL(expectedUrl)
            every { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presignedPutObjectRequest

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: URL 반환 (PNG 지원 확인)
            StepVerifier.create(result)
                .assertNext { presignedUrlInfo ->
                    assertThat(presignedUrlInfo.contentId).isNotNull
                    assertThat(presignedUrlInfo.uploadUrl).isEqualTo(expectedUrl)
                    assertThat(presignedUrlInfo.expiresIn).isEqualTo(900)
                }
                .verifyComplete()

            verify(exactly = 1) { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) }
        }

        @Test
        @DisplayName("비디오 파일 크기가 500MB를 초과하면, IllegalArgumentException이 발생한다")
        fun generateUploadUrl_WhenVideoExceedsMaxSize_ThrowsException() {
            // Given: 500MB 초과 비디오
            val userId = UUID.randomUUID()
            val contentType = ContentType.VIDEO
            val fileName = "large.mp4"
            val fileSize = 600_000_000L  // 600MB

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: 예외 발생
            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is IllegalArgumentException &&
                        error.message!!.contains("File size exceeds maximum allowed size for VIDEO: 500MB")
                }
                .verify()
        }

        @Test
        @DisplayName("사진 파일 크기가 50MB를 초과하면, IllegalArgumentException이 발생한다")
        fun generateUploadUrl_WhenPhotoExceedsMaxSize_ThrowsException() {
            // Given: 50MB 초과 사진
            val userId = UUID.randomUUID()
            val contentType = ContentType.PHOTO
            val fileName = "large.jpg"
            val fileSize = 60_000_000L  // 60MB

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: 예외 발생
            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is IllegalArgumentException &&
                        error.message!!.contains("File size exceeds maximum allowed size for PHOTO: 50MB")
                }
                .verify()
        }

        @Test
        @DisplayName("지원하지 않는 비디오 형식이면, IllegalArgumentException이 발생한다")
        fun generateUploadUrl_WithUnsupportedVideoFormat_ThrowsException() {
            // Given: 지원하지 않는 비디오 형식
            val userId = UUID.randomUUID()
            val contentType = ContentType.VIDEO
            val fileName = "test.wmv"  // 지원하지 않는 형식
            val fileSize = 100_000_000L

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: 예외 발생
            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is IllegalArgumentException &&
                        error.message!!.contains("Unsupported video extension: wmv")
                }
                .verify()
        }

        @Test
        @DisplayName("지원하지 않는 사진 형식이면, IllegalArgumentException이 발생한다")
        fun generateUploadUrl_WithUnsupportedPhotoFormat_ThrowsException() {
            // Given: 지원하지 않는 사진 형식
            val userId = UUID.randomUUID()
            val contentType = ContentType.PHOTO
            val fileName = "test.bmp"  // 지원하지 않는 형식
            val fileSize = 10_000_000L

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: 예외 발생
            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is IllegalArgumentException &&
                        error.message!!.contains("Unsupported photo extension: bmp")
                }
                .verify()
        }

        @Test
        @DisplayName("파일 확장자가 없으면, IllegalArgumentException이 발생한다")
        fun generateUploadUrl_WithNoExtension_ThrowsException() {
            // Given: 확장자 없는 파일
            val userId = UUID.randomUUID()
            val contentType = ContentType.VIDEO
            val fileName = "test"  // 확장자 없음
            val fileSize = 100_000_000L

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: 예외 발생
            StepVerifier.create(result)
                .expectErrorMatches { error ->
                    error is IllegalArgumentException &&
                        error.message!!.contains("Unsupported video extension")
                }
                .verify()
        }

        @Test
        @DisplayName("S3 객체 키가 올바른 형식으로 생성된다")
        fun generateUploadUrl_GeneratesCorrectS3Key() {
            // Given: 유효한 파일 정보
            val userId = UUID.randomUUID()
            val contentType = ContentType.VIDEO
            val fileName = "test.mp4"
            val fileSize = 100_000_000L
            val expectedUrl = "https://s3.amazonaws.com/presigned-url"

            every { presignedPutObjectRequest.url() } returns URL(expectedUrl)
            every { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presignedPutObjectRequest

            // When: Presigned URL 생성
            val result = contentUploadService.generateUploadUrl(userId, contentType, fileName, fileSize)

            // Then: S3 키 형식 확인
            StepVerifier.create(result)
                .assertNext { presignedUrlInfo ->
                    // S3 키는 contents/{contentType}/{userId}/{contentId}/{fileName} 형식
                    assertThat(presignedUrlInfo.contentId).isNotNull
                }
                .verifyComplete()

            // S3 Presigner 호출 시 올바른 키가 사용되었는지 검증
            verify(exactly = 1) {
                s3Presigner.presignPutObject(match<PutObjectPresignRequest> { request ->
                    val putObjectRequest = request.putObjectRequest()
                    putObjectRequest.key().startsWith("contents/VIDEO/$userId/")
                })
            }
        }
    }
}
