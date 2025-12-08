package me.onetwo.upvy.infrastructure.storage

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import javax.imageio.ImageIO

/**
 * ImageResizer 단위 테스트
 */
@DisplayName("이미지 리사이저 테스트")
class ImageResizerTest {

    private lateinit var imageResizer: ImageResizer

    @BeforeEach
    fun setUp() {
        imageResizer = ImageResizer()
    }

    @Test
    @DisplayName("유효한 이미지를 리사이징하면, 지정된 크기 이내로 축소된다")
    fun resize_WithValidImage_ResizesImageToMaxSize() {
        // Given: 800x600 크기의 테스트 이미지 생성
        val originalImage = createTestImage(800, 600)
        val maxWidth = 500
        val maxHeight = 500
        val quality = 0.85

        // When: 리사이징 실행
        val resizedBytes = imageResizer.resize(originalImage, maxWidth, maxHeight, quality)

        // Then: 리사이징된 이미지 크기 확인
        val resizedImage = ImageIO.read(resizedBytes.inputStream())
        assertNotNull(resizedImage)
        assertTrue(resizedImage.width <= maxWidth)
        assertTrue(resizedImage.height <= maxHeight)
        assertTrue(resizedBytes.size < originalImage.size) // 파일 크기도 작아져야 함
    }

    @Test
    @DisplayName("원본 이미지가 최대 크기보다 작으면, 원본을 그대로 반환한다")
    fun resize_WithSmallerImage_ReturnsOriginal() {
        // Given: 300x300 크기의 작은 이미지
        val smallImage = createTestImage(300, 300)
        val maxWidth = 500
        val maxHeight = 500
        val quality = 0.85

        // When: 리사이징 실행
        val resizedBytes = imageResizer.resize(smallImage, maxWidth, maxHeight, quality)

        // Then: 원본과 동일한 바이트 배열 반환
        assertArrayEquals(smallImage, resizedBytes)
    }

    @Test
    @DisplayName("가로가 긴 이미지는 가로 기준으로 리사이징되고, 비율이 유지된다")
    fun resize_WithWideImage_MaintainsAspectRatio() {
        // Given: 1000x500 크기의 가로가 긴 이미지
        val wideImage = createTestImage(1000, 500)
        val maxWidth = 500
        val maxHeight = 500
        val quality = 0.85

        // When: 리사이징 실행
        val resizedBytes = imageResizer.resize(wideImage, maxWidth, maxHeight, quality)

        // Then: 가로세로 비율 유지 확인
        val resizedImage = ImageIO.read(resizedBytes.inputStream())
        assertEquals(500, resizedImage.width)
        assertEquals(250, resizedImage.height) // 2:1 비율 유지
    }

    @Test
    @DisplayName("세로가 긴 이미지는 세로 기준으로 리사이징되고, 비율이 유지된다")
    fun resize_WithTallImage_MaintainsAspectRatio() {
        // Given: 500x1000 크기의 세로가 긴 이미지
        val tallImage = createTestImage(500, 1000)
        val maxWidth = 500
        val maxHeight = 500
        val quality = 0.85

        // When: 리사이징 실행
        val resizedBytes = imageResizer.resize(tallImage, maxWidth, maxHeight, quality)

        // Then: 가로세로 비율 유지 확인
        val resizedImage = ImageIO.read(resizedBytes.inputStream())
        assertEquals(250, resizedImage.width) // 1:2 비율 유지
        assertEquals(500, resizedImage.height)
    }

    @Test
    @DisplayName("잘못된 이미지 데이터는 예외를 발생시킨다")
    fun resize_WithInvalidImage_ThrowsException() {
        // Given: 잘못된 이미지 데이터
        val invalidImage = "invalid image data".toByteArray()
        val maxWidth = 500
        val maxHeight = 500
        val quality = 0.85

        // When & Then: 예외 발생 확인
        val exception = assertThrows<IllegalArgumentException> {
            imageResizer.resize(invalidImage, maxWidth, maxHeight, quality)
        }
        assertTrue(exception.message!!.contains("Failed to resize image"))
    }

    @Test
    @DisplayName("JPEG 이미지의 Content-Type을 정확히 감지한다")
    fun detectContentType_WithJpegImage_ReturnsImageJpeg() {
        // Given: JPEG 이미지
        val jpegImage = createTestImage(100, 100)

        // When: Content-Type 감지
        val contentType = imageResizer.detectContentType(jpegImage)

        // Then: image/jpeg 반환
        assertEquals("image/jpeg", contentType)
    }

    @Test
    @DisplayName("잘못된 이미지 데이터의 Content-Type 감지 시 기본값을 반환한다")
    fun detectContentType_WithInvalidImage_ReturnsDefault() {
        // Given: 잘못된 데이터
        val invalidData = "invalid".toByteArray()

        // When: Content-Type 감지
        val contentType = imageResizer.detectContentType(invalidData)

        // Then: 기본값 반환
        assertEquals("application/octet-stream", contentType)
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

        // 이미지에 간단한 패턴 그리기
        val graphics = bufferedImage.createGraphics()
        graphics.color = java.awt.Color.BLUE
        graphics.fillRect(0, 0, width, height)
        graphics.color = java.awt.Color.WHITE
        graphics.drawString("Test Image", 10, 20)
        graphics.dispose()

        // JPEG로 변환
        val outputStream = java.io.ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "jpg", outputStream)
        return outputStream.toByteArray()
    }
}
