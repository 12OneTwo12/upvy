package me.onetwo.upvy.domain.content.service

import me.onetwo.upvy.domain.content.exception.ContentException
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.model.UploadSession
import me.onetwo.upvy.domain.content.repository.UploadSessionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

/**
 * 콘텐츠 업로드 서비스 구현체
 *
 * AWS S3 Presigned URL을 사용하여 클라이언트가 직접 S3에 파일을 업로드하도록 지원합니다.
 *
 * @property s3Presigner AWS S3 Presigner
 * @property bucketName S3 버킷 이름
 */
@Service
class ContentUploadServiceImpl(
    private val s3Presigner: S3Presigner,
    private val uploadSessionRepository: UploadSessionRepository,
    @Value("\${spring.cloud.aws.s3.bucket}") private val bucketName: String
) : ContentUploadService {

    private val logger = LoggerFactory.getLogger(ContentUploadServiceImpl::class.java)

    companion object {
        private const val MAX_VIDEO_SIZE = 500L * 1024 * 1024  // 500MB
        private const val MAX_PHOTO_SIZE = 50L * 1024 * 1024   // 50MB
        private const val PRESIGNED_URL_DURATION_MINUTES = 15L

        private val ALLOWED_VIDEO_TYPES = listOf("video/mp4", "video/quicktime", "video/x-msvideo")
        private val ALLOWED_PHOTO_TYPES = listOf("image/jpeg", "image/jpg", "image/png", "image/heic", "image/heif")
    }

    /**
     * S3 Presigned URL을 생성합니다.
     *
     * 클라이언트가 콘텐츠를 직접 S3에 업로드할 수 있도록 Presigned URL을 발급합니다.
     *
     * ### 처리 흐름
     * 1. 파일 유효성 검증 (크기, 형식)
     * 2. S3 object key 생성
     * 3. Presigned URL 생성 (유효기간: 15분)
     * 4. URL 및 contentId 반환
     *
     * @param userId 사용자 ID
     * @param contentType 콘텐츠 타입 (VIDEO, PHOTO)
     * @param fileName 파일 이름
     * @param fileSize 파일 크기 (바이트)
     * @param mimeType MIME 타입 (제공되지 않으면 fileName에서 추론)
     * @return Presigned URL 정보를 담은 Mono
     * @throws IllegalArgumentException 파일 유효성 검증 실패 시
     */
    override fun generateUploadUrl(
        userId: UUID,
        contentType: ContentType,
        fileName: String,
        fileSize: Long,
        mimeType: String?
    ): Mono<PresignedUrlInfo> {
        return Mono.fromCallable {
            logger.info("Generating presigned URL for user: $userId, contentType: $contentType, fileName: $fileName, mimeType: $mimeType")

            // 1. MIME 타입 결정 (명시적으로 제공된 값 우선, 없으면 fileName에서 추론)
            val actualMimeType = mimeType ?: getMimeType(fileName, contentType)

            // 2. 파일 유효성 검증
            validateFile(contentType, actualMimeType, fileSize)

            // 3. Content ID 및 S3 object key 생성
            val contentId = UUID.randomUUID()
            val key = generateS3Key(userId, contentId, contentType, fileName)

            // 4. Presigned URL 생성
            val presignedUrl = createPresignedUrl(key, actualMimeType, fileSize)

            logger.info("Presigned URL generated successfully: contentId=$contentId, key=$key")

            // Redis에 업로드 세션 저장 (TTL 15분)
            val uploadSession = UploadSession(
                contentId = contentId.toString(),
                userId = userId.toString(),
                s3Key = key,
                contentType = contentType,
                fileName = fileName,
                fileSize = fileSize
            )
            uploadSessionRepository.save(uploadSession)

            logger.info("Upload session saved to Redis: contentId=$contentId")

            PresignedUrlInfo(
                contentId = contentId,
                uploadUrl = presignedUrl,
                expiresIn = (PRESIGNED_URL_DURATION_MINUTES * 60).toInt()
            )
        }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnError { error ->
                logger.error("Failed to generate presigned URL for user: $userId", error)
            }
    }

    /**
     * 파일 유효성을 검증합니다.
     *
     * @param contentType 콘텐츠 타입
     * @param mimeType MIME 타입
     * @param fileSize 파일 크기 (바이트)
     * @throws IllegalArgumentException 유효성 검증 실패 시
     */
    private fun validateFile(contentType: ContentType, mimeType: String, fileSize: Long) {
        // 파일 크기 검증
        val maxSize = when (contentType) {
            ContentType.VIDEO -> MAX_VIDEO_SIZE
            ContentType.PHOTO -> MAX_PHOTO_SIZE
            ContentType.QUIZ -> throw ContentException.UnsupportedContentTypeException(contentType.name, "file uploads")
        }

        if (fileSize > maxSize) {
            throw ContentException.FileSizeLimitExceededException(
                contentType.name,
                maxSize / 1024 / 1024
            )
        }

        // MIME 타입 검증
        val allowedTypes = when (contentType) {
            ContentType.VIDEO -> ALLOWED_VIDEO_TYPES
            ContentType.PHOTO -> ALLOWED_PHOTO_TYPES
            ContentType.QUIZ -> throw ContentException.UnsupportedContentTypeException(contentType.name, "file uploads")
        }

        if (mimeType !in allowedTypes) {
            throw ContentException.InvalidFileException(
                "Unsupported file type: $mimeType. Allowed types for $contentType: $allowedTypes"
            )
        }

        logger.debug("File validation passed: contentType=$contentType, size=$fileSize, mimeType=$mimeType")
    }

    /**
     * S3 object key를 생성합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param contentType 콘텐츠 타입
     * @param fileName 파일 이름
     * @return S3 object key (예: "contents/VIDEO/userId/contentId/fileName.mp4")
     */
    private fun generateS3Key(
        userId: UUID,
        contentId: UUID,
        contentType: ContentType,
        fileName: String
    ): String {
        val timestamp = System.currentTimeMillis()
        val extension = fileName.substringAfterLast('.', "")
        val sanitizedFileName = "${contentId}_${timestamp}.$extension"

        return "contents/${contentType.name}/$userId/$contentId/$sanitizedFileName"
    }

    /**
     * 파일 이름에서 MIME 타입을 추출합니다.
     *
     * @param fileName 파일 이름
     * @param contentType 콘텐츠 타입
     * @return MIME 타입
     */
    private fun getMimeType(fileName: String, contentType: ContentType): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when (contentType) {
            ContentType.VIDEO -> when (extension) {
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                "avi" -> "video/x-msvideo"
                else -> throw ContentException.InvalidFileException("Unsupported video extension: $extension")
            }
            ContentType.PHOTO -> when (extension) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                else -> throw ContentException.InvalidFileException("Unsupported photo extension: $extension")
            }
            ContentType.QUIZ -> throw ContentException.UnsupportedContentTypeException(contentType.name, "file uploads")
        }
    }

    /**
     * S3 Presigned URL을 생성합니다.
     *
     * @param key S3 object key
     * @param contentType Content-Type
     * @param contentLength Content-Length
     * @return Presigned URL
     */
    private fun createPresignedUrl(key: String, contentType: String, contentLength: Long): String {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(PRESIGNED_URL_DURATION_MINUTES))
            .putObjectRequest(putObjectRequest)
            .build()

        val presignedRequest = s3Presigner.presignPutObject(presignRequest)

        return presignedRequest.url().toString()
    }
}
