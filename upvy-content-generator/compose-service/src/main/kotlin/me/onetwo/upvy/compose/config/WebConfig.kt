package me.onetwo.upvy.compose.config

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * 전역 예외 처리
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * Validation 에러
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val errors = e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "Validation 에러: $errors" }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Validation Error",
                message = errors.joinToString(", "),
                timestamp = Instant.now().toString()
            )
        )
    }

    /**
     * IllegalArgumentException
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn { "잘못된 요청: ${e.message}" }

        return ResponseEntity.badRequest().body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = "Bad Request",
                message = e.message ?: "잘못된 요청입니다",
                timestamp = Instant.now().toString()
            )
        )
    }

    /**
     * FFmpeg 실행 에러
     */
    @ExceptionHandler(FFmpegException::class)
    fun handleFFmpegException(e: FFmpegException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "FFmpeg 에러: ${e.message}" }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "FFmpeg Error",
                message = e.message ?: "영상 합성 중 오류가 발생했습니다",
                timestamp = Instant.now().toString()
            )
        )
    }

    /**
     * GCS 에러
     */
    @ExceptionHandler(GcsException::class)
    fun handleGcsException(e: GcsException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "GCS 에러: ${e.message}" }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Storage Error",
                message = e.message ?: "파일 저장소 오류가 발생했습니다",
                timestamp = Instant.now().toString()
            )
        )
    }

    /**
     * 기타 모든 예외
     */
    @ExceptionHandler(Exception::class)
    fun handleGenericException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error(e) { "서버 에러: ${e.message}" }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = "Internal Server Error",
                message = "서버 내부 오류가 발생했습니다",
                timestamp = Instant.now().toString()
            )
        )
    }
}

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val status: Int,
    val error: String,
    val message: String,
    val timestamp: String
)

/**
 * FFmpeg 예외
 */
class FFmpegException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * GCS 예외
 */
class GcsException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
