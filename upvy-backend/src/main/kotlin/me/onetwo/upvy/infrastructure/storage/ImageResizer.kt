package me.onetwo.upvy.infrastructure.storage

import net.coobird.thumbnailator.Thumbnails
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * 이미지 리사이징 유틸리티
 *
 * Thumbnailator 라이브러리를 사용하여 이미지를 리사이징합니다.
 */
@Component
class ImageResizer {

    private val logger = LoggerFactory.getLogger(ImageResizer::class.java)

    /**
     * 이미지를 리사이징합니다.
     *
     * 이미지의 가로/세로 비율을 유지하면서 지정된 최대 크기 내로 리사이징합니다.
     *
     * ### 리사이징 규칙
     * - 원본 이미지가 maxWidth x maxHeight보다 작으면 원본 크기 유지
     * - 가로/세로 비율은 유지하면서 maxWidth 또는 maxHeight에 맞춤
     * - JPEG 형식으로 변환하여 반환
     *
     * @param imageBytes 원본 이미지 바이트 배열
     * @param maxWidth 최대 너비 (픽셀)
     * @param maxHeight 최대 높이 (픽셀)
     * @param quality 이미지 품질 (0.0 ~ 1.0)
     * @return 리사이징된 이미지 바이트 배열
     * @throws IllegalArgumentException 이미지 처리 실패 시
     */
    fun resize(
        imageBytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        quality: Double
    ): ByteArray {
        return try {
            logger.debug("Resizing image: maxWidth=$maxWidth, maxHeight=$maxHeight, quality=$quality")

            // 원본 이미지 크기 확인
            val originalImage = ImageIO.read(ByteArrayInputStream(imageBytes))
                ?: throw IllegalArgumentException("Invalid image format")

            val originalWidth = originalImage.width
            val originalHeight = originalImage.height

            logger.debug("Original image size: ${originalWidth}x${originalHeight}")

            // 리사이징 필요 여부 확인
            if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
                logger.debug("Image is already smaller than max size, returning original")
                return imageBytes
            }

            // 리사이징 실행
            val outputStream = ByteArrayOutputStream()
            Thumbnails.of(ByteArrayInputStream(imageBytes))
                .size(maxWidth, maxHeight)
                .outputFormat("jpg")
                .outputQuality(quality)
                .toOutputStream(outputStream)

            val resizedBytes = outputStream.toByteArray()
            logger.info("Image resized successfully: ${resizedBytes.size} bytes")

            resizedBytes
        } catch (e: Exception) {
            logger.error("Failed to resize image", e)
            throw IllegalArgumentException("Failed to resize image: ${e.message}", e)
        }
    }

    /**
     * 이미지의 Content-Type을 확인합니다.
     *
     * @param imageBytes 이미지 바이트 배열
     * @return Content-Type (예: "image/jpeg")
     */
    fun detectContentType(imageBytes: ByteArray): String {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(imageBytes))
                ?: return "application/octet-stream"

            // 이미지 형식 감지
            val readers = ImageIO.getImageReaders(ImageIO.createImageInputStream(ByteArrayInputStream(imageBytes)))
            if (readers.hasNext()) {
                val reader = readers.next()
                val formatName = reader.formatName.lowercase()
                "image/$formatName"
            } else {
                "image/jpeg"  // 기본값
            }
        } catch (e: Exception) {
            logger.warn("Failed to detect image content type", e)
            "image/jpeg"  // 기본값
        }
    }
}
