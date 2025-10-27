package me.onetwo.growsnap.domain.content.service

import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentResponse
import me.onetwo.growsnap.domain.content.dto.ContentUpdateRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlResponse
import me.onetwo.growsnap.domain.content.model.Content
import me.onetwo.growsnap.domain.content.model.ContentMetadata
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.repository.ContentRepository
import org.slf4j.LoggerFactory
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
    private val contentRepository: ContentRepository
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

            // TODO: CRITICAL - S3 URL 하드코딩 문제
            // - 버킷 이름과 경로가 하드코딩되어 있음
            // - generateUploadUrl에서 생성한 실제 S3 객체 키와 다를 수 있음
            // - 해결 방법: ContentUploadService에서 생성한 객체 키 정보를 Redis/DB에 저장하고,
            //   createContent에서 조회하여 사용하도록 아키텍처 수정 필요
            val s3Url = "https://bucket.s3.amazonaws.com/contents/$contentId/${request.contentId}"

            // TODO: CRITICAL - contentType 하드코딩 문제
            // - VIDEO로 고정되어 있어 사진 콘텐츠를 생성할 수 없음
            // - 해결 방법: ContentCreateRequest에 contentType 필드 추가하거나,
            //   generateUploadUrl 시점에 저장한 contentType을 조회하여 사용
            // Content 엔티티 생성 및 저장
            val content = Content(
                id = contentId,
                creatorId = userId,
                contentType = me.onetwo.growsnap.domain.content.model.ContentType.VIDEO,
                url = s3Url,
                thumbnailUrl = request.thumbnailUrl,
                duration = request.duration,
                width = request.width,
                height = request.height,
                status = ContentStatus.PUBLISHED,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )

            val savedContent = contentRepository.save(content)
                ?: throw IllegalStateException("Failed to save content")

            // ContentMetadata 생성 및 저장
            val metadata = ContentMetadata(
                contentId = contentId,
                title = request.title,
                description = request.description,
                category = request.category,
                tags = request.tags,
                language = request.language,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId
            )

            val savedMetadata = contentRepository.saveMetadata(metadata)
                ?: throw IllegalStateException("Failed to save content metadata")

            Pair(savedContent, savedMetadata)
        }.map { (content, metadata) ->
            ContentResponse(
                id = content.id.toString(),
                creatorId = content.creatorId.toString(),
                contentType = content.contentType,
                url = content.url,
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
        }.doOnSuccess {
            logger.info("Content created successfully: contentId=${it.id}")
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
            ContentResponse(
                id = content.id.toString(),
                creatorId = content.creatorId.toString(),
                contentType = content.contentType,
                url = content.url,
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
            contentRepository.findWithMetadataByCreatorId(creatorId)
        }.flatMapMany { contentWithMetadataList ->
            Flux.fromIterable(contentWithMetadataList).map { (content, metadata) ->
                ContentResponse(
                    id = content.id.toString(),
                    creatorId = content.creatorId.toString(),
                    contentType = content.contentType,
                    url = content.url,
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
                updatedBy = userId
            )

            val success = contentRepository.updateMetadata(updatedMetadata)
            if (!success) {
                throw IllegalStateException("Failed to update content metadata")
            }

            Pair(content, updatedMetadata)
        }.map { (content, metadata) ->
            ContentResponse(
                id = content.id.toString(),
                creatorId = content.creatorId.toString(),
                contentType = content.contentType,
                url = content.url,
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
}
