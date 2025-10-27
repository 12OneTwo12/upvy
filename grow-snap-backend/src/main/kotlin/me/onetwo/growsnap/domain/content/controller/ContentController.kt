package me.onetwo.growsnap.domain.content.controller

import jakarta.validation.Valid
import me.onetwo.growsnap.domain.content.dto.ContentCreateRequest
import me.onetwo.growsnap.domain.content.dto.ContentResponse
import me.onetwo.growsnap.domain.content.dto.ContentUpdateRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlRequest
import me.onetwo.growsnap.domain.content.dto.ContentUploadUrlResponse
import me.onetwo.growsnap.domain.content.service.ContentService
import me.onetwo.growsnap.infrastructure.security.util.toUserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.security.Principal
import java.util.UUID

/**
 * 콘텐츠 컨트롤러
 *
 * 크리에이터 스튜디오 API 엔드포인트를 제공합니다.
 *
 * @property contentService 콘텐츠 서비스
 */
@RestController
@RequestMapping("/api/v1/contents")
class ContentController(
    private val contentService: ContentService
) {

    /**
     * S3 Presigned Upload URL을 생성합니다.
     *
     * 클라이언트가 S3에 직접 파일을 업로드할 수 있도록 Presigned URL을 발급합니다.
     *
     * ### 처리 흐름
     * 1. 파일 유효성 검증 (크기, 형식)
     * 2. S3 Presigned URL 생성
     * 3. URL 및 contentId 반환
     *
     * @param principal 인증된 사용자 Principal
     * @param request Presigned URL 요청
     * @return 200 OK - Presigned URL 정보
     */
    @PostMapping("/upload-url")
    fun generateUploadUrl(
        principal: Mono<Principal>,
        @Valid @RequestBody request: ContentUploadUrlRequest
    ): Mono<ResponseEntity<ContentUploadUrlResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                contentService.generateUploadUrl(userId, request)
            }
            .map { ResponseEntity.ok(it) }
    }

    /**
     * 콘텐츠를 생성합니다.
     *
     * S3 업로드 완료 후 콘텐츠 메타데이터를 등록합니다.
     *
     * ### 처리 흐름
     * 1. 콘텐츠 메타데이터 저장
     * 2. Content 및 ContentMetadata 엔티티 생성
     * 3. 생성된 콘텐츠 정보 반환
     *
     * @param principal 인증된 사용자 Principal
     * @param request 콘텐츠 생성 요청
     * @return 201 Created - 생성된 콘텐츠 정보
     */
    @PostMapping
    fun createContent(
        principal: Mono<Principal>,
        @Valid @RequestBody request: ContentCreateRequest
    ): Mono<ResponseEntity<ContentResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                contentService.createContent(userId, request)
            }
            .map { ResponseEntity.status(HttpStatus.CREATED).body(it) }
    }

    /**
     * 콘텐츠를 조회합니다.
     *
     * @param contentId 콘텐츠 ID
     * @return 200 OK - 콘텐츠 정보
     *         404 Not Found - 콘텐츠가 존재하지 않음
     */
    @GetMapping("/{contentId}")
    fun getContent(
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<ContentResponse>> {
        return contentService.getContent(contentId)
            .map { ResponseEntity.ok(it) }
            .defaultIfEmpty(ResponseEntity.notFound().build())
    }

    /**
     * 크리에이터의 콘텐츠 목록을 조회합니다.
     *
     * @param principal 인증된 사용자 Principal
     * @return 200 OK - 콘텐츠 목록
     */
    @GetMapping("/me")
    fun getMyContents(
        principal: Mono<Principal>
    ): Mono<ResponseEntity<List<ContentResponse>>> {
        return principal
            .toUserId()
            .flatMapMany { userId ->
                contentService.getContentsByCreator(userId)
            }
            .collectList()
            .map { ResponseEntity.ok(it) }
    }

    /**
     * 콘텐츠를 수정합니다.
     *
     * 콘텐츠 작성자만 수정할 수 있습니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 콘텐츠 ID
     * @param request 수정 요청
     * @return 200 OK - 수정된 콘텐츠 정보
     *         403 Forbidden - 권한 없음
     *         404 Not Found - 콘텐츠가 존재하지 않음
     */
    @PutMapping("/{contentId}")
    fun updateContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID,
        @Valid @RequestBody request: ContentUpdateRequest
    ): Mono<ResponseEntity<ContentResponse>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                contentService.updateContent(userId, contentId, request)
            }
            .map { ResponseEntity.ok(it) }
            .onErrorResume { error ->
                when (error) {
                    is NoSuchElementException -> Mono.just(ResponseEntity.notFound().build())
                    is IllegalAccessException -> Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
                    else -> Mono.error(error)
                }
            }
    }

    /**
     * 콘텐츠를 삭제합니다 (Soft Delete).
     *
     * 콘텐츠 작성자만 삭제할 수 있습니다.
     *
     * @param principal 인증된 사용자 Principal
     * @param contentId 콘텐츠 ID
     * @return 204 No Content - 삭제 성공
     *         403 Forbidden - 권한 없음
     *         404 Not Found - 콘텐츠가 존재하지 않음
     */
    @DeleteMapping("/{contentId}")
    fun deleteContent(
        principal: Mono<Principal>,
        @PathVariable contentId: UUID
    ): Mono<ResponseEntity<Void>> {
        return principal
            .toUserId()
            .flatMap { userId ->
                contentService.deleteContent(userId, contentId)
            }
            .then(Mono.just(ResponseEntity.noContent().build<Void>()))
            .onErrorResume { error ->
                when (error) {
                    is NoSuchElementException -> Mono.just(ResponseEntity.notFound().build())
                    is IllegalAccessException -> Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build())
                    else -> Mono.error(error)
                }
            }
    }
}
