package me.onetwo.upvy.domain.content.model

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash
import org.springframework.data.redis.core.TimeToLive
import java.time.Instant
import java.util.UUID

/**
 * 업로드 세션 정보
 *
 * S3 Presigned URL 생성 시 업로드 정보를 Redis에 임시 저장합니다.
 * 클라이언트가 실제로 파일을 업로드한 후 createContent를 호출할 때 이 정보를 사용합니다.
 *
 * @property contentId 콘텐츠 고유 ID (upload token 역할)
 * @property userId 업로드 요청한 사용자 ID
 * @property s3Key S3 object key
 * @property contentType 콘텐츠 타입 (VIDEO, PHOTO)
 * @property fileName 원본 파일 이름
 * @property fileSize 파일 크기 (bytes)
 * @property createdAt 세션 생성 시각 (UTC Instant)
 * @property ttl Time to live (초, 15분 = 900초)
 */
@RedisHash("upload_session")
data class UploadSession(
    @Id
    val contentId: String,
    val userId: String,
    val s3Key: String,
    val contentType: ContentType,
    val fileName: String,
    val fileSize: Long,
    val createdAt: Instant = Instant.now(),
    @TimeToLive
    val ttl: Long = 900  // 15분
)
