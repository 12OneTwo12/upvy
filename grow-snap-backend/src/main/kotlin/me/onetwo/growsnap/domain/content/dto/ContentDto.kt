package me.onetwo.growsnap.domain.content.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import me.onetwo.growsnap.domain.content.model.Category
import me.onetwo.growsnap.domain.content.model.ContentStatus
import me.onetwo.growsnap.domain.content.model.ContentType
import me.onetwo.growsnap.domain.content.model.DifficultyLevel
import java.time.LocalDateTime

/**
 * 콘텐츠 업로드 URL 요청
 *
 * 클라이언트가 S3 Presigned URL을 요청할 때 사용합니다.
 *
 * @property contentType 콘텐츠 타입 (VIDEO, PHOTO)
 * @property fileName 파일 이름
 * @property fileSize 파일 크기 (바이트)
 *
 * ## 파일 크기 제한
 * - 비디오: 최대 500MB (ContentUploadServiceImpl에서 검증)
 * - 사진: 최대 50MB (ContentUploadServiceImpl에서 검증)
 *
 * DTO에서는 최소값(1바이트)만 검증하고, 최대값은 contentType에 따라
 * 서비스 계층에서 동적으로 검증합니다.
 */
data class ContentUploadUrlRequest(
    @field:NotNull(message = "콘텐츠 타입은 필수입니다")
    val contentType: ContentType,

    @field:NotBlank(message = "파일 이름은 필수입니다")
    @field:Size(max = 255, message = "파일 이름은 255자 이하여야 합니다")
    val fileName: String,

    @field:NotNull(message = "파일 크기는 필수입니다")
    @field:Min(value = 1, message = "파일 크기는 1바이트 이상이어야 합니다")
    val fileSize: Long
)

/**
 * 콘텐츠 업로드 URL 응답
 *
 * S3 Presigned URL 정보를 반환합니다.
 *
 * @property contentId 콘텐츠 ID (upload token 역할)
 * @property uploadUrl S3 업로드 URL
 * @property expiresIn URL 유효기간 (초)
 */
data class ContentUploadUrlResponse(
    val contentId: String,
    val uploadUrl: String,
    val expiresIn: Int
)

/**
 * 콘텐츠 생성 요청
 *
 * S3 업로드 완료 후 콘텐츠 메타데이터를 등록할 때 사용합니다.
 *
 * @property contentId 콘텐츠 ID (업로드 URL 요청 시 받은 upload token)
 * @property title 제목
 * @property description 설명
 * @property category 카테고리
 * @property tags 태그 목록
 * @property language 언어 코드 (ISO 639-1)
 * @property photoUrls 사진 목록 (PHOTO 타입인 경우, 인스타그램 스타일 갤러리)
 * @property thumbnailUrl 썸네일 URL
 * @property duration 비디오 길이 (초, 사진인 경우 null)
 * @property width 가로 크기 (픽셀)
 * @property height 세로 크기 (픽셀)
 */
data class ContentCreateRequest(
    @field:NotBlank(message = "콘텐츠 ID는 필수입니다")
    val contentId: String,

    @field:NotBlank(message = "제목은 필수입니다")
    @field:Size(max = 200, message = "제목은 200자 이하여야 합니다")
    val title: String,

    @field:Size(max = 2000, message = "설명은 2000자 이하여야 합니다")
    val description: String? = null,

    @field:NotNull(message = "카테고리는 필수입니다")
    val category: Category,

    val tags: List<String> = emptyList(),

    @field:NotBlank(message = "언어는 필수입니다")
    @field:Size(min = 2, max = 10, message = "언어 코드는 2-10자여야 합니다")
    val language: String = "ko",

    val photoUrls: List<String>? = null,

    @field:NotBlank(message = "썸네일 URL은 필수입니다")
    val thumbnailUrl: String,

    @field:Min(value = 0, message = "비디오 길이는 0 이상이어야 합니다")
    val duration: Int? = null,

    @field:NotNull(message = "가로 크기는 필수입니다")
    @field:Min(value = 1, message = "가로 크기는 1 이상이어야 합니다")
    val width: Int,

    @field:NotNull(message = "세로 크기는 필수입니다")
    @field:Min(value = 1, message = "세로 크기는 1 이상이어야 합니다")
    val height: Int
)

/**
 * 콘텐츠 수정 요청
 *
 * 콘텐츠 메타데이터를 수정할 때 사용합니다.
 *
 * @property title 제목
 * @property description 설명
 * @property category 카테고리
 * @property tags 태그 목록
 * @property language 언어 코드
 * @property photoUrls 사진 URL 목록 (PHOTO 타입 콘텐츠만 사용, null이면 사진 목록 변경 안 함)
 */
data class ContentUpdateRequest(
    @field:Size(max = 200, message = "제목은 200자 이하여야 합니다")
    val title: String? = null,

    @field:Size(max = 2000, message = "설명은 2000자 이하여야 합니다")
    val description: String? = null,

    val category: Category? = null,

    val tags: List<String>? = null,

    @field:Size(min = 2, max = 10, message = "언어 코드는 2-10자여야 합니다")
    val language: String? = null,

    @field:Size(min = 1, max = 10, message = "사진은 1-10장까지 업로드 가능합니다")
    val photoUrls: List<String>? = null
)

/**
 * 콘텐츠 응답
 *
 * 콘텐츠 정보를 반환합니다.
 *
 * @property id 콘텐츠 ID
 * @property creatorId 크리에이터 ID
 * @property contentType 콘텐츠 타입
 * @property url 콘텐츠 URL
 * @property photoUrls 사진 목록 (PHOTO 타입인 경우)
 * @property thumbnailUrl 썸네일 URL
 * @property duration 비디오 길이 (초)
 * @property width 가로 크기
 * @property height 세로 크기
 * @property status 콘텐츠 상태
 * @property title 제목
 * @property description 설명
 * @property category 카테고리
 * @property tags 태그 목록
 * @property language 언어
 * @property createdAt 생성 시각
 * @property updatedAt 수정 시각
 */
data class ContentResponse(
    val id: String,
    val creatorId: String,
    val contentType: ContentType,
    val url: String,
    val photoUrls: List<String>?,
    val thumbnailUrl: String,
    val duration: Int?,
    val width: Int,
    val height: Int,
    val status: ContentStatus,
    val title: String,
    val description: String?,
    val category: Category,
    val tags: List<String>,
    val language: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
