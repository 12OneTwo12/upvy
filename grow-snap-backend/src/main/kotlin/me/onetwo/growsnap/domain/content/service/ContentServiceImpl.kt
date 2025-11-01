package me.onetwo.growsnap.domain.content.service

import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentResponse
import me.onetwo.growsnap.domain.content.dto.ContentUpdateRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlResponse
import me.onetwo.growsnap.domain.content.event.ContentCreatedEvent
import me.onetwo.growsnap.domain.content.exception.FileNotUploadedException
import me.onetwo.growsnap.domain.content.exception.UploadSessionNotFoundException
import me.onetwo.growsnap.domain.content.exception.UploadSessionUnauthorizedException
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentPhoto
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.util.UUID

/**
 * 콘텐츠 서비스 구현체
 *
 * 콘텐츠 생성, 조회, 수정, 삭제 등 비즈니스 로직을 담당합니다.
 *
 * @property contentUploadService S3 Presigned URL 생성 서비스
 * @property contentRepository 콘텐츠 레포지토리
 */
@Service
class ContentServiceImpl(
    private val contentUploadService: ContentUploadService,
    private val contentRepository: ContentRepository,
    private val contentPhotoRepository: me.onetwo.growsnap.domain.content.repository.ContentPhotoRepository,
    private val uploadSessionRepository: me.onetwo.growsnap.domain.content.repository.UploadSessionRepository,
    private val s3Client: software.amazon.awssdk.services.s3.S3Client,
    private val eventPublisher: ApplicationEventPublisher,
    @Value("\${spring.cloud.aws.s3.bucket}") private val bucketName: String,
    @Value("\${spring.cloud.aws.region.static}") private val region: String
) : ContentService {

    private val logger = LoggerFactory.getLogger(ContentServiceImpl::class.java)

    /**
     * S3 Presigned URL을 생성합니다.
     *
     * 클라이언트가 S3에 직접 파일을 업로드할 수 있도록 Presigned URL을 발급합니다.
     *
     * @param userId 사용자 ID
     * @param request Presigned URL 요청 정보
     * @return Presigned URL 정보를 담은 Mono
     */
    override fun generateUploadUrl(
        userId: UUID,
        request: ContentUploadUrlRequest
    ): Mono<ContentUploadUrlResponse> {
        logger.info("Generating upload URL for user: $userId, contentType: ${request.contentType}")

        return contentUploadService.generateUploadUrl(
            userId = userId,
            contentType = request.contentType,
            fileName = request.fileName,
            fileSize = request.fileSize
        ).map { presignedUrlInfo ->
            ContentUploadUrlResponse(
                contentId = presignedUrlInfo.contentId.toString(),
                uploadUrl = presignedUrlInfo.uploadUrl,
                expiresIn = presignedUrlInfo.expiresIn
            )
        }.doOnSuccess {
            logger.info("Upload URL generated successfully: contentId=${it.contentId}")
        }.doOnError { error ->
            logger.error("Failed to generate upload URL for user: $userId", error)
        }
    }

    /**
     * 콘텐츠를 생성합니다.
     *
     * S3 업로드 완료 후 콘텐츠 메타데이터를 등록합니다.
     *
     * @param userId 사용자 ID
     * @param request 콘텐츠 생성 요청
     * @return 생성된 콘텐츠 정보를 담은 Mono
     */
    @Transactional
    override fun createContent(
        userId: UUID,
        request: ContentCreateRequest
    ): Mono<ContentResponse> {
        logger.info("Creating content: userId=$userId, contentId=${request.contentId}")

        return Mono.fromCallable {
            val contentId = UUID.fromString(request.contentId)
            val now = LocalDateTime.now()

            // 1. Redis에서 업로드 세션 조회
            val uploadSession = uploadSessionRepository.findById(request.contentId).orElseThrow {
                UploadSessionNotFoundException(request.contentId)
            }

            // 2. 권한 확인 - 업로드 요청한 사용자와 콘텐츠 생성 요청한 사용자가 같은지 확인
            if (uploadSession.userId != userId.toString()) {
                throw UploadSessionUnauthorizedException(request.contentId)
            }

            // 3. S3에 파일이 실제로 존재하는지 확인
            if (!checkS3ObjectExists(uploadSession.s3Key)) {
                throw FileNotUploadedException(request.contentId)
            }

            logger.info("S3 file verified: s3Key=${uploadSession.s3Key}")

            // 4. S3 URL 생성
            val s3Url = "https://$bucketName.s3.$region.amazonaws.com/${uploadSession.s3Key}"

            // 5. Content 엔티티 생성 및 저장
            val content = Content(
                id = contentId,
                creatorId = userId,
                contentType = uploadSession.contentType,
                url = s3Url,
                thumbnailUrl = request.thumbnailUrl,
                duration = request.duration,
                width = request.width,
                height = request.height,
                status = ContentStatus.PUBLISHED,
                createdAt = now,
                createdBy = userId.toString(),
                updatedAt = now,
                updatedBy = userId.toString()
            )

            val savedContent = contentRepository.save(content)
                ?: error("Failed to save content")

            // ContentMetadata 생성 및 저장
            val metadata = ContentMetadata(
                contentId = contentId,
                title = request.title,
                description = request.description,
                category = request.category,
                tags = request.tags,
                language = request.language,
                createdAt = now,
                createdBy = userId.toString(),
                updatedAt = now,
                updatedBy = userId.toString()
            )

            val savedMetadata = contentRepository.saveMetadata(metadata)
                ?: error("Failed to save content metadata")

            // 6. PHOTO 타입인 경우 사진 목록 저장
            if (savedContent.contentType == ContentType.PHOTO && !request.photoUrls.isNullOrEmpty()) {
                request.photoUrls.forEachIndexed { index, photoUrl ->
                    val photo = ContentPhoto(
                        contentId = contentId,
                        photoUrl = photoUrl,
                        displayOrder = index,
                        width = request.width,  // 대표 사진 크기 사용
                        height = request.height,
                        createdAt = now,
                        createdBy = userId.toString(),
                        updatedAt = now,
                        updatedBy = userId.toString()
                    )
                    val saved = contentPhotoRepository.save(photo)
                    check(saved) { "Failed to save content photo" }
                }
                logger.info("Content photos saved: contentId=$contentId, count=${request.photoUrls.size}")
            }

            // 7. Redis에서 업로드 세션 삭제 (1회성 토큰)
            uploadSessionRepository.deleteById(request.contentId)
            logger.info("Upload session deleted from Redis: contentId=${request.contentId}")

            Triple(savedContent, savedMetadata, contentId)
        }.map { (content, metadata, contentId) ->
            // PHOTO 타입인 경우 사진 목록 (이미 저장된 request.photoUrls 사용)
            val photoUrls = if (content.contentType == ContentType.PHOTO) {
                request.photoUrls
            } else {
                null
            }

            val response = ContentResponse(
                id = content.id.toString(),
                creatorId = content.creatorId.toString(),
                contentType = content.contentType,
                url = content.url,
                photoUrls = photoUrls,
                thumbnailUrl = content.thumbnailUrl,
                duration = content.duration,
                width = content.width,
                height = content.height,
                status = content.status,
                title = metadata.title,
                description = metadata.description,
                category = metadata.category,
                tags = metadata.tags,
                language = metadata.language,
                createdAt = content.createdAt,
                updatedAt = content.updatedAt
            )

            Pair(response, contentId)
        }.doOnSuccess { (response, contentId) ->
            logger.info("Content created successfully: contentId=${response.id}")

            // ContentCreatedEvent 발행 - 전체 작업이 성공한 후에만 발행
            eventPublisher.publishEvent(ContentCreatedEvent(contentId, userId))
            logger.info("ContentCreatedEvent published: contentId=$contentId")
        }.map { (response, _) ->
            response
        }.doOnError { error ->
            logger.error("Failed to create content: userId=$userId, contentId=${request.contentId}", error)
        }
    }

    /**
     * 콘텐츠를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 콘텐츠 정보를 담은 Mono
     */
    override fun getContent(contentId: UUID): Mono<ContentResponse> {
        logger.info("Getting content: contentId=$contentId")

        return Mono.fromCallable {
            val content = contentRepository.findById(contentId)
                ?: throw NoSuchElementException("Content not found: $contentId")

            val metadata = contentRepository.findMetadataByContentId(contentId)
                ?: throw NoSuchElementException("Content metadata not found: $contentId")

            Pair(content, metadata)
        }.map { (content, metadata) ->
            // PHOTO 타입인 경우 사진 목록 조회
            val photoUrls = if (content.contentType == ContentType.PHOTO) {
                contentPhotoRepository.findByContentId(content.id!!)
                    .map { it.photoUrl }
            } else {
                null
            }

            ContentResponse(
                id = content.id.toString(),
                creatorId = content.creatorId.toString(),
                contentType = content.contentType,
                url = content.url,
                photoUrls = photoUrls,
                thumbnailUrl = content.thumbnailUrl,
                duration = content.duration,
                width = content.width,
                height = content.height,
                status = content.status,
                title = metadata.title,
                description = metadata.description,
                category = metadata.category,
                tags = metadata.tags,
                language = metadata.language,
                createdAt = content.createdAt,
                updatedAt = content.updatedAt
            )
        }
    }

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 목록을 담은 Flux
     */
    override fun getContentsByCreator(creatorId: UUID): Flux<ContentResponse> {
        logger.info("Getting contents by creator: creatorId=$creatorId")

        return Mono.fromCallable {
            val contentWithMetadataList = contentRepository.findWithMetadataByCreatorId(creatorId)

            // N+1 방지: PHOTO 타입 콘텐츠의 사진 목록을 일괄 조회
            val photoContentIds = contentWithMetadataList
                .filter { it.first.contentType == ContentType.PHOTO }
                .mapNotNull { it.first.id }

            val photoUrlsMap = if (photoContentIds.isNotEmpty()) {
                contentPhotoRepository.findByContentIds(photoContentIds)
                    .mapValues { (_, photos) -> photos.map { it.photoUrl } }
            } else {
                emptyMap()
            }

            Pair(contentWithMetadataList, photoUrlsMap)
        }.flatMapMany { (contentWithMetadataList, photoUrlsMap) ->
            Flux.fromIterable(contentWithMetadataList).map { (content, metadata) ->
                val photoUrls = if (content.contentType == ContentType.PHOTO) {
                    photoUrlsMap[content.id]
                } else {
                    null
                }

                ContentResponse(
                    id = content.id.toString(),
                    creatorId = content.creatorId.toString(),
                    contentType = content.contentType,
                    url = content.url,
                    photoUrls = photoUrls,
                    thumbnailUrl = content.thumbnailUrl,
                    duration = content.duration,
                    width = content.width,
                    height = content.height,
                    status = content.status,
                    title = metadata.title,
                    description = metadata.description,
                    category = metadata.category,
                    tags = metadata.tags,
                    language = metadata.language,
                    createdAt = content.createdAt,
                    updatedAt = content.updatedAt
                )
            }
        }
    }

    /**
     * 콘텐츠를 수정합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @param request 수정 요청
     * @return 수정된 콘텐츠 정보를 담은 Mono
     */
    @Transactional
    override fun updateContent(
        userId: UUID,
        contentId: UUID,
        request: ContentUpdateRequest
    ): Mono<ContentResponse> {
        logger.info("Updating content: userId=$userId, contentId=$contentId")

        return Mono.fromCallable {
            // 콘텐츠 존재 확인 및 권한 검증
            val content = contentRepository.findById(contentId)
                ?: throw NoSuchElementException("Content not found: $contentId")

            if (content.creatorId != userId) {
                throw IllegalAccessException("Not authorized to update this content")
            }

            val metadata = contentRepository.findMetadataByContentId(contentId)
                ?: throw NoSuchElementException("Content metadata not found: $contentId")

            // 메타데이터 수정
            val updatedMetadata = metadata.copy(
                title = request.title ?: metadata.title,
                description = request.description ?: metadata.description,
                category = request.category ?: metadata.category,
                tags = request.tags ?: metadata.tags,
                language = request.language ?: metadata.language,
                updatedAt = LocalDateTime.now(),
                updatedBy = userId.toString()
            )

            val success = contentRepository.updateMetadata(updatedMetadata)
            check(success) { "Failed to update content metadata" }

            // PHOTO 타입 콘텐츠의 사진 목록 수정
            if (content.contentType == ContentType.PHOTO && request.photoUrls != null) {
                // 기존 사진 삭제 (Soft Delete)
                contentPhotoRepository.deleteByContentId(contentId, userId.toString())
                logger.info("Deleted existing photos: contentId=$contentId")

                // 새 사진 저장
                request.photoUrls.forEachIndexed { index, photoUrl ->
                    val photo = me.onetwo.growsnap.domain.content.model.ContentPhoto(
                        contentId = contentId,
                        photoUrl = photoUrl,
                        displayOrder = index,
                        width = content.width,
                        height = content.height,
                        createdAt = LocalDateTime.now(),
                        createdBy = userId.toString(),
                        updatedAt = LocalDateTime.now(),
                        updatedBy = userId.toString()
                    )
                    val saved = contentPhotoRepository.save(photo)
                    check(saved) { "Failed to save content photo during update" }
                }
                logger.info("Updated photos: contentId=$contentId, count=${request.photoUrls.size}")
            }

            Triple(content, updatedMetadata, request.photoUrls)
        }.map { (content, metadata, updatedPhotoUrls) ->
            // PHOTO 타입인 경우 사진 목록 조회 (수정되었으면 수정된 것, 아니면 기존 것)
            val photoUrls = if (content.contentType == ContentType.PHOTO) {
                updatedPhotoUrls ?: contentPhotoRepository.findByContentId(content.id!!)
                    .map { it.photoUrl }
            } else {
                null
            }

            ContentResponse(
                id = content.id.toString(),
                creatorId = content.creatorId.toString(),
                contentType = content.contentType,
                url = content.url,
                photoUrls = photoUrls,
                thumbnailUrl = content.thumbnailUrl,
                duration = content.duration,
                width = content.width,
                height = content.height,
                status = content.status,
                title = metadata.title,
                description = metadata.description,
                category = metadata.category,
                tags = metadata.tags,
                language = metadata.language,
                createdAt = content.createdAt,
                updatedAt = metadata.updatedAt
            )
        }.doOnSuccess {
            logger.info("Content updated successfully: contentId=$contentId")
        }.doOnError { error ->
            logger.error("Failed to update content: userId=$userId, contentId=$contentId", error)
        }
    }

    /**
     * 콘텐츠를 삭제합니다.
     *
     * @param userId 사용자 ID
     * @param contentId 콘텐츠 ID
     * @return 삭제 완료를 알리는 Mono<Void>
     */
    @Transactional
    override fun deleteContent(
        userId: UUID,
        contentId: UUID
    ): Mono<Void> {
        logger.info("Deleting content: userId=$userId, contentId=$contentId")

        return Mono.fromCallable {
            // 콘텐츠 존재 확인 및 권한 검증
            val content = contentRepository.findById(contentId)
                ?: throw NoSuchElementException("Content not found: $contentId")

            if (content.creatorId != userId) {
                throw IllegalAccessException("Not authorized to delete this content")
            }

            // Soft Delete
            val success = contentRepository.delete(contentId, userId)
            if (!success) {
                throw IllegalStateException("Failed to delete content")
            }
        }.then().doOnSuccess {
            logger.info("Content deleted successfully: contentId=$contentId")
        }.doOnError { error ->
            logger.error("Failed to delete content: userId=$userId, contentId=$contentId", error)
        }
    }

    /**
     * S3 객체 존재 확인
     *
     * @param s3Key S3 object key
     * @return 파일이 존재하면 true, 아니면 false
     */
    private fun checkS3ObjectExists(s3Key: String): Boolean {
        return try {
            s3Client.headObject {
                it.bucket(bucketName)
                it.key(s3Key)
            }
            true
        } catch (e: software.amazon.awssdk.services.s3.model.NoSuchKeyException) {
            logger.warn("S3 object not found: s3Key=$s3Key")
            false
        } catch (e: Exception) {
            logger.error("Failed to check S3 object existence: s3Key=$s3Key", e)
            false
        }
    }
}
