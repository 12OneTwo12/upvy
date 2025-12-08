package me.onetwo.upvy.domain.content.exception

import me.onetwo.upvy.infrastructure.exception.BusinessException
import org.springframework.http.HttpStatus

/**
 * 업로드 세션을 찾을 수 없거나 만료된 경우
 *
 * - HTTP Status: 400 Bad Request
 * - Error Code: UPLOAD_SESSION_NOT_FOUND
 * - 시나리오: Redis에 업로드 세션이 없거나 TTL이 만료된 경우
 */
class UploadSessionNotFoundException(
    uploadToken: String
) : BusinessException(
    errorCode = "UPLOAD_SESSION_NOT_FOUND",
    httpStatus = HttpStatus.BAD_REQUEST,
    message = "Upload session not found or expired: $uploadToken. Please generate a new upload URL."
)

/**
 * 업로드 토큰에 대한 권한이 없는 경우
 *
 * - HTTP Status: 403 Forbidden
 * - Error Code: UPLOAD_SESSION_UNAUTHORIZED
 * - 시나리오: 다른 사용자의 업로드 토큰을 사용하려는 경우
 */
class UploadSessionUnauthorizedException(
    uploadToken: String
) : BusinessException(
    errorCode = "UPLOAD_SESSION_UNAUTHORIZED",
    httpStatus = HttpStatus.FORBIDDEN,
    message = "You are not authorized to use this upload token: $uploadToken"
)

/**
 * S3에 업로드된 파일을 찾을 수 없는 경우
 *
 * - HTTP Status: 400 Bad Request
 * - Error Code: FILE_NOT_UPLOADED
 * - 시나리오: Presigned URL을 받았으나 S3에 파일 업로드를 완료하지 않은 경우
 */
class FileNotUploadedException(
    uploadToken: String
) : BusinessException(
    errorCode = "FILE_NOT_UPLOADED",
    httpStatus = HttpStatus.BAD_REQUEST,
    message = "File not found in S3. Please upload the file before creating content: $uploadToken"
)
