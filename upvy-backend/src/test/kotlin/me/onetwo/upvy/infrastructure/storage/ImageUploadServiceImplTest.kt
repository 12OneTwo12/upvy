package me.onetwo.upvy.infrastructure.storage

import me.onetwo.upvy.infrastructure.config.BaseReactiveTest
import io.mockk.*
import io.mockk.junit5.MockKExtension
import me.onetwo.upvy.infrastructure.config.ImageUploadProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import reactor.test.StepVerifier
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.util.UUID
import javax.imageio.ImageIO

/**
 * ImageUploadService 단위 테스트
 */
@ExtendWith(MockKExtension::class)
@DisplayName("이미지 업로드 Service 테스트")
class ImageUploadServiceImplTest : BaseReactiveTest {

    private lateinit var s3Client: S3Client
    private lateinit var imageResizer: ImageResizer
    private lateinit var imageUploadProperties: ImageUploadProperties
    private lateinit var imageUploadService: ImageUploadService

    private val bucketName = "test-bucket"
    private val testUserId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        s3Client = mockk()
        imageResizer = mockk()
        imageUploadProperties = ImageUploadProperties()
        imageUploadService = ImageUploadServiceImpl(
            s3Client,
            imageResizer,
            imageUploadProperties,
            bucketName
        )
    }

    @Test
    @DisplayName("유효한 프로필 이미지 업로드 시, 리사이징 후 S3에 업로드하고 URL을 반환한다")
    fun uploadProfileImage_WithValidImage_ResizesAndUploadsToS3() {
        // Given: 유효한 이미지 데이터
        val originalImage = createTestImage(800, 600)
        val resizedImage = createTestImage(500, 375)
        val contentType = "image/jpeg"

        every { imageResizer.resize(any(), any(), any(), any()) } returns resizedImage
        every {
            s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        // When: 프로필 이미지 업로드
        val result = imageUploadService.uploadProfileImage(testUserId, originalImage, contentType)

        // Then: URL이 반환되고 S3에 업로드됨
        StepVerifier.create(result)
            .assertNext { imageUrl ->
                assertTrue(imageUrl.startsWith("https://$bucketName.s3.amazonaws.com/"))
                assertTrue(imageUrl.contains("profile-images/$testUserId/"))
            }
            .verifyComplete()

        verify(exactly = 1) {
            imageResizer.resize(
                originalImage,
                imageUploadProperties.profileImage.maxWidth,
                imageUploadProperties.profileImage.maxHeight,
                imageUploadProperties.profileImage.quality
            )
        }
        verify(exactly = 1) {
            s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())
        }
    }

    @Test
    @DisplayName("파일 크기가 최대 크기를 초과하면, 예외를 발생시킨다")
    fun uploadProfileImage_WithOversizedImage_ThrowsException() {
        // Given: 최대 크기를 초과하는 이미지
        val oversizedImage = ByteArray(imageUploadProperties.maxFileSize.toInt() + 1)
        val contentType = "image/jpeg"

        // When: 업로드 시도
        val result = imageUploadService.uploadProfileImage(testUserId, oversizedImage, contentType)

        // Then: 예외 발생
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is IllegalArgumentException &&
                        error.message!!.contains("File size exceeds maximum allowed size")
            }
            .verify()

        verify(exactly = 0) { imageResizer.resize(any(), any(), any(), any()) }
        verify(exactly = 0) { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
    }

    @Test
    @DisplayName("허용되지 않은 이미지 형식은, 예외를 발생시킨다")
    fun uploadProfileImage_WithUnsupportedFormat_ThrowsException() {
        // Given: 허용되지 않은 형식
        val imageBytes = createTestImage(100, 100)
        val unsupportedContentType = "image/bmp"

        // When: 업로드 시도
        val result = imageUploadService.uploadProfileImage(testUserId, imageBytes, unsupportedContentType)

        // Then: 예외 발생
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is IllegalArgumentException &&
                        error.message!!.contains("Unsupported image type")
            }
            .verify()

        verify(exactly = 0) { imageResizer.resize(any(), any(), any(), any()) }
        verify(exactly = 0) { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
    }

    @Test
    @DisplayName("S3 업로드 실패 시, 에러를 전파한다")
    fun uploadProfileImage_WhenS3UploadFails_PropagatesError() {
        // Given: S3 업로드 실패 상황
        val imageBytes = createTestImage(100, 100)
        val contentType = "image/jpeg"

        every { imageResizer.resize(any(), any(), any(), any()) } returns imageBytes
        every {
            s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())
        } throws RuntimeException("S3 upload failed")

        // When: 업로드 시도
        val result = imageUploadService.uploadProfileImage(testUserId, imageBytes, contentType)

        // Then: 에러 전파
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is RuntimeException &&
                        error.message!!.contains("S3 upload failed")
            }
            .verify()
    }

    @Test
    @DisplayName("이미지 삭제 시, S3에서 해당 객체를 삭제한다")
    fun deleteImage_WithValidUrl_DeletesFromS3() {
        // Given: 유효한 S3 URL
        val imageUrl = "https://$bucketName.s3.amazonaws.com/profile-images/$testUserId/image.jpg"

        every {
            s3Client.deleteObject(any<DeleteObjectRequest>())
        } returns DeleteObjectResponse.builder().build()

        // When: 이미지 삭제
        val result = imageUploadService.deleteImage(imageUrl)

        // Then: S3에서 삭제됨
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) {
            s3Client.deleteObject(
                match<DeleteObjectRequest> { request ->
                    request.bucket() == bucketName &&
                            request.key() == "profile-images/$testUserId/image.jpg"
                }
            )
        }
    }

    @Test
    @DisplayName("잘못된 S3 URL로 삭제 시도 시, 예외를 발생시킨다")
    fun deleteImage_WithInvalidUrl_ThrowsException() {
        // Given: 잘못된 URL
        val invalidUrl = "https://invalid-url.com/image.jpg"

        // When: 삭제 시도
        val result = imageUploadService.deleteImage(invalidUrl)

        // Then: 예외 발생
        StepVerifier.create(result)
            .expectErrorMatches { error ->
                error is IllegalArgumentException &&
                        error.message!!.contains("Invalid S3 URL")
            }
            .verify()

        verify(exactly = 0) { s3Client.deleteObject(any<DeleteObjectRequest>()) }
    }

    @Test
    @DisplayName("JPEG 형식 이미지는 정상적으로 업로드된다")
    fun uploadProfileImage_WithJpegImage_UploadsSuccessfully() {
        // Given: JPEG 이미지
        val jpegImage = createTestImage(100, 100)
        val contentType = "image/jpeg"

        every { imageResizer.resize(any(), any(), any(), any()) } returns jpegImage
        every {
            s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        // When: 업로드
        val result = imageUploadService.uploadProfileImage(testUserId, jpegImage, contentType)

        // Then: 성공
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    @DisplayName("PNG 형식 이미지는 정상적으로 업로드된다")
    fun uploadProfileImage_WithPngImage_UploadsSuccessfully() {
        // Given: PNG 이미지
        val pngImage = createTestImage(100, 100)
        val contentType = "image/png"

        every { imageResizer.resize(any(), any(), any(), any()) } returns pngImage
        every {
            s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>())
        } returns PutObjectResponse.builder().build()

        // When: 업로드
        val result = imageUploadService.uploadProfileImage(testUserId, pngImage, contentType)

        // Then: 성공
        StepVerifier.create(result)
            .expectNextCount(1)
            .verifyComplete()
    }

    /**
     * 테스트용 이미지 생성
     *
     * @param width 너비
     * @param height 높이
     * @return JPEG 이미지 바이트 배열
     */
    private fun createTestImage(width: Int, height: Int): ByteArray {
        val bufferedImage = java.awt.image.BufferedImage(
            width,
            height,
            java.awt.image.BufferedImage.TYPE_INT_RGB
        )

        val graphics = bufferedImage.createGraphics()
        graphics.color = java.awt.Color.BLUE
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()

        val outputStream = java.io.ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "jpg", outputStream)
        return outputStream.toByteArray()
    }
}
