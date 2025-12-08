package me.onetwo.upvy.infrastructure.storage

import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 이미지 업로드 서비스 인터페이스
 *
 * 이미지 파일을 S3에 업로드하는 기능을 정의합니다.
 */
interface ImageUploadService {

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
    fun uploadProfileImage(
        userId: UUID,
        imageBytes: ByteArray,
        contentType: String
    ): Mono<String>

    /**
     * S3에서 이미지를 삭제합니다.
     *
     * @param imageUrl 삭제할 이미지 URL
     * @return 삭제 완료를 알리는 Mono<Void>
     */
    fun deleteImage(imageUrl: String): Mono<Void>
}
