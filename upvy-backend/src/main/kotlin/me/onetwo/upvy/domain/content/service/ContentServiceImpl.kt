package me.onetwo.upvy.domain.content.service

import me.onetwo.upvy.domain.content.dto.ContentCreateRequest
import me.onetwo.upvy.domain.content.dto.ContentCreationResult
import me.onetwo.upvy.domain.content.dto.ContentPageResponse
import me.onetwo.upvy.domain.content.dto.ContentResponse
import me.onetwo.upvy.domain.content.dto.ContentResponseWithMetadata
import me.onetwo.upvy.domain.content.dto.ContentUpdateRequest
import me.onetwo.upvy.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.upvy.domain.content.dto.ContentUploadUrlResponse
import me.onetwo.upvy.domain.content.event.ContentCreatedEvent
import me.onetwo.upvy.domain.content.exception.FileNotUploadedException
import me.onetwo.upvy.domain.content.exception.UploadSessionNotFoundException
import me.onetwo.upvy.domain.content.exception.UploadSessionUnauthorizedException
import me.onetwo.upvy.domain.content.model.Content
import me.onetwo.upvy.domain.content.model.ContentMetadata
import me.onetwo.upvy.domain.content.model.ContentPhoto
import me.onetwo.upvy.domain.content.model.ContentStatus
import me.onetwo.upvy.domain.content.model.ContentType
import me.onetwo.upvy.domain.content.repository.ContentRepository
import me.onetwo.upvy.domain.content.repository.ContentPhotoRepository
import me.onetwo.upvy.domain.content.repository.UploadSessionRepository
import me.onetwo.upvy.domain.feed.dto.InteractionInfoResponse
import me.onetwo.upvy.domain.analytics.service.ContentInteractionService
import me.onetwo.upvy.domain.analytics.repository.ContentInteractionRepository
import me.onetwo.upvy.domain.interaction.repository.UserLikeRepository
import me.onetwo.upvy.domain.interaction.repository.UserSaveRepository
import me.onetwo.upvy.domain.tag.service.TagService
import me.onetwo.upvy.infrastructure.common.dto.CursorPageRequest
import me.onetwo.upvy.infrastructure.common.dto.CursorPageResponse
import me.onetwo.upvy.infrastructure.event.ReactiveEventPublisher
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.s3.S3Client
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
    private val contentPhotoRepository: ContentPhotoRepository,
    private val uploadSessionRepository: UploadSessionRepository,
    private val s3Client: S3Client,
    private val eventPublisher: ReactiveEventPublisher,
    private val contentInteractionService: ContentInteractionService,
    private val contentInteractionRepository: ContentInteractionRepository,
    private val userLikeRepository: UserLikeRepository,
    private val userSaveRepository: UserSaveRepository,
    private val tagService: TagService,
    @Value("\${spring.cloud.aws.s3.bucket}") private val bucketName: String,
    @Value("\${spring.cloud.aws.region.static}") private val region: String
) : ContentService {

    private val logger = LoggerFactory.getLogger(ContentServiceImpl::class.java)

    /**
     * 콘텐츠 생성을 위한 준비 데이터
     */
    private data class PreparedContent(
        val content: Content,
        val metadata: ContentMetadata,
        val contentType: ContentType
    )

    /**
     * 저장된 콘텐츠와 메타데이터
     */
    private data class SavedContent(
        val content: Content,
        val metadata: ContentMetadata,
        val contentType: ContentType
    )

    /**
     * 콘텐츠 생성 결과
     */
    private data class FinalizedContent(
        val content: Content,
        val metadata: ContentMetadata,
        val contentId: UUID
    )

    /**
     * 인터랙션 집계 데이터
     *
     * 7-tuple 사용을 피하고 가독성을 높이기 위한 WebFlux Best Practice 적용
     */
    private data class InteractionData(
        val likeCount: Int,
        val commentCount: Int,
        val saveCount: Int,
        val shareCount: Int,
        val viewCount: Int,
        val isLiked: Boolean,
        val isSaved: Boolean
    )

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

        return validateAndPrepareContent(userId, request)
            .flatMap { prepared -> saveContentAndMetadata(prepared) }
            .flatMap { saved -> attachTagsIfNeeded(saved, request, userId) }
            .flatMap { saved -> savePhotosIfNeeded(saved, request, userId) }
            .flatMap { saved -> finalizeContentCreation(saved, request, userId) }
            .map { result -> buildContentResponse(result, request) }
            .doOnSuccess { response ->
                eventPublisher.publish(
                    ContentCreatedEvent(
                        contentId = UUID.fromString(response.id),
                        creatorId = userId
                    )
                )
                logger.info("Content created successfully: contentId=${response.id}")
            }
            .doOnError { error ->
                logger.error("Failed to create content: userId=$userId, contentId=${request.contentId}", error)
            }
    }

    /**
     * 콘텐츠 생성 전 검증 및 준비
     *
     * 1. Redis에서 업로드 세션 조회
     * 2. 권한 확인
     * 3. S3 파일 존재 확인
     * 4. Content 및 ContentMetadata 엔티티 생성
     *
     * @param userId 사용자 ID
     * @param request 콘텐츠 생성 요청
     * @return 준비된 콘텐츠 데이터
     */
    private fun validateAndPrepareContent(
        userId: UUID,
        request: ContentCreateRequest
    ): Mono<PreparedContent> {
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

            PreparedContent(
                content = content,
                metadata = metadata,
                contentType = uploadSession.contentType
            )
        }
    }

    /**
     * Content와 ContentMetadata를 DB에 저장
     *
     * @param prepared 준비된 콘텐츠 데이터
     * @return 저장된 콘텐츠와 메타데이터
     */
    private fun saveContentAndMetadata(
        prepared: PreparedContent
    ): Mono<SavedContent> {
        return contentRepository.save(prepared.content)
            .switchIfEmpty(Mono.error(IllegalStateException("Failed to save content")))
            .flatMap { savedContent ->
                contentRepository.saveMetadata(prepared.metadata)
                    .switchIfEmpty(Mono.error(IllegalStateException("Failed to save content metadata")))
                    .map { savedMetadata ->
                        SavedContent(
                            content = savedContent,
                            metadata = savedMetadata,
                            contentType = prepared.contentType
                        )
                    }
            }
    }

    /**
     * 태그 연결 (tags가 있는 경우에만)
     *
     * @param saved 저장된 콘텐츠
     * @param request 콘텐츠 생성 요청
     * @param userId 사용자 ID
     * @return 저장된 콘텐츠 (변경 없음)
     */
    private fun attachTagsIfNeeded(
        saved: SavedContent,
        request: ContentCreateRequest,
        userId: UUID
    ): Mono<SavedContent> {
        if (request.tags.isNullOrEmpty()) {
            return Mono.just(saved)
        }

        return tagService.attachTagsToContent(
            contentId = saved.content.id!!,
            tagNames = request.tags!!,
            userId = userId.toString()
        )
            .collectList()
            .doOnSuccess { tags ->
                logger.info("Tags attached: contentId=${saved.content.id}, tags=${tags.map { it.name }}")
            }
            .thenReturn(saved)
    }

    /**
     * PHOTO 타입인 경우 사진 목록 저장
     *
     * @param saved 저장된 콘텐츠
     * @param request 콘텐츠 생성 요청
     * @param userId 사용자 ID
     * @return 저장된 콘텐츠 (변경 없음)
     */
    private fun savePhotosIfNeeded(
        saved: SavedContent,
        request: ContentCreateRequest,
        userId: UUID
    ): Mono<SavedContent> {
        if (saved.contentType != ContentType.PHOTO || request.photoUrls.isNullOrEmpty()) {
            return Mono.just(saved)
        }

        return Flux.fromIterable(request.photoUrls.mapIndexed { index, photoUrl ->
            ContentPhoto(
                contentId = saved.content.id!!,
                photoUrl = photoUrl,
                displayOrder = index,
                width = request.width,
                height = request.height,
                createdAt = saved.content.createdAt,
                createdBy = userId.toString(),
                updatedAt = saved.content.updatedAt,
                updatedBy = userId.toString()
            )
        })
            .flatMap { photo ->
                contentPhotoRepository.save(photo)
                    .onErrorMap { IllegalStateException("Failed to save content photo: ${it.message}") }
            }
            .then()
            .doOnSuccess {
                logger.info("Content photos saved: contentId=${saved.content.id}, count=${request.photoUrls.size}")
            }
            .thenReturn(saved)
    }

    /**
     * 콘텐츠 생성 마무리
     *
     * 1. ContentInteraction 생성
     * 2. Redis 업로드 세션 삭제
     *
     * @param saved 저장된 콘텐츠
     * @param request 콘텐츠 생성 요청
     * @param userId 사용자 ID
     * @return 최종 콘텐츠 생성 결과
     */
    private fun finalizeContentCreation(
        saved: SavedContent,
        request: ContentCreateRequest,
        userId: UUID
    ): Mono<FinalizedContent> {
        return contentInteractionService.createContentInteraction(saved.content.id!!, userId)
            .then(
                Mono.fromCallable {
                    // Redis에서 업로드 세션 삭제 (1회성 토큰)
                    uploadSessionRepository.deleteById(request.contentId)
                    logger.info("Upload session deleted from Redis: contentId=${request.contentId}")

                    FinalizedContent(
                        content = saved.content,
                        metadata = saved.metadata,
                        contentId = saved.content.id!!
                    )
                }
            )
    }

    /**
     * ContentResponse 생성
     *
     * @param result 최종 콘텐츠 생성 결과
     * @param request 콘텐츠 생성 요청
     * @return ContentResponse
     */
    private fun buildContentResponse(
        result: FinalizedContent,
        request: ContentCreateRequest
    ): ContentResponse {
        val photoUrls = if (result.content.contentType == ContentType.PHOTO) {
            request.photoUrls
        } else {
            null
        }

        return ContentResponse(
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

        // Parallel fetch: content와 metadata를 동시에 조회하여 성능 향상
        return Mono.zip(
            contentRepository.findById(contentId)
                .switchIfEmpty(Mono.error(NoSuchElementException("Content not found: $contentId"))),
            contentRepository.findMetadataByContentId(contentId)
                .switchIfEmpty(Mono.error(NoSuchElementException("Content metadata not found: $contentId")))
        )
            .flatMap { tuple ->
                val content = tuple.t1
                val metadata = tuple.t2
                // PHOTO 타입인 경우 사진 목록 조회, 아니면 null 반환
                val photoUrlsMono = if (content.contentType == ContentType.PHOTO) {
                    contentPhotoRepository.findByContentId(content.id!!)
                        .map { photos -> photos.map { it.photoUrl } }
                } else {
                    Mono.justOrEmpty(null)
                }

                // Interaction 정보 조회
                val interactionMono = getInteractionInfo(contentId, userId)

                // Tags 조회
                val tagsMono = tagService.getTagsByContentId(contentId)
                    .map { it.name }
                    .collectList()

                Mono.zip(photoUrlsMono.defaultIfEmpty(emptyList()), interactionMono, tagsMono)
                    .map { tuple ->
                        val photoUrls = tuple.t1.takeIf { it.isNotEmpty() }
                        val interactions = tuple.t2
                        val tags = tuple.t3

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
                            tags = tags,
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

                // N+1 방지: 태그 일괄 조회
                val contentIds = contentWithMetadataList.mapNotNull { it.content.id }
                val tagsMapMono = tagService.getTagsByContentIds(contentIds)
                    .collectList()
                    .map { projections ->
                        projections.associate { it.contentId to it.tags }
                    }

                Mono.zip(photoUrlsMapMono, tagsMapMono) { photoUrlsMap, tagsMap ->
                        Triple(contentWithMetadataList, photoUrlsMap, tagsMap)
                    }
            }
            .flatMapMany { (contentWithMetadataList, photoUrlsMap, tagsMap) ->
                Flux.fromIterable(contentWithMetadataList).concatMap { contentWithMetadata ->
                    val content = contentWithMetadata.content
                    val metadata = contentWithMetadata.metadata
                    val photoUrls = if (content.contentType == ContentType.PHOTO) {
                        photoUrlsMap[content.id]
                    } else {
                        null
                    }
                    val tags = tagsMap[content.id] ?: emptyList()

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
                            tags = tags,
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
     * 크리에이터의 콘텐츠 목록을 커서 기반 페이징으로 조회합니다.
     *
     * @param creatorId 크리에이터 ID
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @param pageRequest 커서 페이지 요청
     * @return 콘텐츠 페이지 응답을 담은 Mono
     */
    override fun getContentsByCreatorWithCursor(
        creatorId: UUID,
        userId: UUID?,
        pageRequest: CursorPageRequest
    ): Mono<ContentPageResponse> {
        logger.info("Getting contents by creator with cursor: creatorId=$creatorId, userId=$userId, cursor=${pageRequest.cursor}, limit=${pageRequest.limit}")

        val cursor = pageRequest.cursor?.let { UUID.fromString(it) }
        val limit = pageRequest.limit

        return contentRepository.findWithMetadataByCreatorIdWithCursor(
            creatorId = creatorId,
            cursor = cursor,
            limit = limit + 1  // hasNext 판단용
        )
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

                // N+1 방지: 태그 일괄 조회
                val contentIds = contentWithMetadataList.mapNotNull { it.content.id }
                val tagsMapMono = tagService.getTagsByContentIds(contentIds)
                    .collectList()
                    .map { projections ->
                        projections.associate { it.contentId to it.tags }
                    }

                Mono.zip(photoUrlsMapMono, tagsMapMono) { photoUrlsMap, tagsMap ->
                        Triple(contentWithMetadataList, photoUrlsMap, tagsMap)
                    }
            }
            .flatMapMany { (contentWithMetadataList, photoUrlsMap, tagsMap) ->
                Flux.fromIterable(contentWithMetadataList).concatMap { contentWithMetadata ->
                    val content = contentWithMetadata.content
                    val metadata = contentWithMetadata.metadata
                    val photoUrls = if (content.contentType == ContentType.PHOTO) {
                        photoUrlsMap[content.id]
                    } else {
                        null
                    }
                    val tags = tagsMap[content.id] ?: emptyList()

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
                            tags = tags,
                            language = metadata.language,
                            interactions = interactions,
                            createdAt = content.createdAt,
                            updatedAt = content.updatedAt
                        )
                    }
                }
            }
            .collectList()
            .map { contentResponses ->
                CursorPageResponse.of(
                    content = contentResponses,
                    limit = limit,
                    getCursor = { it.id }
                )
            }
    }

    /**
     * 여러 콘텐츠 ID로 콘텐츠 목록을 배치 조회합니다.
     *
     * 저장한 콘텐츠 목록 등에서 N+1 문제 없이 조회하기 위해 사용됩니다.
     *
     * @param contentIds 조회할 콘텐츠 ID 목록
     * @param userId 사용자 ID (선택, 인터랙션 정보에 사용자별 상태를 포함하려면 제공)
     * @return 콘텐츠 목록을 담은 Flux (순서는 DB 순서대로)
     */
    override fun getContentsByIds(contentIds: List<UUID>, userId: UUID?): Flux<ContentResponse> {
        logger.info("Getting contents by IDs: contentIds.size=${contentIds.size}, userId=$userId")

        if (contentIds.isEmpty()) {
            return Flux.empty()
        }

        return contentRepository.findByIdsWithMetadata(contentIds)
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

                // N+1 방지: 태그 일괄 조회
                val contentIdsList = contentWithMetadataList.mapNotNull { it.content.id }
                val tagsMapMono = tagService.getTagsByContentIds(contentIdsList)
                    .collectList()
                    .map { projections ->
                        projections.associate { it.contentId to it.tags }
                    }

                Mono.zip(photoUrlsMapMono, tagsMapMono) { photoUrlsMap, tagsMap ->
                        Triple(contentWithMetadataList, photoUrlsMap, tagsMap)
                    }
            }
            .flatMapMany { (contentWithMetadataList, photoUrlsMap, tagsMap) ->
                Flux.fromIterable(contentWithMetadataList).concatMap { contentWithMetadata ->
                    val content = contentWithMetadata.content
                    val metadata = contentWithMetadata.metadata
                    val photoUrls = if (content.contentType == ContentType.PHOTO) {
                        photoUrlsMap[content.id]
                    } else {
                        null
                    }
                    val tags = tagsMap[content.id] ?: emptyList()

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
                            tags = tags,
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

                photoUpdateMono
                    .then(Mono.just(content to updatedMetadata))
            }
            .flatMap { (content, updatedMetadata) ->
                // Tags 업데이트 (tags가 변경된 경우에만)
                if (request.tags != null) {
                    // 기존 태그 제거 후 새 태그 연결
                    tagService.detachTagsFromContent(contentId, userId.toString())
                        .doOnSuccess { count ->
                            logger.info("Detached tags: contentId=$contentId, count=$count")
                        }
                        .then(
                            if (request.tags!!.isNotEmpty()) {
                                tagService.attachTagsToContent(
                                    contentId = contentId,
                                    tagNames = request.tags!!,
                                    userId = userId.toString()
                                )
                                    .collectList()
                                    .doOnSuccess { tags ->
                                        logger.info("Attached new tags: contentId=$contentId, tags=${tags.map { it.name }}")
                                    }
                            } else {
                                Mono.just(emptyList())
                            }
                        )
                        .map { Pair(content, updatedMetadata) }
                } else {
                    Mono.just(Pair(content, updatedMetadata))
                }
            }
            .flatMap { (content, updatedMetadata) ->
                val triple = Triple(content, updatedMetadata, request.photoUrls)
                // 원래 로직 계속
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
            val data = InteractionData(
                likeCount = tuple.t1,
                commentCount = tuple.t2,
                saveCount = tuple.t3,
                shareCount = tuple.t4,
                viewCount = tuple.t5,
                isLiked = tuple.t6,
                isSaved = tuple.t7
            )
            InteractionInfoResponse(
                likeCount = data.likeCount,
                commentCount = data.commentCount,
                saveCount = data.saveCount,
                shareCount = data.shareCount,
                viewCount = data.viewCount,
                isLiked = data.isLiked,
                isSaved = data.isSaved
            )
        }
    }

    /**
     * 크리에이터의 총 콘텐츠 개수를 조회합니다.
     *
     * 프로필 화면에서 사용자의 총 콘텐츠 개수를 표시하기 위해 사용됩니다.
     * Soft Delete되지 않은 콘텐츠만 카운트합니다.
     *
     * @param creatorId 크리에이터 ID
     * @return 콘텐츠 개수를 담은 Mono
     */
    override fun countContentsByCreatorId(creatorId: UUID): Mono<Long> {
        logger.debug("Counting contents for creator: creatorId={}", creatorId)
        return contentRepository.countByCreatorId(creatorId)
            .doOnSuccess { count ->
                logger.debug("Content count retrieved: creatorId={}, count={}", creatorId, count)
            }
            .doOnError { error ->
                logger.error("Failed to count contents: creatorId={}", creatorId, error)
            }
    }
}
