package me.onetwo.growsnap.domain.content.service

import me.onetwo.growsnap.domain.content.model.ContentType
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 콘텐츠 업로드 서비스 인터페이스
 *
 * S3 Presigned URL을 생성하여 클라이언트가 직접 S3에 파일을 업로드할 수 있도록 지원합니다.
 */
interface ContentUploadService {

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
    fun generateUploadUrl(
        userId: UUID,
        contentType: ContentType,
        fileName: String,
        fileSize: Long,
        mimeType: String? = null
    ): Mono<PresignedUrlInfo>
}

/**
 * Presigned URL 정보
 *
 * @property contentId 콘텐츠 고유 ID (upload token 역할)
 * @property uploadUrl S3 Presigned Upload URL
 * @property expiresIn URL 유효기간 (초)
 */
data class PresignedUrlInfo(
    val contentId: UUID,
    val uploadUrl: String,
    val expiresIn: Int = 900  // 15분
)
