package me.onetwo.upvy.infrastructure.storage

import me.onetwo.upvy.infrastructure.config.ImageUploadProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

/**
 * 이미지 업로드 서비스 구현체
 *
 * AWS S3를 사용하여 이미지를 업로드하고 관리합니다.
 *
 * @property s3Client AWS S3 클라이언트
 * @property imageResizer 이미지 리사이저
 * @property imageUploadProperties 이미지 업로드 설정
 * @property bucketName S3 버킷 이름
 */
@Service
class ImageUploadServiceImpl(
    private val s3Client: S3Client,
    private val imageResizer: ImageResizer,
    private val imageUploadProperties: ImageUploadProperties,
    @Value("\${spring.cloud.aws.s3.bucket}") private val bucketName: String
) : ImageUploadService {

    private val logger = LoggerFactory.getLogger(ImageUploadServiceImpl::class.java)

    /**
     * 프로필 이미지를 업로드합니다.
     *
     * 이미지를 리사이징한 후 S3에 업로드하고, 업로드된 이미지의 URL을 반환합니다.
     *
     * ### 처리 흐름
     * 1. 이미지 유효성 검증 (크기, 형식)
     * 2. 이미지 리사이징 (설정된 크기로)
     * 3. S3에 업로드 (profile-images/{userId}/{fileName})
     * 4. 업로드된 이미지 URL 반환
     *
     * @param userId 사용자 ID
     * @param imageBytes 이미지 바이트 배열
     * @param contentType 이미지 Content-Type (예: "image/jpeg")
     * @return 업로드된 이미지 URL을 담은 Mono
     * @throws IllegalArgumentException 이미지 유효성 검증 실패 시
     * @throws RuntimeException S3 업로드 실패 시
     */
    override fun uploadProfileImage(
        userId: UUID,
        imageBytes: ByteArray,
        contentType: String
    ): Mono<String> {
        return Mono.fromCallable {
            logger.info("Uploading profile image for user: $userId")

            // 1. 이미지 유효성 검증
            validateImage(imageBytes, contentType)

            // 2. 이미지 리사이징
            val config = imageUploadProperties.profileImage
            val resizedImage = imageResizer.resize(
                imageBytes,
                config.maxWidth,
                config.maxHeight,
                config.quality
            )

            // 3. S3에 업로드
            val fileName = generateFileName(userId)
            val key = "profile-images/$userId/$fileName"

            uploadToS3(key, resizedImage, "image/jpeg")

            // 4. URL 반환
            val imageUrl = "https://$bucketName.s3.amazonaws.com/$key"
            logger.info("Profile image uploaded successfully: $imageUrl")

            imageUrl
        }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError { error ->
                logger.error("Failed to upload profile image for user: $userId", error)
            }
    }

    /**
     * S3에서 이미지를 삭제합니다.
     *
     * @param imageUrl 삭제할 이미지 URL
     * @return 삭제 완료를 알리는 Mono<Void>
     */
    override fun deleteImage(imageUrl: String): Mono<Void> {
        return Mono.fromRunnable<Void> {
            logger.info("Deleting image: $imageUrl")

            // URL에서 key 추출
            val key = extractKeyFromUrl(imageUrl)

            val deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()

            s3Client.deleteObject(deleteRequest)

            logger.info("Image deleted successfully: $imageUrl")
        }
            .subscribeOn(Schedulers.boundedElastic())
            .then()
            .doOnError { error ->
                logger.error("Failed to delete image: $imageUrl", error)
            }
    }

    /**
     * 이미지 유효성을 검증합니다.
     *
     * @param imageBytes 이미지 바이트 배열
     * @param contentType Content-Type
     * @throws IllegalArgumentException 유효성 검증 실패 시
     */
    private fun validateImage(imageBytes: ByteArray, contentType: String) {
        // 파일 크기 검증
        if (imageBytes.size > imageUploadProperties.maxFileSize) {
            throw IllegalArgumentException(
                "File size exceeds maximum allowed size: ${imageUploadProperties.maxFileSize} bytes"
            )
        }

        // Content-Type 검증
        if (contentType !in imageUploadProperties.allowedImageTypes) {
            throw IllegalArgumentException(
                "Unsupported image type: $contentType. Allowed types: ${imageUploadProperties.allowedImageTypes}"
            )
        }

        logger.debug("Image validation passed: size=${imageBytes.size}, contentType=$contentType")
    }

    /**
     * S3에 파일을 업로드합니다.
     *
     * @param key S3 object key
     * @param data 파일 데이터
     * @param contentType Content-Type
     */
    private fun uploadToS3(key: String, data: ByteArray, contentType: String) {
        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .contentLength(data.size.toLong())
            .build()

        s3Client.putObject(putRequest, RequestBody.fromBytes(data))

        logger.debug("Uploaded to S3: bucket=$bucketName, key=$key, size=${data.size}")
    }

    /**
     * 고유한 파일 이름을 생성합니다.
     *
     * @param userId 사용자 ID
     * @return 파일 이름 (예: "profile_123e4567-e89b-12d3-a456-426614174000_1234567890.jpg")
     */
    private fun generateFileName(userId: UUID): String {
        val timestamp = System.currentTimeMillis()
        return "profile_${userId}_$timestamp.jpg"
    }

    /**
     * 이미지 URL에서 S3 key를 추출합니다.
     *
     * @param imageUrl 이미지 URL
     * @return S3 object key
     */
    private fun extractKeyFromUrl(imageUrl: String): String {
        // https://bucket-name.s3.amazonaws.com/profile-images/userId/fileName.jpg
        // -> profile-images/userId/fileName.jpg
        val pattern = """https://$bucketName\.s3\.amazonaws\.com/(.+)""".toRegex()
        val matchResult = pattern.find(imageUrl)
            ?: throw IllegalArgumentException("Invalid S3 URL: $imageUrl")

        return matchResult.groupValues[1]
    }
}
