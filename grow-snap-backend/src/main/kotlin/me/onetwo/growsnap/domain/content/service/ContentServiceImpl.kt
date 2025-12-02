package me.onetwo.growsnap.domain.content.service

import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentCreationResult
import me.onetwo.growsnap.domain.content.dto.ContentResponse
import me.onetwo.growsnap.domain.content.dto.ContentResponseWithMetadata
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
import me.onetwo.growsnap.domain.feed.dto.InteractionInfoResponse
import me.onetwo.growsnap.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.time.Instant
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
@Transactional(readOnly = true)
class ContentServiceImpl(
    private val contentUploadService: ContentUploadService,
    private val contentRepository: ContentRepository,
    private val contentPhotoRepository: me.onetwo.growsnap.domain.content.repository.ContentPhotoRepository,
    private val uploadSessionRepository: me.onetwo.growsnap.domain.content.repository.UploadSessionRepository,
    private val s3Client: software.amazon.awssdk.services.s3.S3Client,
    private val eventPublisher: ReactiveEventPublisher,
    private val contentInteractionService: me.onetwo.growsnap.domain.analytics.service.ContentInteractionService,
    private val contentInteractionRepository: me.onetwo.growsnap.domain.analytics.repository.ContentInteractionRepository,
    private val userLikeRepository: me.onetwo.growsnap.domain.interaction.repository.UserLikeRepository,
    private val userSaveRepository: me.onetwo.growsnap.domain.interaction.repository.UserSaveRepository,
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
        logger.info("Generating upload URL for user: $userId, contentType: ${request.contentType}, mimeType: ${request.mimeType}")

        return contentUploadService.generateUploadUrl(
            userId = userId,
            contentType = request.contentType,
            fileName = request.fileName,
            fileSize = request.fileSize,
            mimeType = request.mimeType
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
            val now = Instant.now()

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

            // 5. Content 엔티티 생성
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

            // ContentMetadata 생성
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

            Triple(content, metadata, uploadSession.contentType)
        }.flatMap { (content, metadata, contentType) ->
            // Save content and metadata reactively
            contentRepository.save(content)
                .switchIfEmpty(Mono.error(IllegalStateException("Failed to save content")))
                .flatMap { savedContent ->
                    contentRepository.saveMetadata(metadata)
                        .switchIfEmpty(Mono.error(IllegalStateException("Failed to save content metadata")))
                        .map { savedMetadata ->
                            Triple(savedContent, savedMetadata, contentType)
                        }
                }
        }.flatMap { (savedContent, savedMetadata, contentType) ->
            // 6. PHOTO 타입인 경우 사진 목록 저장
            val photoSaveMono = if (contentType == ContentType.PHOTO && !request.photoUrls.isNullOrEmpty()) {
                Flux.fromIterable(request.photoUrls.mapIndexed { index, photoUrl ->
                    ContentPhoto(
                        contentId = savedContent.id!!,
                        photoUrl = photoUrl,
                        displayOrder = index,
                        width = request.width,
                        height = request.height,
                        createdAt = savedContent.createdAt,
                        createdBy = userId.toString(),
                        updatedAt = savedContent.updatedAt,
                        updatedBy = userId.toString()
                    )
                })
                .flatMap { photo ->
                    contentPhotoRepository.save(photo)
                        .onErrorMap { IllegalStateException("Failed to save content photo: ${it.message}") }
                }
                .then()
                .doOnSuccess {
                    logger.info("Content photos saved: contentId=${savedContent.id}, count=${request.photoUrls.size}")
                }
            } else {
                Mono.empty()
            }

            photoSaveMono
                .then(contentInteractionService.createContentInteraction(savedContent.id!!, userId))
                .then(
                    Mono.fromCallable {
                        // 7. Redis에서 업로드 세션 삭제 (1회성 토큰)
                        uploadSessionRepository.deleteById(request.contentId)
                        logger.info("Upload session deleted from Redis: contentId=${request.contentId}")

                        ContentCreationResult(
                            content = savedContent,
                            metadata = savedMetadata,
                            contentId = savedContent.id!!
                        )
                    }
                )
        }.map { result ->
            // PHOTO 타입인 경우 사진 목록 (이미 저장된 request.photoUrls 사용)
            val photoUrls = if (result.content.contentType == ContentType.PHOTO) {
                request.photoUrls
            } else {
                null
            }

            val response = ContentResponse(
                id = result.content.id.toString(),
                creatorId = result.content.creatorId.toString(),
                contentType = result.content.contentType,
                url = result.content.url,
                photoUrls = photoUrls,
                thumbnailUrl = result.content.thumbnailUrl,
                duration = result.content.duration,
                width = result.content.width,
                height = result.content.height,
                status = result.content.status,
                title = result.metadata.title,
                description = result.metadata.description,
                category = result.metadata.category,
                tags = result.metadata.tags,
                language = result.metadata.language,
                createdAt = result.content.createdAt,
                updatedAt = result.content.updatedAt
            )

            ContentResponseWithMetadata(
                response = response,
                contentId = result.contentId
            )
        }.doOnSuccess { result ->
            logger.info("Content created successfully: contentId=${result.response.id}")

            eventPublisher.publish(
                ContentCreatedEvent(
                    contentId = result.contentId,
                    creatorId = userId
                )
            )
            logger.info("ContentCreatedEvent published: contentId=${result.contentId}")
        }.map { result ->
            result.response
        }.doOnError { error ->
            logger.error("Failed to create content: userId=$userId, contentId=${request.contentId}", error)
        }
    }

    /**
     * 콘텐츠를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @return 콘텐츠 정보를 담은 Mono
     */
    override fun getContent(contentId: UUID, userId: UUID?): Mono<ContentResponse> {
        logger.info("Getting content: contentId=$contentId, userId=$userId")

        return contentRepository.findById(contentId)
            .switchIfEmpty(Mono.error(NoSuchElementException("Content not found: $contentId")))
            .flatMap { content ->
                contentRepository.findMetadataByContentId(contentId)
                    .switchIfEmpty(Mono.error(NoSuchElementException("Content metadata not found: $contentId")))
                    .map { metadata -> content to metadata }
            }
            .flatMap { (content, metadata) ->
                // PHOTO 타입인 경우 사진 목록 조회, 아니면 null 반환
                val photoUrlsMono = if (content.contentType == ContentType.PHOTO) {
                    contentPhotoRepository.findByContentId(content.id!!)
                        .map { photos -> photos.map { it.photoUrl } }
                } else {
                    Mono.justOrEmpty(null)
                }

                // Interaction 정보 조회
                val interactionMono = getInteractionInfo(contentId, userId)

                Mono.zip(photoUrlsMono.defaultIfEmpty(emptyList()), interactionMono)
                    .map { tuple ->
                        val photoUrls = tuple.t1.takeIf { it.isNotEmpty() }
                        val interactions = tuple.t2

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
                            interactions = interactions,
                            createdAt = content.createdAt,
                            updatedAt = content.updatedAt
                        )
                    }
            }
    }

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @return 콘텐츠 목록을 담은 Flux
     */
    override fun getContentsByCreator(creatorId: UUID, userId: UUID?): Flux<ContentResponse> {
        logger.info("Getting contents by creator: creatorId=$creatorId, userId=$userId")

        return contentRepository.findWithMetadataByCreatorId(creatorId)
            .collectList()
            .flatMap { contentWithMetadataList ->
                // N+1 방지: PHOTO 타입 콘텐츠의 사진 목록을 일괄 조회
                val photoContentIds = contentWithMetadataList
                    .filter { it.content.contentType == ContentType.PHOTO }
                    .mapNotNull { it.content.id }

                val photoUrlsMapMono = if (photoContentIds.isNotEmpty()) {
                    contentPhotoRepository.findByContentIds(photoContentIds)
                        .map { photoMap ->
                            photoMap.mapValues { (_, photos) -> photos.map { it.photoUrl } }
                        }
                } else {
                    Mono.just(emptyMap())
                }

                photoUrlsMapMono.map { photoUrlsMap ->
                    contentWithMetadataList to photoUrlsMap
                }
            }
            .flatMapMany { (contentWithMetadataList, photoUrlsMap) ->
                Flux.fromIterable(contentWithMetadataList).concatMap { contentWithMetadata ->
                    val content = contentWithMetadata.content
                    val metadata = contentWithMetadata.metadata
                    val photoUrls = if (content.contentType == ContentType.PHOTO) {
                        photoUrlsMap[content.id]
                    } else {
                        null
                    }

                    // Interaction 정보 조회
                    getInteractionInfo(content.id!!, userId).map { interactions ->
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
                            interactions = interactions,
                            createdAt = content.createdAt,
                            updatedAt = content.updatedAt
                        )
                    }
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

        return contentRepository.findById(contentId)
            .switchIfEmpty(Mono.error(NoSuchElementException("Content not found: $contentId")))
            .flatMap { content ->
                if (content.creatorId != userId) {
                    return@flatMap Mono.error<Pair<Content, ContentMetadata>>(
                        IllegalAccessException("Not authorized to update this content")
                    )
                }

                contentRepository.findMetadataByContentId(contentId)
                    .switchIfEmpty(Mono.error(NoSuchElementException("Content metadata not found: $contentId")))
                    .map { metadata -> content to metadata }
            }
            .flatMap { (content, metadata) ->
                // 메타데이터 수정
                val updatedMetadata = metadata.copy(
                    title = request.title ?: metadata.title,
                    description = request.description ?: metadata.description,
                    category = request.category ?: metadata.category,
                    tags = request.tags ?: metadata.tags,
                    language = request.language ?: metadata.language,
                    updatedAt = Instant.now(),
                    updatedBy = userId.toString()
                )

                contentRepository.updateMetadata(updatedMetadata)
                    .flatMap { success ->
                        if (!success) {
                            Mono.error(IllegalStateException("Failed to update content metadata"))
                        } else {
                            Mono.just(Triple(content, updatedMetadata, request.photoUrls))
                        }
                    }
            }
            .flatMap { (content, updatedMetadata, photoUrls) ->
                // PHOTO 타입 콘텐츠의 사진 목록 수정
                val photoUpdateMono = if (content.contentType == ContentType.PHOTO && photoUrls != null) {
                    // 기존 사진 삭제 (Soft Delete)
                    contentPhotoRepository.deleteByContentId(contentId, userId.toString())
                        .doOnSuccess {
                            logger.info("Deleted existing photos: contentId=$contentId")
                        }
                        .then(
                            // 새 사진 저장
                            Flux.fromIterable(photoUrls.mapIndexed { index, photoUrl ->
                                ContentPhoto(
                                    contentId = contentId,
                                    photoUrl = photoUrl,
                                    displayOrder = index,
                                    width = content.width,
                                    height = content.height,
                                    createdAt = Instant.now(),
                                    createdBy = userId.toString(),
                                    updatedAt = Instant.now(),
                                    updatedBy = userId.toString()
                                )
                            })
                            .flatMap { photo ->
                                contentPhotoRepository.save(photo)
                                    .onErrorMap { IllegalStateException("Failed to save content photo during update: ${it.message}") }
                            }
                            .then()
                            .doOnSuccess {
                                logger.info("Updated photos: contentId=$contentId, count=${photoUrls.size}")
                            }
                        )
                } else {
                    Mono.empty()
                }

                photoUpdateMono.then(Mono.just(Triple(content, updatedMetadata, photoUrls)))
            }
            .flatMap { triple ->
                val (content, metadata, updatedPhotoUrls) = triple
                // PHOTO 타입인 경우 사진 목록 조회 (수정되었으면 수정된 것, 아니면 기존 것)
                val photoUrlsMono: Mono<List<String>?> = if (content.contentType == ContentType.PHOTO) {
                    if (updatedPhotoUrls != null) {
                        Mono.just(updatedPhotoUrls)
                    } else {
                        contentPhotoRepository.findByContentId(content.id!!)
                            .map { photos -> photos.map { it.photoUrl } }
                    }
                } else {
                    Mono.justOrEmpty(null)
                }

                photoUrlsMono.map { photoUrls ->
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
                }
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

        return contentRepository.findById(contentId)
            .switchIfEmpty(Mono.error(NoSuchElementException("Content not found: $contentId")))
            .flatMap { content ->
                if (content.creatorId != userId) {
                    return@flatMap Mono.error<Void>(
                        IllegalAccessException("Not authorized to delete this content")
                    )
                }

                // Soft Delete
                contentRepository.delete(contentId, userId)
                    .flatMap { success ->
                        if (!success) {
                            Mono.error(IllegalStateException("Failed to delete content"))
                        } else {
                            Mono.empty()
                        }
                    }
            }
            .doOnSuccess {
                logger.info("Content deleted successfully: contentId=$contentId")
            }
            .doOnError { error ->
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
        } catch (e: NoSuchKeyException) {
            logger.warn("S3 object not found: s3Key=$s3Key")
            false
        } catch (e: Exception) {
            logger.error("Failed to check S3 object existence: s3Key=$s3Key", e)
            false
        }
    }

    /**
     * 인터랙션 정보 조회
     *
     * 콘텐츠의 인터랙션 통계 (좋아요, 댓글, 저장, 공유, 조회수) 및 사용자별 상태를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @param userId 사용자 ID (선택, null이면 사용자별 상태는 false로 반환)
     * @return 인터랙션 정보를 담은 Mono
     */
    private fun getInteractionInfo(contentId: UUID, userId: UUID?): Mono<InteractionInfoResponse> {
        val likeCountMono = contentInteractionRepository.getLikeCount(contentId)
        val commentCountMono = contentInteractionRepository.getCommentCount(contentId)
        val saveCountMono = contentInteractionRepository.getSaveCount(contentId)
        val shareCountMono = contentInteractionRepository.getShareCount(contentId)
        val viewCountMono = contentInteractionRepository.getViewCount(contentId)

        val isLikedMono = userId?.let { userLikeRepository.exists(it, contentId) } ?: Mono.just(false)
        val isSavedMono = userId?.let { userSaveRepository.exists(it, contentId) } ?: Mono.just(false)

        return Mono.zip(
            likeCountMono,
            commentCountMono,
            saveCountMono,
            shareCountMono,
            viewCountMono,
            isLikedMono,
            isSavedMono
        ).map { tuple ->
            InteractionInfoResponse(
                likeCount = tuple.t1,
                commentCount = tuple.t2,
                saveCount = tuple.t3,
                shareCount = tuple.t4,
                viewCount = tuple.t5,
                isLiked = tuple.t6,
                isSaved = tuple.t7
            )
        }
    }
}
